

package jetbrains.buildServer.clouds.azure.arm.types

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import jetbrains.buildServer.clouds.azure.arm.AzureCloudDeployTarget
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.throttler.ThrottlerExecutionTaskException
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
        if (envVars.lines().map { it.trim() }.filter { it.isNotEmpty() }.any { !AzureUtils.customEnvironmentVariableSyntaxIsValid(it) }) {
            errors.add(CheckedCloudException("Invalid custom environment variables"))
        }
    }
}

fun AzureCloudImageDetails.checkCustomTags(errors: MutableList<Throwable>) {
    customTags?.let { tags ->
        if (tags.lines().map { it.trim() }.filter { it.isNotEmpty() }.any { !AzureUtils.customTagSyntaxIsValid(it) }) {
            errors.add(CheckedCloudException("Invalid custom tags"))
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

            val templateParameters = try {
                AzureUtils.getTemplateParameters(root)
            } catch (e: Exception) {
                throw CheckedCloudException("Invalid JSON template", e)
            }

            val providedParameters = AzureUtils.getProvidedTemplateParameters(disableTemplateModification)
            val matchingErrors = AzureUtils
                .getMatchedTemplateParameters(providedParameters, templateParameters)
                .filter {!it.isMatched && (it.isProvided || !it.hasValue) }

            if (matchingErrors.any()) {
                val message = matchingErrors
                    .sortedBy { it.name }
                    .map {
                        when {
                            it.isProvided -> "Parameter '${it.name}' is required but not declared in template"
                            !it.hasValue -> "Parameter '${it.name}' is declared in template but cannot be resolved"
                            else -> "Problem with '${it.name}' parameter"
                        }
                    }
                    .joinToString(";${System::lineSeparator}")
                throw CheckedCloudException(message);
            }

            if (disableTemplateModification != true) {
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
            }
        } catch (e: Throwable) {
            AzureTemplateHandler.LOG.infoAndDebugDetails("Invalid template", e)
            exceptions.add(e)
        }
    }
}

suspend fun AzureCloudImageDetails.checkResourceGroup(connector: AzureApiConnector, errors: MutableList<Throwable>) {
    if (target == AzureCloudDeployTarget.SpecificGroup) {
        try {
            if (groupId == null) {
                errors.add(CheckedCloudException("Resource group name is empty"))
            } else if (!connector.getResourceGroups().containsKey(groupId)) {
                errors.add(CheckedCloudException("Resource group \"$groupId\" does not exist"))
            }
        } catch (e : ThrottlerExecutionTaskException) {
            errors.add(CheckedCloudException("Could not update resource groups. Please wait"))
        }
    }
}

suspend fun AzureCloudImageDetails.checkServiceExistence(serviceName: String, connector: AzureApiConnector, errors: MutableList<Throwable>) {
    try {
        val services = connector.getServices(region!!)
        if (!services.containsKey(serviceName)) {
            errors.add(CheckedCloudException(
                    "\"$serviceName\" resource provider is not available in $region region.\n" +
                            "Ensure that you have registered \"$serviceName\" in your subscription, and it is available in $region region:\n" +
                            "https://docs.microsoft.com/en-us/azure/azure-resource-manager/resource-manager-supported-services"
            ))
        }
    } catch (e : ThrottlerExecutionTaskException) {
        errors.add(CheckedCloudException("Could not update services existence. Please wait"))
    }
}
