package jetbrains.buildServer.clouds.azure

import jetbrains.buildServer.agent.BuildAgentConfigurationEx

/**
 * Process configuration settings from environment
 */
class AzureEnvironmentReader(private val configuration: BuildAgentConfigurationEx) {
    fun process(): Boolean {
        val parameters = System.getenv().filter { it.key.startsWith(AzurePropertiesNames.TEAMCITY_ACI_PREFIX) }.toMap()
        parameters.forEach {
            configuration.addConfigurationParameter(it.key.removePrefix(AzurePropertiesNames.TEAMCITY_ACI_PREFIX), it.value)
        }

        return parameters.isNotEmpty()
    }
}
