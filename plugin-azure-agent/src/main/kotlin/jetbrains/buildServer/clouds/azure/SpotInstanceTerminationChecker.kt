package jetbrains.buildServer.clouds.azure

interface SpotInstanceTerminationChecker {
    fun start(resourceName: String)
}
