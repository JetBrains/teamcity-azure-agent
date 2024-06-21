package jetbrains.buildServer.clouds.azure.arm.throttler

import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.management.resources.fluentcore.arm.AzureConfigurable
import jetbrains.buildServer.clouds.azure.arm.connector.CredentialsAuthenticator
import jetbrains.buildServer.serverSide.TeamCityProperties
import java.net.InetSocketAddress
import java.net.Proxy

object AzureProxyUtils {
    fun <T : AzureConfigurable<T>> T.configureTeamCityProxy() : T {
        val builder = StringBuilder()

        // Set HTTP proxy
        val httpProxyHost = TeamCityProperties.getProperty(HTTP_PROXY_HOST)
        val httpProxyPort = TeamCityProperties.getInteger(HTTP_PROXY_PORT, 80)
        if (httpProxyHost.isNotBlank()) {
            this.withProxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(httpProxyHost, httpProxyPort)))
            builder.append("$httpProxyHost:$httpProxyPort")
        }

        // Set HTTPS proxy
        val httpsProxyHost = TeamCityProperties.getProperty(HTTPS_PROXY_HOST)
        val httpsProxyPort = TeamCityProperties.getInteger(HTTPS_PROXY_PORT, 443)
        if (httpsProxyHost.isNotBlank()) {
            this.withProxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(httpsProxyHost, httpsProxyPort)))
            builder.setLength(0)
            builder.append("$httpsProxyHost:$httpsProxyPort")
        }

        // Set proxy authentication
        val httpProxyUser = TeamCityProperties.getProperty(HTTP_PROXY_USER)
        val httpProxyPassword = TeamCityProperties.getProperty(HTTP_PROXY_PASSWORD)
        if (httpProxyUser.isNotBlank() && httpProxyPassword.isNotBlank()) {
            val authenticator = CredentialsAuthenticator(httpProxyUser, httpProxyPassword)
            this.withProxyAuthenticator(authenticator)
            builder.insert(0, "$httpProxyUser@")
        }

        if (builder.isNotEmpty()) {
            LOG.debug("Using proxy server $builder for connection")
        }

        return this
    }

    private val LOG = Logger.getInstance(AzureProxyUtils::class.java.name)

    private const val HTTP_PROXY_HOST = "http.proxyHost"
    private const val HTTP_PROXY_PORT = "http.proxyPort"
    private const val HTTPS_PROXY_HOST = "https.proxyHost"
    private const val HTTPS_PROXY_PORT = "https.proxyPort"
    private const val HTTP_PROXY_USER = "http.proxyUser"
    private const val HTTP_PROXY_PASSWORD = "http.proxyPassword"
}
