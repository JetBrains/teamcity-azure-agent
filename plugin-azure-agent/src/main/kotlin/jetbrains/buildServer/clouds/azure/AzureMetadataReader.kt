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

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.BuildAgentConfigurationEx

class AzureMetadataReader(private val configuration: BuildAgentConfigurationEx) {

    fun process() {
        val metadata = try {
            AzureMetadata.readInstanceMetadata()
        } catch (e: Throwable) {
            LOG.info("Azure instance metadata is not available: " + e.message)
            LOG.debug(e)
            return
        }

        updateConfiguration(metadata)
    }

    internal fun updateConfiguration(metadata: AzureMetadata.Metadata) {
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

    companion object {
        private val LOG = Logger.getInstance(AzureMetadataReader::class.java.name)
    }
}
