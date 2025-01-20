package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTaskBaseImpl
import rx.Single

class DeleteResourceGroupTaskImpl : AzureThrottlerTaskBaseImpl<AzureApi, String, Unit>() {
    override fun create(api: AzureApi, taskContext: AzureTaskContext, parameter: String): Single<Unit> {
        return api
                .resourceGroups()
                .deleteByNameAsync(parameter)
                .toObservable<Unit>()
                .defaultIfEmpty(Unit)
                .toSingle()
    }
}
