package jetbrains.buildServer.clouds.azure

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.BuildAgentConfigurationEx

/**
 * Process configuration settings from environment
 */
class AzureEnvironmentReader(private val configuration: BuildAgentConfigurationEx) {
    fun process(): Boolean {
        val parameters = System.getenv().filter {
            it.key.startsWith(AzureProperties.ENV_VAR_PREFIX)
        }.toMap()

        parameters.forEach { (parameter, value) ->
            val key = parameter.removePrefix(AzureProperties.ENV_VAR_PREFIX)
            configuration.addConfigurationParameter(key, value)
            LOG.info("Added configuration parameter: {$key, $value}")
        }

        return parameters.isNotEmpty()
    }

    companion object {
        private val LOG = Logger.getInstance(AzureEnvironmentReader::class.java.name)
    }
}
