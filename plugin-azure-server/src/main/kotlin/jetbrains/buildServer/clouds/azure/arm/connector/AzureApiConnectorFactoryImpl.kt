

package jetbrains.buildServer.clouds.azure.arm.connector

import jetbrains.buildServer.clouds.azure.arm.throttler.AzureRequestThrottlerCache
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerSchedulersProvider
import jetbrains.buildServer.serverSide.ServerSettings

class AzureApiConnectorFactoryImpl(
        private val myAzureRequestThrottlerCache: AzureRequestThrottlerCache,
        private val mySettings: ServerSettings
) : AzureApiConnectorFactory {
    override fun create(parameters: Map<String, String>, profileId: String?): AzureApiConnector {
        return AzureApiConnectorImpl(parameters, myAzureRequestThrottlerCache, { mySettings.serverUUID!! })
                .also { connector -> profileId?.let { connector.setProfileId(it) } }
    }
}
