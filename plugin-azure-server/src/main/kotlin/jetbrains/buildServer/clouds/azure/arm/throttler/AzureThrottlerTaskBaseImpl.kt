package jetbrains.buildServer.clouds.azure.arm.throttler

abstract class AzureThrottlerTaskBaseImpl<A, P, T> : AzureThrottlerTask<A, P, T> {
    override fun areParametersEqual(parameter: P, other: P): Boolean {
        return parameter == other
    }
}
