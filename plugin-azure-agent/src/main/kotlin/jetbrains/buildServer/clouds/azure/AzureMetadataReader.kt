package jetbrains.buildServer.clouds.azure

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.BuildAgentConfigurationEx
import jetbrains.buildServer.clouds.CloudInstanceUserData

class AzureMetadataReader(
        private val configuration: BuildAgentConfigurationEx,
        private val spotTerminationChecker: SpotInstanceTerminationChecker
) {
    private lateinit var myAzureUserData: AzureUserData

    fun process() : MetadataReaderResult {
        val metadata = try {
            AzureMetadata.readInstanceMetadata()
        } catch (e: Throwable) {
            LOG.info("Azure instance metadata is not available: " + e.message)
            LOG.debug(e)
            return MetadataReaderResult.SKIP
        }

        val result = updateConfiguration(metadata)
        if (result != MetadataReaderResult.SKIP) {
            runSpotChecker(metadata)
        }
        return result
    }

    private fun runSpotChecker(metadata: AzureMetadata.Metadata) {
        metadata.compute?.name?.let {
            spotTerminationChecker.start(it)
        }
    }

    internal fun updateConfiguration(metadata: AzureMetadata.Metadata): MetadataReaderResult {
        metadata.compute?.let {
            if (it.name?.isNotBlank() == true && configuration.name.isBlank()) {
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

        val userData = metadata.compute?.userData
        if (userData.isNullOrBlank()) {
            LOG.info("No Azure userData provided")
            return MetadataReaderResult.SKIP
        } else {
            LOG.info("Processing Azure userData from IMDS")
        }

        val azureUserData = try {
            AzureUserData.deserialize(userData)
        } catch (throwable: Throwable) {
            LOG.warnAndDebugDetails("Could not parse Azure userData.", throwable)
            return MetadataReaderResult.SKIP
        }

        if (azureUserData.pluginCode != AzureUserData.PLUGIN_CODE) {
            LOG.warn("Unsupported plugin code (${azureUserData.pluginCode}) in Azure userData")
            return MetadataReaderResult.SKIP
        }

        myAzureUserData = azureUserData
        return MetadataReaderResult.NEED_POST_PROCESS
    }

    fun postProcess() {
        val data = CloudInstanceUserData.deserialize(myAzureUserData.cloudInstanceUserData)
        if (data == null) {
            LOG.info("Unable to deserialize userData.cloudInstanceUserData value: '${myAzureUserData.cloudInstanceUserData}'")
            return
        }

        data.customAgentConfigurationParameters[STARTING_INSTANCE_ID]?.let {
            configuration.addConfigurationParameter(STARTING_INSTANCE_ID, it)
            LOG.info("Updated configuration parameter: $STARTING_INSTANCE_ID")
        }
    }

    companion object {
        private const val STARTING_INSTANCE_ID = "teamcity.agent.startingInstanceId"
        private val LOG = Logger.getInstance(AzureMetadataReader::class.java.name)
    }
}

enum class MetadataReaderResult {
    SKIP,
    PROCESSED,
    NEED_POST_PROCESS
}
