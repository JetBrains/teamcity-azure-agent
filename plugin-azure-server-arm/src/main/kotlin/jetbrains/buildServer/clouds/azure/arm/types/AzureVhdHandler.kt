package jetbrains.buildServer.clouds.azure.arm.types

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.*
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import java.util.*

class AzureVhdHandler(private val connector: AzureApiConnector) : AzureHandler {
    private val LOG = Logger.getInstance(AzureVhdHandler::class.java.name)

    private val METADATA_CONTENT_MD5 = "contentMD5"
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
        if (details.networkId.isNullOrEmpty() || details.subnetId.isEmpty()) {
            exceptions.add(CheckedCloudException("Invalid network settings"))
        }
        if (details.networkId.isNullOrEmpty() || details.subnetId.isEmpty()) {
            exceptions.add(CheckedCloudException("Invalid network settings"))
        }
        if (details.osType.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Invalid OS Type value"))
        }

        val imageUrl = details.imageUrl
        if (imageUrl == null || imageUrl.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Image URL is empty"))
        } else {
            try {
                val region = details.region!!
                connector.getVhdOsTypeAsync(imageUrl, region).await()
            } catch (e: Throwable) {
                LOG.infoAndDebugDetails("Failed to get os type for vhd " + imageUrl, e)
                exceptions.add(e)
            }
        }

        exceptions
    }

    override fun prepareBuilderAsync(instance: AzureCloudInstance) = async(CommonPool) {
        val details = instance.image.imageDetails
        val template = AzureUtils.getResourceAsString("/templates/vm-template.json")
        val builder = ArmTemplateBuilder(template)

        if (details.vmPublicIp) {
            builder.setPublicIp()
        }

        connector.deleteVmBlobsAsync(instance).await()

        builder.setParameterValue("vmName", instance.name)
                .addParameter(AzureConstants.IMAGE_URL, "string", "This is the name of the generalized VHD image")
                .setParameterValue(AzureConstants.IMAGE_URL, details.imageUrl!!)
                .setVhdImage()
                .setParameterValue("networkId", details.networkId)
                .setParameterValue("subnetName", details.subnetId)
                .setParameterValue("adminUserName", details.username)
                .setParameterValue("adminPassword", details.password!!)
                .setParameterValue(AzureConstants.OS_TYPE, details.osType)
                .setParameterValue("vmSize", details.vmSize)
    }

    override fun getImageHashAsync(details: AzureCloudImageDetails) = async(CommonPool) {
        val imageUrl = details.imageUrl!!
        val region = details.region!!
        val metadata = connector.getVhdMetadataAsync(imageUrl, region).await() ?: emptyMap()
        metadata[METADATA_CONTENT_MD5] ?: ""
    }
}
