package jetbrains.buildServer.clouds.azure.arm.connector

import jetbrains.buildServer.clouds.azure.arm.throttler.AzureRequestThrottlerCache
import jetbrains.buildServer.serverSide.ServerSettings

class AzureApiConnectorFactoryImpl(
        private val myAzureRequestThrottlerCache: AzureRequestThrottlerCache,
        private val mySettings: ServerSettings,
        private val parametersProvider: AzureProjectParametersProvider,
) : AzureApiConnectorFactory {
    override fun create(parameters: Map<String, String>, profileId: String?): AzureApiConnector {
        return AzureApiConnectorImpl(parameters, myAzureRequestThrottlerCache, profileId, parametersProvider, { mySettings.serverUUID!! })
    }
}
