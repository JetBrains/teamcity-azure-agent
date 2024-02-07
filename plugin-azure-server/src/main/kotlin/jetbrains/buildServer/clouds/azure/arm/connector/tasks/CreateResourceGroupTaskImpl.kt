

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.resources.fluentcore.model.Indexable
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTaskBaseImpl
import rx.Single

data class CreateResourceGroupTaskParameter(
        val groupName: String,
        val region: String)

class CreateResourceGroupTaskImpl : AzureThrottlerTaskBaseImpl<AzureApi, CreateResourceGroupTaskParameter, Indexable>() {
    override fun create(api: AzureApi, taskContext: AzureTaskContext, parameter: CreateResourceGroupTaskParameter): Single<Indexable> {
        return api
                .resourceGroups()
                .define(parameter.groupName)
                .withRegion(parameter.region)
                .createAsync()
                .toSingle()
    }
}
