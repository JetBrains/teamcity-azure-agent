package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.resources.DeploymentMode
import com.microsoft.azure.management.resources.fluentcore.model.Indexable
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTask
import rx.Single

data class CreateDeploymentTaskParameter(
        val groupName: String,
        val deploymentName: String,
        val template: String,
        val params: String)

class CreateDeploymentTaskImpl : AzureThrottlerTask<Azure, CreateDeploymentTaskParameter, Indexable> {
    override fun create(api: Azure, parameter: CreateDeploymentTaskParameter): Single<Indexable> {
        return api
                .deployments()
                .define(parameter.deploymentName)
                .withExistingResourceGroup(parameter.groupName)
                .withTemplate(parameter.template)
                .withParameters(parameter.params)
                .withMode(DeploymentMode.INCREMENTAL)
                .createAsync()
                .toSingle()
    }
}
