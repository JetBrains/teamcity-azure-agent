/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.azure

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.BuildAgentConfigurationEx
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils

class AzureMetadataReader(private val configuration: BuildAgentConfigurationEx) {

    fun process() {
        createHttpClient().use {
            val response = try {
                it.execute(HttpGet(METADATA_URL).apply {
                    addHeader("Metadata", "true")
                })
            } catch (e: Exception) {
                LOG.infoAndDebugDetails("Azure instance metadata is not available: Failed to connect to $METADATA_URL: ${e.message}", e)
                return
            }

            val statusCode = response.statusLine.statusCode
            if (statusCode == 200) {
                updateConfiguration(EntityUtils.toString(response.entity))
            } else {
                LOG.info("Azure instance metadata is not available: Failed to connect to $METADATA_URL: HTTP $statusCode")
            }
        }
    }

    internal fun updateConfiguration(json: String) {
        val metadata = deserializeMetadata(json)
        if (metadata == null) {
            LOG.info("Azure instance metadata is not available: Invalid instance metadata")
            LOG.debug(json)
            return
        }

        metadata.compute?.name?.let {
            if (it.isNotBlank() && configuration.name.isBlank()) {
                LOG.info("Setting name from instance metadata: $it")
                configuration.name = it
            }
        }

        metadata.network?.interfaces?.firstOrNull()?.ipv4?.ipAddress?.firstOrNull()?.publicIpAddress?.let {
            if (it.isNotBlank()) {
                LOG.info("Setting external IP address from instance metadata: $it")
                configuration.addAlternativeAgentAddress(it)
                configuration.addSystemProperty("ec2.public-hostname", it)
            }
        }
    }

    private fun createHttpClient(): CloseableHttpClient {
        val requestConfig = RequestConfig.custom().setSocketTimeout(TIMEOUT).setConnectTimeout(TIMEOUT).build()
        return HttpClients.custom().useSystemProperties().setDefaultRequestConfig(requestConfig).build()
    }

    data class Metadata(
            val compute: Compute?,
            val network: Network?
    )

    data class Compute(
            val name: String?
    )

    data class Network(
            @SerializedName("interface")
            val interfaces: List<NetworkInterface>?
    )

    data class NetworkInterface(
            val ipv4: NetworkProtocol?,
            val ipv6: NetworkProtocol?
    )

    data class NetworkProtocol(
            val ipAddress: List<NetworkIpAddress>?
    )

    data class NetworkIpAddress(
            val privateIpAddress: String?,
            val publicIpAddress: String?
    )

    companion object {
        private val LOG = Logger.getInstance(AzureMetadataReader::class.java.name)
        private val METADATA_URL = "http://169.254.169.254/metadata/instance?api-version=2017-04-02"
        private val TIMEOUT = 10000
        private val GSON = Gson()

        fun deserializeMetadata(json: String) = try {
            GSON.fromJson<Metadata>(json, Metadata::class.java)
        } catch (e: Exception) {
            LOG.debug("Failed to deserialize JSON data ${e.message}", e)
            null
        }
    }
}
