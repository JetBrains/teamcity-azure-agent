package jetbrains.buildServer.clouds.azure.arm.types

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImage
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails
import jetbrains.buildServer.clouds.azure.arm.AzureCloudInstance
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.async
import java.util.*

class AzureImageHandler(private val connector: AzureApiConnector) : AzureHandler {
    private val LOG = Logger.getInstance(AzureImageHandler::class.java.name)

    @Suppress("UselessCallOnNotNull")
    override fun checkImageAsync(image: AzureCloudImage) = async(CommonPool) {
        val exceptions = ArrayList<Throwable>()
        val details = image.imageDetails
        if (details.sourceId.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Invalid source id"))
        }
        if (details.region.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Invalid region"))
        }
        if (details.networkId.isNullOrEmpty() || details.subnetId.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Invalid network settings"))
        }
        if (details.osType.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Invalid OS Type value"))
        }

        val imageId = details.imageId
        if (imageId == null || imageId.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Image ID is empty"))
        } else {
            try {
                connector.getImageNameAsync(imageId)
            } catch (e: Throwable) {
                LOG.infoAndDebugDetails("Failed to get image ID " + imageId, e)
                exceptions.add(e)
            }
        }

        exceptions
    }

    override fun prepareBuilderAsync(instance: AzureCloudInstance) = async(CommonPool) {
        val details = instance.image.imageDetails
        val template = AzureUtils.getResourceAsString("/templates/vm-template.json")
        val builder = ArmTemplateBuilder(template)

        if (details.vmPublicIp == true) {
            builder.setPublicIp()
        }

        builder.setParameterValue("vmName", instance.name)
                .addParameter(AzureConstants.IMAGE_ID, "string", "This is the identifier of custom image")
                .setParameterValue(AzureConstants.IMAGE_ID, details.imageId!!)
                .setCustomImage()
                .setParameterValue("networkId", details.networkId!!)
                .setParameterValue("subnetName", details.subnetId!!)
                .setParameterValue("adminUserName", details.username!!)
                .setParameterValue("adminPassword", details.password!!)
                .setParameterValue(AzureConstants.OS_TYPE, details.osType!!)
                .setParameterValue("vmSize", details.vmSize!!)
    }

    override fun getImageHashAsync(details: AzureCloudImageDetails) =
            CompletableDeferred(Integer.toHexString(details.imageId!!.hashCode())!!)
}
