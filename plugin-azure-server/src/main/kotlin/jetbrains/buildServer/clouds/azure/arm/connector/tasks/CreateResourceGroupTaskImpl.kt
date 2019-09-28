package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.resources.fluentcore.model.Indexable
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTask
import rx.Single

data class CreateResourceGroupTaskParameter(
        val groupName: String,
        val region: String)

class CreateResourceGroupTaskImpl : AzureThrottlerTask<Azure, CreateResourceGroupTaskParameter, Indexable> {
    override fun create(api: Azure, parameter: CreateResourceGroupTaskParameter): Single<Indexable> {
        return api
                .resourceGroups()
                .define(parameter.groupName)
                .withRegion(parameter.region)
                .createAsync()
                .toSingle()
    }
}

