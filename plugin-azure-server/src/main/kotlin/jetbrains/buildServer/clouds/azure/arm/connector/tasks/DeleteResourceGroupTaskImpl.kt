package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTask
import rx.Single

class DeleteResourceGroupTaskImpl : AzureThrottlerTask<Azure, String, Unit> {
    override fun create(api: Azure, parameter: String): Single<Unit> {
        return api
                .resourceGroups()
                .deleteByNameAsync(parameter)
                .toObservable<Unit>()
                .toSingle()
    }
}

