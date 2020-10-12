/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.resources.Deployment
import com.microsoft.azure.management.resources.fluentcore.collection.SupportsDeletingById
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskNotifications
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTaskBaseImpl
import rx.Notification
import rx.Observable
import rx.Single
import rx.subjects.BehaviorSubject

data class DeleteDeploymentTaskParameter(
        val resourceGroupName: String,
        val name: String)

class DeleteDeploymentTaskImpl(private val myNotifications: AzureTaskNotifications) : AzureThrottlerTaskBaseImpl<Azure, DeleteDeploymentTaskParameter, Unit>() {
    override fun create(api: Azure, parameter: DeleteDeploymentTaskParameter): Single<Unit> {
        var originalDeployment: Deployment? = null
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
                .doOnNext {
                    LOG.debug("Deleting deployment ${it.name()} in group ${it.resourceGroupName()}. Provisioning state ${it.provisioningState()}")
                    if (it.provisioningState().equals(PROVISIONING_STATE_CANCELLED, ignoreCase = true)) {
                        LOG.debug("Deployment ${it.name()} in group ${it.resourceGroupName()} was canceled")
                    }
                    originalDeployment = it
                }
                .flatMap { deployment ->
                    if (deployment.provisioningState().equals(PROVISIONING_STATE_RUNNING, ignoreCase = true)) {
                        val cancelOperationState = BehaviorSubject.create<Notification<Deployment>>()
                        Observable.
                                defer {
                                    deployment.
                                            cancelAsync().
                                            toObservable<Deployment>().
                                            materialize().
                                            take(1).
                                            doOnNext { cancelOperationState.onNext(it) }
                                }.
                                repeatWhen {
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
                    } else {
                        Observable.just(deployment)
                    }
                }
                .flatMap {deployment ->
                    deployment
                            .deploymentOperations()
                            .listAsync()
                            .sorted { t1, t2 -> t2.timestamp().compareTo(t1.timestamp()) }
                            .map { it.targetResource() }
                            .filter { it != null }
                            .concatMap { targetResource ->
                                val result = Observable.just(targetResource.id() to targetResource.resourceType())
                                if (targetResource.resourceType().equals(VIRTUAL_MACHINES_RESOURCE_TYPE, ignoreCase = true))
                                        api
                                                .virtualMachines()
                                                .getByIdAsync(targetResource.id())
                                                .filter { it != null && it.isManagedDiskEnabled }
                                                .map { it.osDiskId() }
                                                .concatMap {
                                                    result.concatWith(Observable.just(it to DISKS_RESOURCE_TYPE))
                                                }
                                else
                                    result
                            }
                            .distinctUntilChanged()
                            .flatMap { (resourceId, resourceType) ->
                                LOG.debug("Deleting resource $resourceId")

                                when(resourceType) {
                                    CONTAINER_GROUPS_RESOURCE_TYPE -> api.containerGroups() as SupportsDeletingById
                                    else -> api.genericResources() as SupportsDeletingById
                                }
                                .deleteByIdAsync(resourceId)
                                .toObservable<Unit>()
                                .concatWith(Observable.just(Unit))
                                .onErrorReturn {
                                    LOG.warnAndDebugDetails("Error occured during deletion of resource $resourceId :", it)
                                    Observable.just(Unit)
                                }
                            }
                }
                .concatWith(Observable.just(Unit))
                .last()
                .doOnNext {
                    originalDeployment?.let { myNotifications.raise(AzureTaskDeploymentStatusChangedEventArgs(api, it, true)) }
                }
                .toSingle()
    }

    companion object {
        private val LOG = Logger.getInstance(DeleteDeploymentTaskImpl::class.java.name)
        private const val VIRTUAL_MACHINES_RESOURCE_TYPE = "Microsoft.Compute/virtualMachines"
        private const val CONTAINER_GROUPS_RESOURCE_TYPE = "Microsoft.ContainerInstance/containerGroups"
        private const val DISKS_RESOURCE_TYPE = "Microsoft.Compute/disks"
        private const val PROVISIONING_STATE_CANCELLED = "Canceled"
        private const val PROVISIONING_STATE_RUNNING = "Running"
    }
}
