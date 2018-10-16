package jetbrains.buildServer.clouds.azure.arm.types

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.*
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import kotlinx.coroutines.experimental.coroutineScope
import java.util.*

class AzureImageHandler(private val connector: AzureApiConnector) : AzureHandler {
    @Suppress("UselessCallOnNotNull")
    override suspend fun checkImage(image: AzureCloudImage) = coroutineScope {
        val exceptions = ArrayList<Throwable>()
        val details = image.imageDetails

        details.checkSourceId(exceptions)
        details.checkRegion(exceptions)
        details.checkOsType(exceptions)
        details.checkNetworkId(exceptions)
        details.checkResourceGroup(connector, exceptions)

        val imageId = details.imageId
        if (imageId == null || imageId.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Image ID is empty"))
        } else {
            try {
                connector.getImageName(imageId)
            } catch (e: Throwable) {
                LOG.infoAndDebugDetails("Failed to get image ID $imageId", e)
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

        builder.setParameterValue("vmName", instance.name)
                .addParameter(AzureConstants.IMAGE_ID, "string", "This is the identifier of custom image")
                .setParameterValue(AzureConstants.IMAGE_ID, details.imageId!!)
                .setCustomImage()
                .setParameterValue("networkId", details.networkId!!)
                .setParameterValue("subnetName", details.subnetId!!)
                .setParameterValue("adminUserName", details.username!!)
                .setParameterValue("adminPassword", details.password!!)
                .setParameterValue(AzureConstants.OS_TYPE, details.osType!!)
                .setStorageAccountType(details.storageAccountType)
                .setParameterValue("vmSize", details.vmSize!!)
    }

    override suspend fun getImageHash(details: AzureCloudImageDetails) = coroutineScope {
        Integer.toHexString(details.imageId!!.hashCode())!!
    }

    companion object {
        private val LOG = Logger.getInstance(AzureImageHandler::class.java.name)
    }
}
