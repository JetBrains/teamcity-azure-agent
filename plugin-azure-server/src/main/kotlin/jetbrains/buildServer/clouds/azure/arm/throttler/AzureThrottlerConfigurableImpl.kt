package jetbrains.buildServer.clouds.azure.arm.throttler

import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.credentials.ApplicationTokenCredentials
import com.microsoft.azure.credentials.AzureTokenCredentials
import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.resources.fluentcore.arm.implementation.AzureConfigurableImpl
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnectorImpl
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureProxyUtils.configureTeamCityProxy
import okhttp3.Interceptor
import java.io.File
import java.io.IOException

class AzureThrottlerConfigurableImpl : AzureConfigurableImpl<Azure.Configurable>(), AzureConfigurableWithNetworkInterceptors {
    override fun withNetworkInterceptor(interceptor: Interceptor): AzureConfigurableWithNetworkInterceptors {
        restClientBuilder.withNetworkInterceptor(interceptor)
        return this
    }

    override fun authenticate(credentials: AzureTokenCredentials): Azure.Authenticated {
        return if (credentials.defaultSubscriptionId() != null) Azure.authenticate(this.buildRestClient(credentials), credentials.domain(), credentials.defaultSubscriptionId()) else Azure.authenticate(this.buildRestClient(credentials), credentials.domain())
    }

    @Throws(IOException::class)
    override fun authenticate(credentialsFile: File): Azure.Authenticated {
        val credentials = ApplicationTokenCredentials.fromFile(credentialsFile)
        return Azure.authenticate(this.buildRestClient(credentials), credentials.domain(), credentials.defaultSubscriptionId())
    }

    /**
     * Configures http proxy settings.
     */
    override fun configureProxy(): AzureConfigurableWithNetworkInterceptors {
        configureTeamCityProxy()
        return this
    }

    companion object {
        private val LOG = Logger.getInstance(AzureApiConnectorImpl::class.java.name)
        private const val HTTP_PROXY_HOST = "http.proxyHost"
        private const val HTTP_PROXY_PORT = "http.proxyPort"
        private const val HTTPS_PROXY_HOST = "https.proxyHost"
        private const val HTTPS_PROXY_PORT = "https.proxyPort"
        private const val HTTP_PROXY_USER = "http.proxyUser"
        private const val HTTP_PROXY_PASSWORD = "http.proxyPassword"
    }
}
