

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskNotifications
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTaskBaseImpl
import rx.Observable
import rx.Single

data class StartVirtualMachineTaskParameter(
        val groupId: String,
        val name: String)

class StartVirtualMachineTaskImpl(private val myNotifications: AzureTaskNotifications) : AzureThrottlerTaskBaseImpl<AzureApi, StartVirtualMachineTaskParameter, Unit>() {
    override fun create(api: AzureApi, taskContext: AzureTaskContext, parameter: StartVirtualMachineTaskParameter): Single<Unit> {
        return api
                .virtualMachines()
                .getByResourceGroupAsync(parameter.groupId, parameter.name)
                .flatMap {vm ->
                    if (vm != null) {
                        vm.startAsync().toObservable<Unit>().doOnCompleted { myNotifications.raise(AzureTaskVirtualMachineStatusChangedEventArgs(api, vm)) }
                    } else {
                        LOG.warnAndDebugDetails("Could not find resource to start. GroupId: ${parameter.groupId}, Name: ${parameter.name}", null)
                        Observable.just(Unit)
                    }
                }
                .defaultIfEmpty(Unit)
                .toSingle()
    }

    companion object {
        private val LOG = Logger.getInstance(StartVirtualMachineTaskImpl::class.java.name)
    }
}
