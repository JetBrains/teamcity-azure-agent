package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTask
import rx.Single

data class RestartVirtualMachineTaskParameter(
        val groupId: String,
        val name: String)

class RestartVirtualMachineTaskImpl : AzureThrottlerTask<Azure, RestartVirtualMachineTaskParameter, Unit> {
    override fun create(api: Azure, parameter: RestartVirtualMachineTaskParameter): Single<Unit> {
        return api
                .virtualMachines()
                .getByResourceGroupAsync(parameter.groupId, parameter.name)
                .flatMap { it.restartAsync().toObservable<Unit>() }
                .toSingle()
    }
}

