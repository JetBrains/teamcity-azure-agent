

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.LongRunningOperationOptions
import com.microsoft.azure.management.compute.DiskCreateOptionTypes
import com.microsoft.azure.management.resources.Deployment
import com.microsoft.azure.management.resources.fluentcore.arm.ResourceId
import com.microsoft.azure.management.resources.fluentcore.arm.ResourceUtils
import jetbrains.buildServer.clouds.azure.arm.throttler.*
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
                .doOnNext {
                    if (it == null) {
                        LOG.debug("Deleting deployment error. Could not find deployment ${parameter.name} in group ${parameter.resourceGroupName}. CorellationId=${taskContext.corellationId}")
                    }
                }
                .filter { it != null }
                .flatMap { deleteDeployment(it, api, taskContext, genericResourceService) }
                .defaultIfEmpty(Unit)
                .last()
                .toSingle()
    }

    private fun deleteDeployment(deployment: Deployment, api: AzureApi, taskContext: AzureTaskContext, genericResourceService: GenericResourceService): Observable<Unit> =
        cancelRunningDeployment(deployment, taskContext)
            .flatMap { cancelledDeployment ->
                getDeployedResources(cancelledDeployment, api, taskContext)
                    .concatMap { deleteResource(it, api, genericResourceService, taskContext) }
                    .defaultIfEmpty(Unit)
                    .last()
                    .flatMap {
                        taskContext.apply()
                        api.deployments()
                            .deleteByIdAsync(cancelledDeployment.id())
                            .toObservable<Unit>()
                            .concatWith(Observable.just(Unit))
                    }
                    .doOnNext {
                        LOG.debug("Deployment ${cancelledDeployment.name()} has been deleted. Id: ${cancelledDeployment.id()}, CorellationId=${taskContext.corellationId}")
                        val inner = cancelledDeployment.inner()
                        myNotifications.raise(AzureTaskDeploymentStatusChangedEventArgs(
                            api,
                            inner.id(),
                            inner.name(),
                            inner.properties().provisioningState(),
                            inner.properties().providers(),
                            inner.properties().dependencies(),
                            true
                        ))
                    }
            }

    private fun getDeployedResources(deployment: Deployment, api: AzureApi, taskContext: AzureTaskContext): Observable<ResourceDescriptor> {
        taskContext.apply()
        return deployment
            .deploymentOperations()
            .listAsync()
            .sorted { t1, t2 -> t2.timestamp().compareTo(t1.timestamp()) }
            .map { it.targetResource() }
            .filter { it != null }
            .concatMap { targetResource ->
                val result = Observable.just(ResourceDescriptor(targetResource.id(), targetResource.resourceType()))
                if (targetResource.resourceType().equals(VIRTUAL_MACHINES_RESOURCE_TYPE, ignoreCase = true)) {
                    taskContext.apply()
                    api
                        .virtualMachines()
                        .getByIdAsync(targetResource.id())
                        .filter { it != null && it.isManagedDiskEnabled }
                        .flatMap {
                            Observable
                                .just(it.osDiskId())
                                .concatWith(Observable.from(
                                    it
                                        .dataDisks()
                                        .values
                                        .filter { d -> d.creationMethod() == DiskCreateOptionTypes.FROM_IMAGE }
                                        .map { d -> d.id() }
                                ))
                        }
                        .concatMap {
                            result.concatWith(Observable.just(ResourceDescriptor(it, DISKS_RESOURCE_TYPE)))
                        }
                } else {
                    result
                }
            }
            .distinctUntilChanged()
            .filter {
                if (isKnownGenericResourceType(it.resourceType)) return@filter true

                when(it.resourceType) {
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

    private fun isKnownGenericResourceType(resourceType: String): Boolean =
        TeamCityProperties
            .getPropertyOrNull(TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_KNOWN_RESOURCE_TYPES)
            ?.let {
                it.split(',').map { it.trim().lowercase(Locale.getDefault()) }.filter { it.isNotEmpty() }.toSet()
            }
            ?.contains(resourceType.lowercase(Locale.getDefault()))
            ?: false

    private fun deleteResource(
        resourceDescriptor: ResourceDescriptor,
        api: AzureApi,
        genericResourceService: GenericResourceService,
        taskContext: AzureTaskContext
    ): Observable<Unit> {
        LOG.debug("Deleting resource ${resourceDescriptor.resourceId}. CorellationId=${taskContext.corellationId}")
        taskContext.apply()
        return when (resourceDescriptor.resourceType) {
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
                            .delaySubscription(TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_CONTAINER_NIC_RETRY_DELAY_SEC, 30), TimeUnit.SECONDS, Schedulers.io())
                            .retry { attemptNo, throwable ->
                                LOG.debug("Could not delete network profile ${resourceDescriptor.resourceId}.  CorellationId=${taskContext.corellationId}. Attempt: ${attemptNo}. Error: ${throwable.message}")
                                attemptNo <= 4
                            }
                    }
            }
            DEPLOYMENT_RESOURCE_TYPE -> api.deployments().getByIdAsync(resourceDescriptor.resourceId).flatMap { deleteDeployment(
                it,
                api,
                taskContext,
                genericResourceService
            ) }
            else -> deleteGenericResource(api, taskContext, resourceDescriptor, genericResourceService)
        }
            .defaultIfEmpty(Unit)
            .doOnNext {
                LOG.debug("Resource ${resourceDescriptor.resourceId} has been deleted. CorellationId=${taskContext.corellationId}")
            }
            .onErrorReturn {
                LOG.warnAndDebugDetails("Error occured during deletion of resource ${resourceDescriptor.resourceId}, corellationId=${taskContext.corellationId} :", it)
                Observable.just(Unit)
            }
    }

    private fun deleteGenericResource(
        api: AzureApi,
        taskContext: AzureTaskContext,
        resourceDescriptor: ResourceDescriptor,
        genericResourceService: GenericResourceService
    ): Observable<Unit> {
        taskContext.apply()
        if (TeamCityProperties.getBooleanOrTrue(TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_USE_MILTITHREAD_POLLING)) {
            val manager = api.genericResources().manager()
            return manager
                .providers()
                .getByNameAsync(ResourceUtils.resourceProviderFromResourceId(resourceDescriptor.resourceId))
                .map { ResourceUtils.defaultApiVersion(resourceDescriptor.resourceId, it) }
                .flatMap {
                    val source = genericResourceService
                        .deleteById(
                            resourceDescriptor.resourceId,
                            it,
                            manager.inner().acceptLanguage(),
                            manager.inner().userAgent())
                    manager
                        .inner()
                        .azureClient
                        .postOrDeleteAsync<Void>(source, LongRunningOperationOptions.DEFAULT) {
                            taskContext.apply()
                        }
                        .map { Unit }
                }
        }
        return api.genericResources().deleteByIdAsync(resourceDescriptor.resourceId).toObservable<Unit>()
    }

    private fun cancelRunningDeployment(deployment: Deployment, taskContext: AzureTaskContext): Observable<Deployment> {
        LOG.debug("Deleting deployment ${deployment.name()} in group ${deployment.resourceGroupName()}. Provisioning state ${deployment.provisioningState()}. CorellationId=${taskContext.corellationId}")
        if (deployment.provisioningState().equals(PROVISIONING_STATE_CANCELLED, ignoreCase = true)) {
            LOG.debug("Deployment ${deployment.name()} in group ${deployment.resourceGroupName()} was canceled.  CorellationId=${taskContext.corellationId}")
        }

        if (deployment.provisioningState().equals(PROVISIONING_STATE_RUNNING, ignoreCase = true)) {
            val cancelOperationState = BehaviorSubject.create<Notification<Deployment>>()
            return Observable.defer {
                taskContext.apply()
                deployment.cancelAsync().toObservable<Deployment>().materialize().take(1).doOnNext { cancelOperationState.onNext(it) }
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

    companion object {
        private val LOG = Logger.getInstance(DeleteDeploymentTaskImpl::class.java.name)
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
    }
}
