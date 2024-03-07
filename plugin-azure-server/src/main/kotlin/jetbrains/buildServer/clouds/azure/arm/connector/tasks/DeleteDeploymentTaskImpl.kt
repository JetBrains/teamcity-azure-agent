/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.fasterxml.jackson.databind.json.JsonMapper
import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.LongRunningOperationOptions
import com.microsoft.azure.management.compute.DiskCreateOptionTypes
import com.microsoft.azure.management.compute.VirtualMachine
import com.microsoft.azure.management.resources.*
import com.microsoft.azure.management.resources.fluentcore.arm.ResourceId
import com.microsoft.azure.management.resources.fluentcore.arm.ResourceUtils
import com.microsoft.azure.management.resources.implementation.ProviderInner
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.throttler.*
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import jetbrains.buildServer.clouds.azure.arm.utils.DeploymentFeaturesDescriptorError
import jetbrains.buildServer.serverSide.TeamCityProperties
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*
import rx.Notification
import rx.Observable
import rx.Single
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.util.*
import java.util.concurrent.TimeUnit

data class DeleteDeploymentTaskParameter(
        val resourceGroupName: String,
        val name: String)

class DeleteDeploymentTaskImpl(private val myNotifications: AzureTaskNotifications) : AzureThrottlerTaskBaseImpl<AzureApi, DeleteDeploymentTaskParameter, Unit>() {
    private val providerToApiVersionMap = Collections.synchronizedMap(mutableMapOf<String, InternalProvider>())

    override fun create(api: AzureApi, taskContext: AzureTaskContext, parameter: DeleteDeploymentTaskParameter): Single<Unit> {
        LOG.debug("Deleting deployment. Name=${parameter.name}, CorellationId=${taskContext.corellationId}")

        val genericResourceService = api.genericResources().manager().inner().azureClient.retrofit().create(GenericResourceService::class.java)
        return api
                .deployments()
                .getByResourceGroupAsync(parameter.resourceGroupName, parameter.name)
                .onErrorResumeNext {
                    LOG.debug("Deployment ${parameter.name} in group ${parameter.resourceGroupName} was not found. CorellationId=${taskContext.corellationId}", it)
                    Observable.empty()
                }
                .flatMap { deployment: Deployment? ->
                    if (deployment == null) {
                        LOG.debug("Could not find deployment ${parameter.name} in group ${parameter.resourceGroupName}. Removing resource directly. CorellationId=${taskContext.corellationId}")
                        getVMResources(taskContext) {
                            api
                                .virtualMachines()
                                .getByResourceGroupAsync(parameter.resourceGroupName, parameter.name)
                        }
                            .concatMap { deleteResource(it, api, genericResourceService, taskContext) }
                            .takeLast(1)
                            .concatMap {
                                val subscriptionId = api.subscriptionId()
                                if (subscriptionId != null) {
                                    myNotifications.raise(
                                        AzureTaskVirtualMachineRemoved(
                                            api,
                                            taskContext,
                                            getVirtualMachineResource(subscriptionId, parameter).resourceId,
                                        )
                                    )
                                } else {
                                    Observable.just(Unit)
                                }
                            }
                    } else {
                        switchDeleteDeploymentStrategy(deployment, api, taskContext, genericResourceService, parameter)
                    }
                }
                .defaultIfEmpty(Unit)
                .last()
                .toSingle()
    }

    private fun switchDeleteDeploymentStrategy(deployment: Deployment, api: AzureApi, taskContext: AzureTaskContext, genericResourceService: GenericResourceService, parameter: DeleteDeploymentTaskParameter): Observable<Unit> {
        val deploymentFeatures = deployment.inner()
            .tags
            ?.get(AzureConstants.TAG_FEATURES)
            ?.let {
                try {
                    AzureUtils.getDeploymentFeatures(it)
                } catch (e: DeploymentFeaturesDescriptorError) {
                    LOG.warnAndDebugDetails("Incorrect ${AzureConstants.TAG_FEATURES} tag value for deployment ${deployment.name()}. CorellationId=${taskContext.corellationId}", e)
                    null
                }
            }
        if (deploymentFeatures == null || !deploymentFeatures.isVM || !deploymentFeatures.isSafeRemoval)
            return deleteDeployment(deployment, api, taskContext, genericResourceService)
        else
            LOG.debug("Deployment supports safe removal. CorellationId=${taskContext.corellationId}")
            return safeDeleteVirtualMachineDeployment(deployment, api, taskContext, genericResourceService, parameter)
    }

    private fun safeDeleteVirtualMachineDeployment(deployment: Deployment, api: AzureApi, taskContext: AzureTaskContext, genericResourceService: GenericResourceService, parameter: DeleteDeploymentTaskParameter): Observable<Unit> =
        cancelRunningDeployment(deployment, taskContext)
            .flatMap { cancelledDeployment ->
                val virtualMachineResource = getVirtualMachineResource(ResourceUtils.subscriptionFromResourceId(deployment.id()), parameter)
                LOG.debug("Removing vm ${virtualMachineResource.resourceId}. CorellationId=${taskContext.corellationId}")
                deleteGenericResource(api, taskContext, virtualMachineResource, genericResourceService)
                    .defaultIfEmpty(Unit)
                    .flatMap {
                        val deploymentResource = ResourceDescriptor(deployment.id(), DEPLOYMENT_RESOURCE_TYPE)
                        LOG.debug("Removing deployment template ${deploymentResource.resourceId}. CorellationId=${taskContext.corellationId}")
                        deleteGenericResource(api, taskContext, deploymentResource, genericResourceService)
                    }
                    .defaultIfEmpty(Unit)
                    .concatMap {
                        LOG.debug("Deployment ${cancelledDeployment.name()} has been deleted. Id: ${cancelledDeployment.id()}, CorellationId=${taskContext.corellationId}")
                        val inner = cancelledDeployment.inner()
                        myNotifications.raise(AzureTaskDeploymentStatusChangedEventArgs(
                            api,
                            inner.id(),
                            inner.name(),
                            inner.properties().provisioningState(),
                            inner.properties().providers(),
                            inner.properties().dependencies(),
                            taskContext,
                            true
                        ))
                    }
            }

    private fun getVirtualMachineResource(subscriptionId: String, parameter: DeleteDeploymentTaskParameter): ResourceDescriptor =
        ResourceDescriptor(
            ResourceUtils.constructResourceId(
                subscriptionId,
                parameter.resourceGroupName,
                VIRTUAL_MACHINES_PROVIDER_NAMESPACE,
                VIRTUAL_MACHINES_RESOURCE_TYPE_SHORT,
                parameter.name,
                ""),
            VIRTUAL_MACHINES_RESOURCE_TYPE)


    private fun deleteDeployment(deployment: Deployment, api: AzureApi, taskContext: AzureTaskContext, genericResourceService: GenericResourceService): Observable<Unit> =
        cancelRunningDeployment(deployment, taskContext)
            .flatMap { cancelledDeployment ->
                getDeployedResources(cancelledDeployment, api, taskContext)
                    .concatMap { deleteResource(it, api, genericResourceService, taskContext) }
                    .defaultIfEmpty(Unit)
                    .last()
                    .flatMap {
                        taskContext
                            .getDeferralSequence()
                            .flatMap {
                                api.deployments()
                                    .deleteByIdAsync(cancelledDeployment.id())
                                    .toObservable<Unit>()
                                    .concatWith(Observable.just(Unit))
                            }
                    }
                    .concatMap {
                        LOG.debug("Deployment ${cancelledDeployment.name()} has been deleted. Id: ${cancelledDeployment.id()}, CorellationId=${taskContext.corellationId}")
                        val inner = cancelledDeployment.inner()
                        myNotifications.raise(AzureTaskDeploymentStatusChangedEventArgs(
                            api,
                            inner.id(),
                            inner.name(),
                            inner.properties().provisioningState(),
                            inner.properties().providers(),
                            inner.properties().dependencies(),
                            taskContext,
                            true
                        ))
                    }
            }

    private fun getDeployedResources(deployment: Deployment, api: AzureApi, taskContext: AzureTaskContext): Observable<ResourceDescriptor> {
        return taskContext
            .getDeferralSequence()
            .flatMap {
                deployment
                    .deploymentOperations()
                    .listAsync()
                    .sorted { t1, t2 ->
                        if (isSpecialDeploymentResource(t1.targetResource()))
                            RESOURCE_PROPRITY_HIGH
                        else if (isSpecialDeploymentResource(t2.targetResource()))
                            RESOURCE_PROPRITY_LOW
                        else
                            t2.timestamp().compareTo(t1.timestamp())
                    }
                    .concatMap { operation ->
                        var result: Observable<ResourceDescriptor> = Observable.empty<ResourceDescriptor>()
                        if (operation.provisioningState().equals(PROVISIONING_STATE_FAILED, ignoreCase = true)) {
                            LOG.debug(
                                "Deployment operation failed. OperationId ${operation.operationId()}. CorellationId=${taskContext.corellationId}. Provisioning operation: ${
                                    format(
                                        operation.inner().properties()
                                    )
                                }."
                            )
                            val statusMessage = operation.statusMessage() as StatusMessage?
                            if (statusMessage != null) {
                                val error = statusMessage.error()
                                val code = error.code()
                                val target = error.target()
                                if (code.equals(OPERATION_CODE_CONFLICTING_USER_INPUT, ignoreCase = true)) {
                                    LOG.debug("Resource ${target} will be deleted. OperationId ${operation.operationId()}. CorellationId=${taskContext.corellationId}")
                                    val resourceType = "${ResourceUtils.resourceProviderFromResourceId(target)}/${ResourceUtils.resourceTypeFromResourceId(target)}"
                                    result = Observable.just(ResourceDescriptor(target, resourceType))
                                } else {
                                    LOG.debug("Resource ${target} will not be deleted. OperationId ${operation.operationId()}. CorellationId=${taskContext.corellationId}")
                                }
                            }
                        } else {
                            val targetResource = operation.targetResource()
                            if (targetResource != null) {
                                if (targetResource.resourceType().equals(VIRTUAL_MACHINES_RESOURCE_TYPE, ignoreCase = true)) {
                                    result = getVMResources(taskContext) {
                                        api
                                            .virtualMachines()
                                            .getByIdAsync(targetResource.id())
                                    }
                                } else {
                                    result = Observable.just(ResourceDescriptor(targetResource.id(), targetResource.resourceType()))
                                }
                            }
                        }
                        result
                    }
                    .distinctUntilChanged()
                    .filter {
                        if (isKnownGenericResourceType(it.resourceType)) return@filter true
                        if (isKnownGenericResourceName(it.resourceId)) return@filter true

                        when (it.resourceType) {
                            DEPLOYMENT_RESOURCE_TYPE,
                            CONTAINER_GROUPS_RESOURCE_TYPE,
                            NETWORK_PROFILE_RESOURCE_TYPE,
                            VIRTUAL_MACHINES_RESOURCE_TYPE,
                            DISKS_RESOURCE_TYPE,
                            NETWORK_INTERFACES_RESOURCE_TYPE,
                            NETWORK_PUBLIC_IP_RESOURCE_TYPE -> true

                            else -> {
                                LOG.debug("Skip deleting resource: ${it.resourceId}. CorellationId=${taskContext.corellationId}")
                                false
                            }
                        }
                    }
            }
    }

    private fun getVMResources(taskContext: AzureTaskContext, virtualMachineSource: () -> Observable<VirtualMachine?>): Observable<ResourceDescriptor> {
        return taskContext
            .getDeferralSequence()
            .flatMap {
                virtualMachineSource()
                    .filter { vm: VirtualMachine? -> vm != null && vm.isManagedDiskEnabled }
                    .map { it!! }
                    .flatMap { vm ->
                        Observable.concat(
                            getVmResource(vm),
                            getOsDiskResource(vm),
                            getDataDiskResources(vm)
                        )
                    }
            }
    }

    private fun getVmResource(virtualMachine: VirtualMachine): Observable<ResourceDescriptor> =
        Observable.just(ResourceDescriptor(virtualMachine.id(), VIRTUAL_MACHINES_RESOURCE_TYPE))

    private fun getOsDiskResource(virtualMachine: VirtualMachine): Observable<ResourceDescriptor> =
        if (virtualMachine.osDiskId() != null)
            Observable.just(ResourceDescriptor(virtualMachine.osDiskId(), DISKS_RESOURCE_TYPE))
        else
            Observable.empty()

    private fun getDataDiskResources(virtualMachine: VirtualMachine): Observable<ResourceDescriptor> =
        Observable.from(
            virtualMachine.dataDisks().values
                .filter { disk -> disk.creationMethod() == DiskCreateOptionTypes.FROM_IMAGE }
                .map { disk -> ResourceDescriptor(disk.id(), DISKS_RESOURCE_TYPE) }
        )


    private fun isKnownGenericResourceType(resourceType: String): Boolean =
        TeamCityProperties
            .getPropertyOrNull(TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_KNOWN_RESOURCE_TYPES)
            ?.let {
                it.split(',').map { it.trim().lowercase(Locale.getDefault()) }.filter { it.isNotEmpty() }.toSet()
            }
            ?.contains(resourceType.lowercase(Locale.getDefault()))
            ?: false

    private fun isKnownGenericResourceName(resourceId: String): Boolean =
        TeamCityProperties
            .getPropertyOrNull(TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_KNOWN_RESOURCE_NAMES)
            ?.let {
                it.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            }
            ?.any {
                resourceId.contains(it, ignoreCase = true)
            }
            ?: false

    private fun deleteResource(
        resourceDescriptor: ResourceDescriptor,
        api: AzureApi,
        genericResourceService: GenericResourceService,
        taskContext: AzureTaskContext
    ): Observable<Unit> {
        return taskContext
            .getDeferralSequence()
            .flatMap {
                LOG.debug("Deleting resource ${resourceDescriptor.resourceId}. CorellationId=${taskContext.corellationId}")
                when (resourceDescriptor.resourceType) {
                    CONTAINER_GROUPS_RESOURCE_TYPE -> api.containerGroups().deleteByIdAsync(resourceDescriptor.resourceId).toObservable<Unit>()
                    NETWORK_PROFILE_RESOURCE_TYPE -> {
                        val id = ResourceId.fromString(resourceDescriptor.resourceId)
                        val networkProfilesService = api.networks().manager().inner().networkProfiles()
                        networkProfilesService
                            .getByResourceGroupAsync(id.resourceGroupName(), id.name())
                            .filter { it != null }
                            .concatMap { networkProfile ->
                                networkProfile.containerNetworkInterfaces().clear()
                                networkProfile.withContainerNetworkInterfaceConfigurations(emptyList())
                                networkProfilesService
                                    .createOrUpdateAsync(id.resourceGroupName(), id.name(), networkProfile)
                                    .onErrorReturn {
                                        LOG.debug("Could not reset network profile ${resourceDescriptor.resourceId}. It will be removed. CorellationId=${taskContext.corellationId} Error: ${it.message}")
                                        networkProfile
                                    }
                            }
                            .concatMap {
                                networkProfilesService
                                    .deleteAsync(id.resourceGroupName(), id.name()).map({ Unit })
                                    .delaySubscription(
                                        TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_CONTAINER_NIC_RETRY_DELAY_SEC, 30),
                                        TimeUnit.SECONDS,
                                        Schedulers.io()
                                    )
                                    .retry { attemptNo, throwable ->
                                        LOG.debug("Could not delete network profile ${resourceDescriptor.resourceId}.  CorellationId=${taskContext.corellationId}. Attempt: ${attemptNo}. Error: ${throwable.message}")
                                        attemptNo <= 4
                                    }
                            }
                    }

                    DEPLOYMENT_RESOURCE_TYPE -> api
                        .deployments()
                        .getByIdAsync(resourceDescriptor.resourceId)
                        .filter { deployment: Deployment? -> deployment != null }
                        .flatMap {
                            deleteDeployment(
                                it,
                                api,
                                taskContext,
                                genericResourceService
                            )
                        }

                    else -> deleteGenericResource(api, taskContext, resourceDescriptor, genericResourceService)
                }
                    .defaultIfEmpty(Unit)
                    .doOnNext {
                        LOG.debug("Resource ${resourceDescriptor.resourceId} has been deleted. CorellationId=${taskContext.corellationId}")
                    }
                    .doOnError {
                        LOG.warnAndDebugDetails("Error occured during deletion of resource ${resourceDescriptor.resourceId}, corellationId=${taskContext.corellationId} :", it)
                    }
            }
    }

    private fun deleteGenericResource(
        api: AzureApi,
        taskContext: AzureTaskContext,
        resourceDescriptor: ResourceDescriptor,
        genericResourceService: GenericResourceService
    ): Observable<Unit> {
        LOG.debug("Resource ${resourceDescriptor.resourceId} will be removed. CorellationId=${taskContext.corellationId}")
        if (TeamCityProperties.getBooleanOrTrue(TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_USE_MILTITHREAD_POLLING)) {
            val manager = api.genericResources().manager()
            return getProviderApiVersion(api, taskContext, resourceDescriptor)
                .flatMap { apiVersion ->
                    LOG.debug("Got provider for resource ${resourceDescriptor.resourceId}. CorellationId=${taskContext.corellationId}")
                    val source = taskContext
                        .getDeferralSequence()
                        .flatMap {
                            LOG.debug("Removing resource ${resourceDescriptor.resourceId}. CorellationId=${taskContext.corellationId}")
                            genericResourceService
                                .deleteById(
                                    resourceDescriptor.resourceId,
                                    apiVersion,
                                    manager.inner().acceptLanguage(),
                                    manager.inner().userAgent()
                                )
                        }

                    manager
                        .inner()
                        .azureClient
                        .postOrDeleteAsync<Void>(source, LongRunningOperationOptions.DEFAULT) {
                            taskContext.getDeferralSequence()
                        }
                        .map { Unit }
                        .defaultIfEmpty(Unit)
                }
        }
        return taskContext
            .getDeferralSequence()
            .flatMap {
                api.genericResources().deleteByIdAsync(resourceDescriptor.resourceId).toObservable<Unit>()
            }
            .defaultIfEmpty(Unit)
    }

    private fun getProviderApiVersion(
        api: AzureApi,
        taskContext: AzureTaskContext,
        resourceDescriptor: ResourceDescriptor): Observable<String> {
        val providerName = ResourceUtils.resourceProviderFromResourceId(resourceDescriptor.resourceId)
        val provider = providerToApiVersionMap.get(providerName)
        return if (provider != null && TeamCityProperties.getBooleanOrTrue(TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_USE_PROVIDER_CACHE)) {
            LOG.debug("Found provider in cache. Resource ${resourceDescriptor.resourceId}, provider: ${providerName}. CorellationId=${taskContext.corellationId}")
            Observable.just(provider)
        } else {
            val manager = api.genericResources().manager()
            taskContext
                .getDeferralSequence()
                .flatMap {
                    LOG.debug("Getting provider for resource ${resourceDescriptor.resourceId}. CorellationId=${taskContext.corellationId}")
                    manager
                        .providers()
                        .getByNameAsync(providerName)
                        .map {
                            LOG.debug("Fetched resource provider. Resource ${resourceDescriptor.resourceId}, provider: ${providerName}. CorellationId=${taskContext.corellationId}")
                            val internalProvider = InternalProvider(it.inner())
                            providerToApiVersionMap.putIfAbsent(providerName, internalProvider)
                            internalProvider
                        }
                }
        }
            .map { ResourceUtils.defaultApiVersion(resourceDescriptor.resourceId, it) }
    }

    private fun cancelRunningDeployment(deployment: Deployment, taskContext: AzureTaskContext): Observable<Deployment> {
        LOG.debug("Deleting deployment ${deployment.name()} in group ${deployment.resourceGroupName()}. Provisioning state ${deployment.provisioningState()}. CorellationId=${taskContext.corellationId}")
        if (deployment.provisioningState().equals(PROVISIONING_STATE_CANCELLED, ignoreCase = true)) {
            LOG.debug("Deployment ${deployment.name()} in group ${deployment.resourceGroupName()} was canceled.  CorellationId=${taskContext.corellationId}")
        }

        if (deployment.provisioningState().equals(PROVISIONING_STATE_RUNNING, ignoreCase = true)) {
            val cancelOperationState = BehaviorSubject.create<Notification<Deployment>>()
            return Observable.defer {
                taskContext
                    .getDeferralSequence()
                    .flatMap {
                        deployment.cancelAsync().toObservable<Deployment>().materialize().take(1).doOnNext { cancelOperationState.onNext(it) }
                    }
            }.repeatWhen {
                LOG.debug("Canceling running deployment ${deployment.name()}. CorellationId=${taskContext.corellationId}")

                if (!cancelOperationState.hasValue() || cancelOperationState.value == null) {
                    Observable.just(Unit).concatWith(Observable.never())
                } else {
                    val cancelOperationStateValue = cancelOperationState.value
                    when (cancelOperationStateValue.kind) {
                        Notification.Kind.OnNext -> deployment.refreshAsync().flatMap {
                            if (it.provisioningState().equals(PROVISIONING_STATE_CANCELLED, ignoreCase = true))
                                Observable.empty()
                            else
                                Observable.just(Unit).concatWith(Observable.never())
                        }

                        Notification.Kind.OnError -> Observable.error<Notification<Deployment>>(cancelOperationState.throwable)
                        Notification.Kind.OnCompleted -> Observable.empty<Notification<Deployment>>()
                        null -> Observable.empty<Notification<Deployment>>()
                    }
                }
            }
                .dematerialize<Deployment>()
                .take(1)
                .concatWith(Observable.just(deployment))
        }
        return Observable.just(deployment)
    }

    private data class ResourceDescriptor(
        val resourceId: String,
        val resourceType: String
    )

    private interface GenericResourceService {
        @Headers("Content-Type: application/json; charset=utf-8")
        @HTTP(path = "{resourceId}", method = "DELETE", hasBody = true)
        fun deleteById(
            @Path(value = "resourceId", encoded = true) resourceId: String,
            @Query("api-version") apiVersion: String,
            @Header("accept-language") acceptLanguage: String,
            @Header("User-Agent") userAgent: String
        ): Observable<Response<ResponseBody>>
    }

    class DeploymentOperationPropertiesInternal(properties: DeploymentOperationProperties) {
        val provisioningOperation: ProvisioningOperation? = properties.provisioningOperation()
        val provisioningState: String? = properties.provisioningState()
        val timestamp: String? = properties.timestamp()?.toString()
        val duration: String? = properties.duration()
        val serviceRequestId: String? = properties.serviceRequestId()
        val statusCode: String? = properties.statusCode()
        val statusMessage: StatusMessageInternal? = properties.statusMessage()?.let { StatusMessageInternal(it) }
    }

    class StatusMessageInternal(statusMessage: StatusMessage) {
        val status: String? = statusMessage.status()
        val error: ErrorResponseInternal? = statusMessage.error()?.let { ErrorResponseInternal(it) }
    }

    class ErrorResponseInternal(errorResponse: ErrorResponse) {
        val code: String? = errorResponse.code()
        val message: String? = errorResponse.message()
        val target: String? = errorResponse.target()
        val details: List<ErrorResponseInternal>? = errorResponse.details()?.let { it.map { ErrorResponseInternal((it)) } }
    }

    private class InternalProvider(private val inner : ProviderInner) : Provider {
        private val key = UUID.randomUUID().toString()
        override fun key(): String = key

        override fun inner(): ProviderInner = inner

        override fun namespace(): String = inner.namespace()

        override fun registrationState(): String = inner.registrationState()

        override fun resourceTypes(): MutableList<ProviderResourceType> = inner.resourceTypes()
    }

    companion object {
        private val LOG = Logger.getInstance(DeleteDeploymentTaskImpl::class.java.name)
        private const val VIRTUAL_MACHINES_PROVIDER_NAMESPACE = "Microsoft.Compute"
        private const val VIRTUAL_MACHINES_RESOURCE_TYPE_SHORT = "virtualMachines"
        private const val VIRTUAL_MACHINES_RESOURCE_TYPE = "Microsoft.Compute/virtualMachines"
        private const val CONTAINER_GROUPS_RESOURCE_TYPE = "Microsoft.ContainerInstance/containerGroups"

        private const val NETWORK_PROFILE_RESOURCE_TYPE = "Microsoft.Network/networkProfiles"
        private const val NETWORK_INTERFACES_RESOURCE_TYPE = "Microsoft.Network/networkInterfaces"
        private const val NETWORK_PUBLIC_IP_RESOURCE_TYPE = "Microsoft.Network/publicIPAddresses"

        private const val VIRTUAL_MACHINE_EXTENSIONS_RESOURCE_TYPE = "Microsoft.Compute/virtualMachines/extensions"
        private const val DISKS_RESOURCE_TYPE = "Microsoft.Compute/disks"

        private const val DEPLOYMENT_RESOURCE_TYPE ="Microsoft.Resources/deployments"

        private const val PROVISIONING_STATE_CANCELLED = "Canceled"
        private const val PROVISIONING_STATE_RUNNING = "Running"
        private const val PROVISIONING_STATE_FAILED = "Failed"

        private const val OPERATION_CODE_CONFLICTING_USER_INPUT = "ConflictingUserInput"

        private const val OS_DISK_DELETE_OPTION_DEPLOYMENT_NAME_SUFFIX = "-jb-5fe33749"

        private const val RESOURCE_PROPRITY_LOW = -1
        private const val RESOURCE_PROPRITY_HIGH = 1

        private val mapper = JsonMapper()
        private fun format(properties: DeploymentOperationProperties): String =
            mapper.writeValueAsString(DeploymentOperationPropertiesInternal(properties))

        private fun isSpecialDeploymentResource(targetResource: TargetResource?) =
            targetResource?.resourceType()?.equals(DEPLOYMENT_RESOURCE_TYPE, ignoreCase = true) == true &&
                    targetResource.resourceName()?.contains(OS_DISK_DELETE_OPTION_DEPLOYMENT_NAME_SUFFIX, ignoreCase = true) == true
    }
}
