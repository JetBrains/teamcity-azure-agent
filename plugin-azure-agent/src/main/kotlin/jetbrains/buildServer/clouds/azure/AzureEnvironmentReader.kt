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

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.BuildAgentConfigurationEx

/**
 * Process configuration settings from environment
 */
class AzureEnvironmentReader(private val configuration: BuildAgentConfigurationEx) {
    fun process(): Boolean {
        System.getenv(AzureProperties.INSTANCE_ENV_VAR)?.let {
            AzureCompress.decode(it).forEach { (key, value) ->
                configuration.addConfigurationParameter(key, value)
                LOG.info("Added configuration parameter: {$key, $value}")
            }
            configuration.addEnvironmentVariable(AzureProperties.INSTANCE_ENV_VAR, "")
            return true
        }

        return false
    }

    companion object {
        private val LOG = Logger.getInstance(AzureEnvironmentReader::class.java.name)
    }
}
