package jetbrains.buildServer.clouds.azure.arm.connector

interface AzureApiConnectorFactory {
    fun create(parameters: Map<String, String>, profileId: String?): AzureApiConnector
}
