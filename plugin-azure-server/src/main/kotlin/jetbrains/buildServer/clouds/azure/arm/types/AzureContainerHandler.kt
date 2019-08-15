package jetbrains.buildServer.clouds.azure.arm.types

import jetbrains.buildServer.clouds.azure.arm.*
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import kotlinx.coroutines.coroutineScope
import java.util.*

class AzureContainerHandler(private val connector: AzureApiConnector) : AzureHandler {
    override suspend fun checkImage(image: AzureCloudImage) = coroutineScope {
        val exceptions = ArrayList<Throwable>()
        val details = image.imageDetails

        details.checkSourceId(exceptions)
        details.checkRegion(exceptions)
        details.checkOsType(exceptions)
        details.checkImageId(exceptions)
        details.checkResourceGroup(connector, exceptions)
        details.checkDns(exceptions)
        details.checkServiceExistence("Microsoft.ContainerInstance", connector, exceptions)

        exceptions
    }

    override suspend fun prepareBuilder(instance: AzureCloudInstance) = coroutineScope {
        val details = instance.image.imageDetails
        val template = AzureUtils.getResourceAsString("/templates/container-template.json")
        val builder = ArmTemplateBuilder(template)

        builder.setParameterValue("containerName", instance.name)
                .setParameterValue(AzureConstants.IMAGE_ID, details.imageId!!.trim())
                .setParameterValue(AzureConstants.OS_TYPE, details.osType!!)
                .setParameterValue(AzureConstants.NUMBER_CORES, details.numberCores!!)
                .setParameterValue(AzureConstants.MEMORY, details.memory!!)
                .setCustomDns(details.useCustomDns)
                .addContainer(instance.name)
                .apply {
                    if (!details.registryUsername.isNullOrEmpty() && !details.password.isNullOrEmpty()) {
                        val server = getImageServer(details.imageId)
                        addContainerCredentials(server, details.registryUsername.trim(), details.password!!.trim())
                    }
                }
    }

    override suspend fun getImageHash(details: AzureCloudImageDetails) = coroutineScope {
        Integer.toHexString(details.imageId!!.hashCode())!!
    }

    private fun getImageServer(imageId: String): String {
        return hostMatcher.find(imageId)?.let {
            val (server) = it.destructured
            server
        } ?: imageId
    }

    companion object {
        private val hostMatcher = Regex("^(?:https?:\\/\\/)?([^\\/]+)")
    }
}
