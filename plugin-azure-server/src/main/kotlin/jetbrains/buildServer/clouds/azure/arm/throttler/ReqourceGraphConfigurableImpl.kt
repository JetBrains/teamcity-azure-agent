package jetbrains.buildServer.clouds.azure.arm.throttler

import com.microsoft.azure.credentials.AzureTokenCredentials
import com.microsoft.azure.management.resources.fluentcore.arm.implementation.AzureConfigurableImpl
import jetbrains.buildServer.clouds.azure.arm.resourceGraph.ResourceGraph
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureProxyUtils.configureTeamCityProxy
import okhttp3.Interceptor

class ReqourceGraphConfigurableImpl : AzureConfigurableImpl<ResourceGraph.Configurable>(), ResourceGraphConfigurableWithNetworkInterceptors {
    override fun withNetworkInterceptor(interceptor: Interceptor): ResourceGraphConfigurableWithNetworkInterceptors {
        restClientBuilder.withNetworkInterceptor(interceptor)
        return this
    }

    override fun authenticate(credentials: AzureTokenCredentials): ResourceGraph.Authenticated = ResourceGraph.authenticate(this.buildRestClient(credentials))

    /**
     * Configures http proxy settings.
     */
    override fun configureProxy(): ResourceGraphConfigurableWithNetworkInterceptors {
        configureTeamCityProxy()
        return this
    }
}
