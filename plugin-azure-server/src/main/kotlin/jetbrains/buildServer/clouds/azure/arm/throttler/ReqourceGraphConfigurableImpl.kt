package jetbrains.buildServer.clouds.azure.arm.throttler

import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.credentials.ApplicationTokenCredentials
import com.microsoft.azure.credentials.AzureTokenCredentials
import com.microsoft.azure.management.resources.fluentcore.arm.implementation.AzureConfigurableImpl
import jetbrains.buildServer.clouds.azure.arm.connector.CredentialsAuthenticator
import jetbrains.buildServer.clouds.azure.arm.resourceGraph.ResourceGraph
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureProxyUtils.configureTeamCityProxy
import jetbrains.buildServer.serverSide.TeamCityProperties
import okhttp3.Interceptor
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

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
