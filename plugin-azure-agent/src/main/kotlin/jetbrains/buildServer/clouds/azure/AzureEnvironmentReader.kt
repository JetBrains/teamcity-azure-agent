package jetbrains.buildServer.clouds.azure

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.BuildAgentConfigurationEx

/**
 * Process configuration settings from environment
 */
class AzureEnvironmentReader(private val configuration: BuildAgentConfigurationEx) {
    fun process(): Boolean {
        System.getenv(AzureProperties.INSTANCE_ENV_VAR)?.let {
            AzureCompress.decode(it).forEach { (key, value) ->
                configuration.addConfigurationParameter(key, value)
                LOG.info("Added configuration parameter: {$key, $value}")
            }
            configuration.addEnvironmentVariable(AzureProperties.INSTANCE_ENV_VAR, "")
            return true
        }

        return false
    }

    companion object {
        private val LOG = Logger.getInstance(AzureEnvironmentReader::class.java.name)
    }
}
