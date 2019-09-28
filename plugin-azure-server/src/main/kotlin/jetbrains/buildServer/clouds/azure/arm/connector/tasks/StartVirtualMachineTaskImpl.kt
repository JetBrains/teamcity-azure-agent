package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTask
import rx.Single

data class StartVirtualMachineTaskParameter(
        val groupId: String,
        val name: String)

class StartVirtualMachineTaskImpl : AzureThrottlerTask<Azure, StartVirtualMachineTaskParameter, Unit> {
    override fun create(api: Azure, parameter: StartVirtualMachineTaskParameter): Single<Unit> {
        return api
                .virtualMachines()
                .getByResourceGroupAsync(parameter.groupId, parameter.name)
                .flatMap { it.startAsync().toObservable<Unit>() }
                .toSingle()
    }
}
