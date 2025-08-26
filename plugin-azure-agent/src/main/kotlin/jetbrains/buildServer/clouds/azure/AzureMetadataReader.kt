package jetbrains.buildServer.clouds.azure

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.BuildAgentConfigurationEx

class AzureMetadataReader {
    fun process() : Result<AzureMetadataReaderData> = try {
        Result.success(AzureMetadataReaderData(AzureMetadata.readInstanceMetadata()))
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

class AzureMetadataReaderData(
    val metadata: AzureMetadata.Metadata
) {
    var hasMarkerTags: Boolean = false
        private set

    var hasNewIntegrationMarkerTags: Boolean = false
        private set

    init {
        metadata.compute?.tagsList?.let { tagsList ->
            LOG.debug {
                val teamcityTags = tagsList.filter { it.name.startsWith("teamcity", true) }
                "Azure plugin detected the following TeamCity tags for the VM: {${teamcityTags.joinToString(",") { "\"${it.name}\":\"${it.value}\"" }}}"
            }
            hasMarkerTags = tagsList.any { it.name.equals("teamcity-data-hash", true) }
            hasNewIntegrationMarkerTags = tagsList.any { it.name.equals("teamcity-instance-id", true) }
        }
    }

    fun getUserData() : Result<AzureUserData> {
        val userData = metadata.compute?.userData
        if (userData.isNullOrBlank()) {
            return Result.failure(UserDataIsEmptyException("No Azure userData provided"))
        } else {
            LOG.info("Processing Azure userData from IMDS")
        }

        val azureUserData = try {
            AzureUserData.deserialize(userData)
        } catch (throwable: Throwable) {
            return Result.failure(UserDataDeserializationException("Could not parse Azure userData.", throwable))
        }

        if (azureUserData.pluginCode != AzureUserData.PLUGIN_CODE) {
            return Result.failure(UserDataIncorrectCodeException("Unsupported plugin code (${azureUserData.pluginCode}) in Azure userData. Current plugin code is ${AzureUserData.PLUGIN_CODE}."))
        }

        return Result.success(azureUserData)
    }

    fun updateConfiguration(configuration: BuildAgentConfigurationEx, updateName: Boolean = true) {
        metadata.compute?.let {
            if (updateName && it.name?.isNotBlank() == true && configuration.name.isBlank()) {
                LOG.info("Setting name from instance metadata: ${it.name}")
                configuration.name = it.name
            }

            LOG.info("Setting properties from instance metadata")
            for ((key, value) in it.asMap())
            {
                if (value != null)
                    configuration.addSystemProperty(AzureProperties.INSTANCE_PREFIX + key, value.toString())
            }
        }

        metadata.network?.interfaces?.firstOrNull()?.ipv4?.ipAddress?.firstOrNull()?.publicIpAddress?.let {
            if (it.isNotBlank()) {
                LOG.info("Setting external IP address from instance metadata: $it")
                configuration.addAlternativeAgentAddress(it)
                configuration.addSystemProperty("ec2.public-hostname", it)
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(AzureMetadataReaderData::class.java.name)
    }
}

open class UserDataException(message: String, throwable: Throwable? = null) : Exception(message, throwable)
open class UserDataIsEmptyException(message: String) : UserDataException(message)
open class UserDataDeserializationException(message: String, throwable: Throwable) : UserDataException(message, throwable)
open class UserDataIncorrectCodeException(message: String) : UserDataException(message)

