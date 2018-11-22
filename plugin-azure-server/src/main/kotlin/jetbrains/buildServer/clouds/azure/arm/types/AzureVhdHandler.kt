package jetbrains.buildServer.clouds.azure.arm.types

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.*
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import kotlinx.coroutines.coroutineScope
import java.util.*

class AzureVhdHandler(private val connector: AzureApiConnector) : AzureHandler {
    @Suppress("UselessCallOnNotNull")
    override suspend fun checkImage(image: AzureCloudImage) = coroutineScope {
        val exceptions = ArrayList<Throwable>()
        val details = image.imageDetails

        details.checkSourceId(exceptions)
        details.checkRegion(exceptions)
        details.checkOsType(exceptions)
        details.checkNetworkId(exceptions)
        details.checkResourceGroup(connector, exceptions)
        details.checkServiceExistence("Microsoft.Compute", connector, exceptions)

        val imageUrl = details.imageUrl
        if (imageUrl == null || imageUrl.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Image URL is empty"))
        } else {
            try {
                val region = details.region!!
                connector.getVhdOsType(imageUrl, region)
            } catch (e: Throwable) {
                LOG.infoAndDebugDetails("Failed to get os type for vhd $imageUrl", e)
                exceptions.add(e)
            }
        }

        exceptions
    }

    override suspend fun prepareBuilder(instance: AzureCloudInstance) = coroutineScope {
        val details = instance.image.imageDetails
        val template = AzureUtils.getResourceAsString("/templates/vm-template.json")
        val builder = ArmTemplateBuilder(template)

        if (details.vmPublicIp == true) {
            builder.setPublicIp()
        }

        connector.deleteVmBlobs(instance)

        builder.setParameterValue("vmName", instance.name)
                .addParameter(AzureConstants.IMAGE_URL, "string", "This is the name of the generalized VHD image")
                .setParameterValue(AzureConstants.IMAGE_URL, details.imageUrl!!.trim())
                .setVhdImage()
                .setParameterValue("networkId", details.networkId!!)
                .setParameterValue("subnetName", details.subnetId!!)
                .setParameterValue("adminUserName", details.username!!)
                .setParameterValue("adminPassword", details.password!!)
                .setParameterValue(AzureConstants.OS_TYPE, details.osType!!)
                .setParameterValue("vmSize", details.vmSize!!)
    }

    override suspend fun getImageHash(details: AzureCloudImageDetails) = coroutineScope {
        val imageUrl = details.imageUrl!!
        val region = details.region!!
        val metadata = connector.getVhdMetadata(imageUrl, region) ?: emptyMap()
        metadata[METADATA_ETAG] ?: ""
    }

    companion object {
        private val LOG = Logger.getInstance(AzureVhdHandler::class.java.name)
        private const val METADATA_ETAG = "etag"
    }
}
