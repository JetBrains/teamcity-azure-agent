package jetbrains.buildServer.clouds.azure.arm.throttler

import jetbrains.buildServer.clouds.azure.arm.resourceGraph.ResourceGraph
import okhttp3.Interceptor

interface ResourceGraphConfigurableWithNetworkInterceptors : ResourceGraph.Configurable {
    fun withNetworkInterceptor(interceptor: Interceptor): ResourceGraphConfigurableWithNetworkInterceptors
    fun configureProxy(): ResourceGraphConfigurableWithNetworkInterceptors
}
