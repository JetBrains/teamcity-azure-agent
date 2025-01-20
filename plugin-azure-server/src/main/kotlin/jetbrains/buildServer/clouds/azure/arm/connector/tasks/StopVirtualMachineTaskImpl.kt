package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskNotifications
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTaskBaseImpl
import rx.Observable
import rx.Single

data class StopVirtualMachineTaskParameter(
        val groupId: String,
        val name: String)

class StopVirtualMachineTaskImpl(private val myNotifications: AzureTaskNotifications) : AzureThrottlerTaskBaseImpl<AzureApi, StopVirtualMachineTaskParameter, Unit>() {
    override fun create(api: AzureApi, taskContext: AzureTaskContext, parameter: StopVirtualMachineTaskParameter): Single<Unit> {
        return api
                .virtualMachines()
                .getByResourceGroupAsync(parameter.groupId, parameter.name)
                .flatMap { vm ->
                    if (vm != null) {
                        vm.deallocateAsync()
                            .toObservable<Unit>()
                            .concatMap { myNotifications.raise(AzureTaskVirtualMachineStatusChangedEventArgs(api, vm)) }
                    } else {
                        LOG.warnAndDebugDetails("Could not find virtual machine to stop. GroupId: ${parameter.groupId}, Name: ${parameter.name}", null)
                        Observable.just(Unit)
                    }
                }
                .defaultIfEmpty(Unit)
                .toSingle()
    }

    companion object {
        private val LOG = Logger.getInstance(StopVirtualMachineTaskImpl::class.java.name)
    }
}
