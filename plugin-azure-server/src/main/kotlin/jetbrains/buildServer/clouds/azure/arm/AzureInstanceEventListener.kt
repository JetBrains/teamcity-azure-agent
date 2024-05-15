package jetbrains.buildServer.clouds.azure.arm

interface AzureInstanceEventListener {
    fun instanceTerminated(instance: AzureCloudInstance)
    fun instanceFailedToCreate(instance: AzureCloudInstance, throwable: Throwable)
}
