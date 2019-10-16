package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.resources.Deployment
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTask
import rx.Observable
import rx.Single

data class DeleteDeploymentTaskParameter(
        val resourceGroupName: String,
        val name: String)

class DeleteDeploymentTaskImpl : AzureThrottlerTask<Azure, DeleteDeploymentTaskParameter, Unit> {
    override fun create(api: Azure, parameter: DeleteDeploymentTaskParameter): Single<Unit> {
        return api
                .deployments()
                .getByResourceGroupAsync(parameter.resourceGroupName, parameter.name)
                .onErrorResumeNext {
                    LOG.debug("Deployment ${parameter.name} in group ${parameter.resourceGroupName} was not found", it)
                    Observable.empty()
                }
                .doOnNext{
                    LOG.debug("Deleting deployment ${it.name()} in group ${it.resourceGroupName()}")
                    if (it.provisioningState().equals(PROVISIONING_STATE_CANCELLED, ignoreCase = true)) {
                        LOG.debug("Deployment ${it.name()} in group ${it.resourceGroupName()} was canceled")
                    }
                }
                .flatMap { deployment ->
                    if (deployment.provisioningState().equals(PROVISIONING_STATE_RUNNING, ignoreCase = true)) {
                        Observable.defer { deployment.cancelAsync().toObservable<Deployment>() }
                                .repeatWhen {
                                    LOG.debug("Canceling running deployment ${deployment.name()}")

                                    deployment.refreshAsync().flatMap {
                                        if (it.provisioningState().equals(PROVISIONING_STATE_CANCELLED, ignoreCase = true))
                                            Observable.empty()
                                        else
                                            Observable.just(Unit).concatWith(Observable.never())

                                    }
                                }
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
                                val result = Observable.just(targetResource.id())
                                if (targetResource.resourceType().equals(VIRTUAL_MACHINE_RESOURCE_TYPE, ignoreCase = true))
                                        api
                                                .virtualMachines()
                                                .getByIdAsync(targetResource.id())
                                                .filter { it != null && it.isManagedDiskEnabled }
                                                .map { it.osDiskId() }
                                                .concatMap {
                                                    result.concatWith(Observable.just(it))
                                                }
                                else
                                    result
                            }
                            .distinctUntilChanged()
                            .flatMap { resourceId ->
                                LOG.debug("Deleting resource $resourceId")
                                api
                                        .genericResources()
                                        .deleteByIdAsync(resourceId)
                                        .toObservable<Unit>()
                                        .concatWith(Observable.just(Unit))
                            }
                }
                .concatWith(Observable.just(Unit))
                .last()
                .toSingle()
    }

    companion object {
        private val LOG = Logger.getInstance(DeleteDeploymentTaskImpl::class.java.name)
        private const val VIRTUAL_MACHINE_RESOURCE_TYPE = "Microsoft.Compute/virtualMachines"
        private const val PROVISIONING_STATE_CANCELLED = "Canceled"
        private const val PROVISIONING_STATE_RUNNING = "Running"
    }
}
