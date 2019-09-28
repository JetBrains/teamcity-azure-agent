package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTask
import rx.Single

data class StopVirtualMachineTaskParameter(
        val groupId: String,
        val name: String)

class StopVirtualMachineTaskImpl : AzureThrottlerTask<Azure, StopVirtualMachineTaskParameter, Unit> {
    override fun create(api: Azure, parameter: StopVirtualMachineTaskParameter): Single<Unit> {
        return api
                .virtualMachines()
                .getByResourceGroupAsync(parameter.groupId, parameter.name)
                .flatMap { it.deallocateAsync().toObservable<Unit>() }
                .toSingle()
    }
}
