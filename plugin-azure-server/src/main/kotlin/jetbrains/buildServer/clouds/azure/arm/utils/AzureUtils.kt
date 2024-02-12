

package jetbrains.buildServer.clouds.azure.arm.utils

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.util.io.StreamUtil
import com.microsoft.aad.adal4j.AuthenticationException
import jetbrains.buildServer.clouds.azure.arm.AzureCloudDeployTarget
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageType
import jetbrains.buildServer.util.ExceptionUtil
import jetbrains.buildServer.util.StringUtil
import org.apache.commons.codec.binary.Base64
import org.springframework.util.StringUtils
import java.io.IOException

/**
 * Utilities.
 */
object AzureUtils {
    private val INVALID_TENANT = Regex("AADSTS90002: No service namespace named '([\\w-]+)' was found in the data store\\.")
    private val ENVIRONMENT_VARIABLE_REGEX = Regex("^([a-z_][a-z0-9_]*?)=.*?\$", RegexOption.IGNORE_CASE)
    private val CUSTOM_TAG_REGEX = Regex("^([^<>%&\\\\?/]*?)=.*?\$", RegexOption.IGNORE_CASE)
    private val SIMPLE_TEMPLATE_PARAMETERS = listOf(
        TemplateParameterDescriptor("vmName", "string", true)
    )
    private val FULL_TEMPLATE_PARAMETERS = listOf(
        TemplateParameterDescriptor("vmName", "string", true),
        TemplateParameterDescriptor("customData", "string", true),
        TemplateParameterDescriptor("teamcity-profile", "string", true),
        TemplateParameterDescriptor("teamcity-image-hash", "string", true),
        TemplateParameterDescriptor("teamcity-data-hash", "string", true),
        TemplateParameterDescriptor("teamcity-server", "string", true),
        TemplateParameterDescriptor("teamcity-source", "string", true),
    )
    private val FEATURES_TAG_VERSION = 1

    internal val mapper = ObjectMapper()

    fun getResourceAsString(name: String): String {
        val stream = AzureUtils::class.java.getResourceAsStream(name) ?: return ""

        return try {
            StreamUtil.readText(stream)
        } catch (e: IOException) {
            ""
        }
    }

    fun customEnvironmentVariableSyntaxIsValid(envVar: String): Boolean {
        return envVar.matches(ENVIRONMENT_VARIABLE_REGEX)
    }

    fun customTagSyntaxIsValid(tag: String): Boolean {
        return tag.matches(CUSTOM_TAG_REGEX)
    }

    fun getExceptionDetails(e: com.microsoft.azure.CloudException): String {
        e.body()?.let { error ->
            return error.message() + " " + error.details().joinToString("\n", transform = {
                deserializeAzureError(it.message()) + " (${it.code()})"
            })
        }
        return e.message ?: ""
    }

    internal fun deserializeAzureError(json: String) = try {
        mapper.readValue(json, CloudErrorDetails::class.java)?.error?.let {
            "${it.message} (${it.code})"
        } ?: json
    } catch (e: JsonProcessingException) {
        json
    }

    fun getAuthenticationErrorMessage(e: Throwable): String {
        val authException = ExceptionUtil.getCause(e, AuthenticationException::class.java)
        return if (authException != null) {
            deserializeAuthError(authException.message ?: "")
        } else {
            e.message ?: ""
        }
    }

    internal fun deserializeAuthError(json: String) = try {
        mapper.readValue(json, CloudAuthError::class.java)?.error_description?.let {
            val error = StringUtil.splitByLines(it).firstOrNull() ?: it
            INVALID_TENANT.matchEntire(error)?.let { matchResult ->
                val (id) = matchResult.destructured
                "Application with identifier '$id' was not found in specified tenant or environment."
            } ?: error
        } ?: json
    } catch (e: JsonProcessingException) {
        json
    }

    internal fun getTemplateParameters(templateJson: String) : List<TemplateParameterDescriptor> {
        if (StringUtils.isEmpty(templateJson)) {
            throw TemplateParameterException("Template is empty")
        }

        try {
            val reader = mapper.reader()
            val template = reader.readTree(templateJson)

            return getTemplateParameters(template)
        }
        catch(e: TemplateParameterException) {
            throw e
        }
        catch(e: Exception) {
            throw TemplateParameterException("Incorrect template format", e)
        }
    }

    internal fun getTemplateParameters(template: JsonNode) : List<TemplateParameterDescriptor> {
        val parameters = template["parameters"]
        if (parameters == null) {
            throw TemplateParameterException("Incorrect template format. No 'paramaters' section")
        }
        try {
            return parameters
                .fields()
                .asSequence()
                .map { (fieldName, value) ->
                    val type = value.get("type").asText()
                    val hasValue = value.has("defaultValue")
                    TemplateParameterDescriptor(fieldName, type, hasValue)
                }
                .toList()
        }
        catch(e: TemplateParameterException) {
            throw e
        }
        catch(e: Exception) {
            throw TemplateParameterException("Incorrect template format", e)
        }
    }

    internal fun getProvidedTemplateParameters(disableTemplateModification: Boolean?): List<TemplateParameterDescriptor> =
        if (disableTemplateModification == true) FULL_TEMPLATE_PARAMETERS else SIMPLE_TEMPLATE_PARAMETERS

    internal fun getMatchedTemplateParameters(
        providedParameters: List<TemplateParameterDescriptor>,
        templateParameters: List<TemplateParameterDescriptor>
    ): List<MatchedTemplateParameterDescriptior> {
        val parametersMap = providedParameters
            .map { it.name to MatchedTemplateParameterDescriptior(it, true, false) }
            .toMap()
            .toMutableMap()

        templateParameters.forEach {
            val parameter = parametersMap[it.name]
            parametersMap[it.name] =  if (parameter != null) {
                MatchedTemplateParameterDescriptior(parameter, parameter.isProvided, true)
            } else {
                MatchedTemplateParameterDescriptior(it, false, false)
            }
        }
        return parametersMap.values.toList()
    }

    internal fun getDeploymentFeaturesTagValue(isVM: Boolean, isSafeRemoval: Boolean): String {
        try {
            val inner = DeploymentFeaturesDescriptorInner()
                .also {
                    it.isVM = isVM
                    it.isSafeRemoval = isSafeRemoval
                }
            val data = Base64.encodeBase64String(mapper.writeValueAsString(inner).toByteArray(Charsets.UTF_8))
            return "${FEATURES_TAG_VERSION}-${data}"
        } catch (e: Throwable) {
            throw DeploymentFeaturesDescriptorError("Could not serialize features", e)
        }
    }

    internal fun getDeploymentFeatures(tagValue: String): DeploymentFeaturesDescriptor {
        val versionDelimiterIndex = tagValue.indexOf('-')
        if (versionDelimiterIndex <= 0) {
            throw DeploymentFeaturesDescriptorError("Incorrect format")
        }
        val version = try {
            tagValue.substring(0, versionDelimiterIndex).toInt()
        } catch(e: NumberFormatException) {
            throw DeploymentFeaturesDescriptorError("Incorrect version", e)
        }
        if (version != 1) {
            throw DeploymentFeaturesDescriptorError("Version ${version} is not supported")
        }
        val data = tagValue.substring(versionDelimiterIndex + 1)
        val descriptor = try {
            mapper.readValue(Base64.decodeBase64(data), DeploymentFeaturesDescriptorInner::class.java)
        } catch (e: Throwable) {
            throw DeploymentFeaturesDescriptorError("Could not parse data.", e)
        }
        return DeploymentFeaturesDescriptor(
            version = version,
            isVM = descriptor.isVM,
            isSafeRemoval = descriptor.isSafeRemoval
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CloudErrorDetails(val error: CloudError? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CloudError(val code: String? = null,
                              val message: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CloudAuthError(val error: String? = null,
                                  val error_description: String? = null)

fun AzureCloudImageDetails.isVmInstance(): Boolean {
    return deployTarget == AzureCloudDeployTarget.Instance || type != AzureCloudImageType.Container
}

internal open class TemplateParameterDescriptor(
    val name: String,
    val type: String,
    val hasValue: Boolean
)

internal class MatchedTemplateParameterDescriptior(
    value: TemplateParameterDescriptor,
    val isProvided: Boolean,
    val isMatched: Boolean
) : TemplateParameterDescriptor(value.name, value.type, value.hasValue)

class TemplateParameterException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, rootCause: Throwable) : super(message, rootCause)
}

internal data class DeploymentFeaturesDescriptor(
    val version: Int,
    val isVM: Boolean,
    val isSafeRemoval: Boolean
)

internal class DeploymentFeaturesDescriptorInner {
    var isVM = false
    var isSafeRemoval = false
}

class DeploymentFeaturesDescriptorError(message: String, cause: Throwable? = null): Exception(message, cause)
