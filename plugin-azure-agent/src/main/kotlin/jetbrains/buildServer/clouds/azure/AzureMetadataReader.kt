

package jetbrains.buildServer.clouds.azure

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.BuildAgentConfigurationEx

class AzureMetadataReader(
        private val configuration: BuildAgentConfigurationEx,
        private val spotTerminationChecker: SpotInstanceTerminationChecker
) {
    fun process() {
        val metadata = try {
            AzureMetadata.readInstanceMetadata()
        } catch (e: Throwable) {
            LOG.info("Azure instance metadata is not available: " + e.message)
            LOG.debug(e)
            return
        }

        updateConfiguration(metadata)

        runSpotChecker(metadata);
    }

    private fun runSpotChecker(metadata: AzureMetadata.Metadata) {
        metadata.compute?.name?.let {
            spotTerminationChecker.start(it)
        }
    }

    internal fun updateConfiguration(metadata: AzureMetadata.Metadata) {
        metadata.compute?.name?.let {
            if (it.isNotBlank() && configuration.name.isBlank()) {
                LOG.info("Setting name from instance metadata: $it")
                configuration.name = it
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
        private val LOG = Logger.getInstance(AzureMetadataReader::class.java.name)
    }
}
