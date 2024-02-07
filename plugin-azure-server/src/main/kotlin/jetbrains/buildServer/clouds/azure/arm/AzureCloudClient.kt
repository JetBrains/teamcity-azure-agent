

package jetbrains.buildServer.clouds.azure.arm

import jetbrains.buildServer.clouds.CloudClientParameters
import jetbrains.buildServer.clouds.azure.AzureCloudClientBase
import jetbrains.buildServer.clouds.azure.AzureCloudImagesHolder
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerSchedulersProvider
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * ARM cloud client.
 */
class AzureCloudClient(private val params: CloudClientParameters,
                       apiConnector: CloudApiConnector<AzureCloudImage, AzureCloudInstance>,
                       imagesHolder: AzureCloudImagesHolder,
                       schedulersProvider: AzureThrottlerSchedulersProvider)
    : AzureCloudClientBase<AzureCloudInstance, AzureCloudImage, AzureCloudImageDetails>(params, apiConnector, imagesHolder) {
    private val myScope: CoroutineScope

    init {
        myScope = CoroutineScope(SupervisorJob() + schedulersProvider.getDispatcher())
    }

    override fun createImage(imageDetails: AzureCloudImageDetails): AzureCloudImage {
        if (imageDetails.target == AzureCloudDeployTarget.NewGroup && imageDetails.region.isNullOrEmpty()) {
            imageDetails.region = params.getParameter(AzureConstants.REGION)
        }

        return AzureCloudImage(imageDetails, myApiConnector as AzureApiConnector, myScope)
    }
}
