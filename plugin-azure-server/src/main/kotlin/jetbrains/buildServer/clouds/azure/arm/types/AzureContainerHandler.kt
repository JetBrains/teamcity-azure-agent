package jetbrains.buildServer.clouds.azure.arm.types

import jetbrains.buildServer.clouds.azure.arm.AzureCloudImage
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails
import jetbrains.buildServer.clouds.azure.arm.AzureCloudInstance
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
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
        details.checkCustomEnvironmentVariables(exceptions)
        details.checkCustomTags(exceptions)
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
                .addContainer(instance.name, parseEnvironmentVariables(details.customEnvironmentVariables))
                .apply {
                    if (!details.registryUsername.isNullOrEmpty() && !details.password.isNullOrEmpty()) {
                        val server = getImageServer(details.imageId)
                        addContainerCredentials(server, details.registryUsername.trim(), details.password!!.trim())
                    }
                    if (!details.networkId.isNullOrEmpty() && !details.subnetId.isNullOrEmpty()) {
                        setParameterValue("networkId", details.networkId)
                        setParameterValue("subnetName", details.subnetId)
                        addContainerNetwork()
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

        fun parseEnvironmentVariables(rawString: String?): List<Pair<String, String>> {
            return if (rawString != null && rawString.isNotEmpty()) {
                rawString.lines().map { it.trim() }.filter { it.isNotEmpty() }.filter(AzureUtils::customEnvironmentVariableSyntaxIsValid).mapNotNull {
                    val envVar = it
                    val equalsSignIndex = envVar.indexOf("=")
                    if (equalsSignIndex > 1) {
                        Pair(envVar.substring(0, equalsSignIndex), envVar.substring(equalsSignIndex + 1))
                    } else {
                        null
                    }
                }
            } else {
                Collections.emptyList()
            }
        }
    }
}
