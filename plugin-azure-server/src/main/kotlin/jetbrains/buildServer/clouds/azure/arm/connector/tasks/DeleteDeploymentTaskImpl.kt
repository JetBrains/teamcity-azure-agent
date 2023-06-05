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

import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.management.compute.DiskCreateOptionTypes
import com.microsoft.azure.management.resources.Deployment
import com.microsoft.azure.management.resources.fluentcore.arm.ResourceId
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskNotifications
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTaskBaseImpl
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_CONTAINER_NIC_RETRY_DELAY_SEC
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_KNOWN_RESOURCE_TYPES
import jetbrains.buildServer.serverSide.TeamCityProperties
import rx.Notification
import rx.Observable
import rx.Scheduler
import rx.Single
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.util.*
import java.util.concurrent.TimeUnit

data class DeleteDeploymentTaskParameter(
        val resourceGroupName: String,
        val name: String)

class DeleteDeploymentTaskImpl(private val myNotifications: AzureTaskNotifications) : AzureThrottlerTaskBaseImpl<AzureApi, DeleteDeploymentTaskParameter, Unit>() {
    override fun create(api: AzureApi, parameter: DeleteDeploymentTaskParameter): Single<Unit> {
        return api
                .deployments()
                .getByResourceGroupAsync(parameter.resourceGroupName, parameter.name)
                .onErrorResumeNext {
                    LOG.debug("Deployment ${parameter.name} in group ${parameter.resourceGroupName} was not found", it)
                    Observable.empty()
                }
                .doOnNext {
                    if (it == null) {
                        LOG.debug("Deleting deployment error. Could not find deployment ${parameter.name} in group ${parameter.resourceGroupName}")
                    }
                }
                .filter { it != null }
                .flatMap { deleteDeployment(it, api) }
                .defaultIfEmpty(Unit)
                .last()
                .toSingle()
    }

    private fun deleteDeployment(deployment: Deployment, api: AzureApi): Observable<Unit> =
        cancelRunningDeployment(deployment)
            .flatMap { cancelledDeployment ->
                getDeployedResources(cancelledDeployment, api)
                    .concatMap { deleteResource(it, api) }
                    .defaultIfEmpty(Unit)
                    .last()
                    .flatMap {
                        api.deployments()
                            .deleteByIdAsync(cancelledDeployment.id())
                            .toObservable<Unit>()
                            .concatWith(Observable.just(Unit))
                    }
                    .doOnNext {
                        LOG.debug("Deployment ${cancelledDeployment.name()} has been deleted. Id: ${cancelledDeployment.id()}")
                        myNotifications.raise(AzureTaskDeploymentStatusChangedEventArgs(api, cancelledDeployment, true))
                    }
            }

    private fun getDeployedResources(deployment: Deployment, api: AzureApi): Observable<ResourceDescriptor> =
        deployment
            .deploymentOperations()
            .listAsync()
            .sorted { t1, t2 -> t2.timestamp().compareTo(t1.timestamp()) }
            .map { it.targetResource() }
            .filter { it != null }
            .concatMap { targetResource ->
                val result = Observable.just(ResourceDescriptor(targetResource.id(), targetResource.resourceType()))
                if (targetResource.resourceType().equals(VIRTUAL_MACHINES_RESOURCE_TYPE, ignoreCase = true)) {
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
                        LOG.debug("Skip deleting resource: ${it.resourceId}")
                        false
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

    private fun deleteResource(resourceDescriptor: ResourceDescriptor, api: AzureApi): Observable<Unit> {
        LOG.debug("Deleting resource ${resourceDescriptor.resourceId}")

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
                                LOG.debug("Could not reset network profile ${resourceDescriptor.resourceId}. It will be removed. Error: ${it.message}")
                                networkProfile
                            }
                    }
                    .concatMap {
                        networkProfilesService
                            .deleteAsync(id.resourceGroupName(), id.name()).map({ Unit })
                            .delaySubscription(TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_CONTAINER_NIC_RETRY_DELAY_SEC, 30), TimeUnit.SECONDS, Schedulers.io())
                            .retry { attemptNo, throwable ->
                                LOG.debug("Could not delete network profile ${resourceDescriptor.resourceId}. Attempt: ${attemptNo}. Error: ${throwable.message}")
                                attemptNo <= 4
                            }
                    }
            }
            DEPLOYMENT_RESOURCE_TYPE -> api.deployments().getByIdAsync(resourceDescriptor.resourceId).flatMap { deleteDeployment(it, api) }
            else -> api.genericResources().deleteByIdAsync(resourceDescriptor.resourceId).toObservable<Unit>()
        }
            .defaultIfEmpty(Unit)
            .doOnNext {
                LOG.debug("Resource ${resourceDescriptor.resourceId} has been deleted")
            }
            .onErrorReturn {
                LOG.warnAndDebugDetails("Error occured during deletion of resource ${resourceDescriptor.resourceId} :", it)
                Observable.just(Unit)
            }
    }

    private fun cancelRunningDeployment(deployment: Deployment): Observable<Deployment> {
        LOG.debug("Deleting deployment ${deployment.name()} in group ${deployment.resourceGroupName()}. Provisioning state ${deployment.provisioningState()}")
        if (deployment.provisioningState().equals(PROVISIONING_STATE_CANCELLED, ignoreCase = true)) {
            LOG.debug("Deployment ${deployment.name()} in group ${deployment.resourceGroupName()} was canceled")
        }

        if (deployment.provisioningState().equals(PROVISIONING_STATE_RUNNING, ignoreCase = true)) {
            val cancelOperationState = BehaviorSubject.create<Notification<Deployment>>()
            return Observable.defer {
                deployment.cancelAsync().toObservable<Deployment>().materialize().take(1).doOnNext { cancelOperationState.onNext(it) }
            }.repeatWhen {
                LOG.debug("Canceling running deployment ${deployment.name()}")

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
