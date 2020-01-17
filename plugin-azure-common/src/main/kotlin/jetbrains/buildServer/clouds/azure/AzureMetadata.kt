/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.io.IOException
import java.net.SocketTimeoutException

object AzureMetadata {

    fun readInstanceMetadata(): Metadata {
        val requestConfig = RequestConfig.custom()
                .setConnectTimeout(PING_CONNECTION_TIMEOUT_MS)
                .build()

        HttpClients.custom()
                .useSystemProperties()
                .setDefaultRequestConfig(requestConfig)
                .build().use {
                    for (i in 1..PING_MAX_TRIES) {
                        val response = try {
                            it.execute(HttpGet(METADATA_URL).apply {
                                addHeader("Metadata", "true")
                            })
                        } catch (ignored: SocketTimeoutException) {
                            // Ignore logging timeouts which is the expected failure mode in non Azure environments.
                            continue
                        } catch (e: Exception) {
                            throw IOException("Failed to connect to $METADATA_URL: ${e.message}")
                        }

                        val statusCode = response.statusLine.statusCode
                        if (statusCode == 200) {
                            return deserializeMetadata(EntityUtils.toString(response.entity))
                        } else {
                            throw IOException("Failed to connect to $METADATA_URL: HTTP $statusCode")
                        }
                    }
                }

        throw IOException("Unable to connect to $METADATA_URL in $PING_MAX_TRIES attempts")
    }

    fun deserializeMetadata(json: String): Metadata = try {
        GSON.fromJson<Metadata>(json, Metadata::class.java)
    } catch (e: Exception) {
        throw IOException("Invalid instance metadata ${e.message}", e)
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

    private const val METADATA_URL = "http://169.254.169.254/metadata/instance?api-version=2017-04-02"

    // Note: the explicit `timeout` and `tries` below is a workaround. The underlying
    // issue is that resolving an unknown host on some networks will take
    // 20-30 seconds; making this timeout short fixes the issue, but
    // could lead to false negatives in the event that we are on GCE, but
    // the metadata resolution was particularly slow. The latter case is
    // "unlikely" since the expected 4-nines time is about 0.5 seconds.
    // This allows us to limit the total ping maximum timeout to 1.5 seconds
    // for developer desktop scenarios.
    private const val PING_MAX_TRIES = 3
    private const val PING_CONNECTION_TIMEOUT_MS = 500
    private val GSON = Gson()
}
