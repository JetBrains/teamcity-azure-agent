package jetbrains.buildServer.clouds.azure.arm.throttler

class AzureTimeManagerFactoryImpl : AzureTimeManagerFactory {
    override fun create(): AzureTimeManager = AzureTimeManagerImpl()
}
