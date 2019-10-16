package jetbrains.buildServer.clouds.azure.arm.throttler

import com.microsoft.azure.management.Azure
import okhttp3.Interceptor

interface AzureConfigurableWithNetworkInterceptors : Azure.Configurable {
    fun withNetworkInterceptor(interceptor: Interceptor): AzureConfigurableWithNetworkInterceptors
    fun configureProxy(): AzureConfigurableWithNetworkInterceptors
}
