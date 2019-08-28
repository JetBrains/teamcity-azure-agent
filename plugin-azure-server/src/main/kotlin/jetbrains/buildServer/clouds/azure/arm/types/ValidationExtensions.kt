package jetbrains.buildServer.clouds.azure.arm.types

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import jetbrains.buildServer.clouds.azure.arm.AzureCloudDeployTarget
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import java.util.*

@Suppress("UselessCallOnNotNull")
fun AzureCloudImageDetails.checkSourceId(errors: MutableList<Throwable>) {
    if (sourceId.isNullOrEmpty()) {
        errors.add(CheckedCloudException("Invalid source id"))
    }
}

fun AzureCloudImageDetails.checkRegion(errors: MutableList<Throwable>) {
    if (region.isNullOrEmpty()) {
        errors.add(CheckedCloudException("Invalid region"))
    }
}

fun AzureCloudImageDetails.checkOsType(errors: MutableList<Throwable>) {
    if (osType.isNullOrEmpty()) {
        errors.add(CheckedCloudException("Invalid OS Type value"))
    }
}

fun AzureCloudImageDetails.checkImageId(errors: MutableList<Throwable>) {
    if (imageId.isNullOrEmpty()) {
        errors.add(CheckedCloudException("Docker image is empty"))
    }
}

fun AzureCloudImageDetails.checkNetworkId(errors: MutableList<Throwable>) {
    if (networkId.isNullOrEmpty() || subnetId.isNullOrEmpty()) {
        errors.add(CheckedCloudException("Invalid network settings"))
    }
}

fun AzureCloudImageDetails.checkCustomEnvironmentVariables(errors: MutableList<Throwable>) {
    customEnvironmentVariables?.let { envVars ->
        if (envVars.lines().any { !AzureUtils.customEnvironmentVariableSyntaxIsValid(it) }) {
            errors.add(CheckedCloudException("Invalid custom environment variables"))
        }
    }
}

fun AzureCloudImageDetails.checkTemplate(exceptions: ArrayList<Throwable>) {
    if (template == null || template.isNullOrEmpty()) {
        exceptions.add(CheckedCloudException("Template is empty"))
    } else {
        try {
            val root = try {
                val reader = AzureUtils.mapper.reader()
                reader.readTree(template) as ObjectNode
            } catch (e: Exception) {
                throw CheckedCloudException("Invalid JSON template", e)
            }

            try {
                root["parameters"]["vmName"] as ObjectNode
            } catch (e: Exception) {
                throw CheckedCloudException("No 'vmName' parameter", e)
            }

            try {
                val resources = root["resources"] as ArrayNode
                resources.filterIsInstance<ObjectNode>()
                        .first {
                            it["type"].asText() == "Microsoft.Compute/virtualMachines" &&
                                    it["name"].asText() == "[parameters('vmName')]"
                        }
            } catch (e: Exception) {
                throw CheckedCloudException("No virtual machine resource with name set to 'vmName' parameter", e)
            }
        } catch (e: Throwable) {
            AzureTemplateHandler.LOG.infoAndDebugDetails("Invalid template", e)
            exceptions.add(e)
        }
    }
}

suspend fun AzureCloudImageDetails.checkResourceGroup(connector: AzureApiConnector, errors: MutableList<Throwable>) {
    if (target == AzureCloudDeployTarget.SpecificGroup) {
        if (groupId == null) {
            errors.add(CheckedCloudException("Resource group name is empty"))
        } else if (!connector.getResourceGroups().containsKey(groupId)) {
            errors.add(CheckedCloudException("Resource group \"$groupId\" does not exist"))
        }
    }
}

suspend fun AzureCloudImageDetails.checkServiceExistence(serviceName: String, connector: AzureApiConnector, errors: MutableList<Throwable>) {
    val services = connector.getServices(region!!)
    if (!services.containsKey(serviceName)) {
        errors.add(CheckedCloudException(
                "\"$serviceName\" resource provider is not available in $region region.\n" +
                "Ensure that you have registered \"$serviceName\" in your subscription, and it is available in $region region:\n" +
                "https://docs.microsoft.com/en-us/azure/azure-resource-manager/resource-manager-supported-services"
        ))
    }
}
