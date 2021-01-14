/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

class WindowsCustomDataReader(agentConfiguration: BuildAgentConfigurationEx,
                              fileUtils: FileUtils)
    : AzureCustomDataReader(agentConfiguration, fileUtils) {

    override val customDataFileName: String
        get() = WINDOWS_CUSTOM_DATA_FILE

    override fun parseCustomData(customData: String) {
        // Process custom data
        try {
            processCustomData(customData)
        } catch (e: Exception) {
            LOG.warnAndDebugDetails(String.format(UNABLE_TO_READ_CUSTOM_DATA_FILE, customDataFileName), e)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(WindowsCustomDataReader::class.java.name)
        private val SYSTEM_DRIVE = System.getenv("SystemDrive")
        private val WINDOWS_CUSTOM_DATA_FILE = "$SYSTEM_DRIVE\\AzureData\\CustomData.bin"
    }
}
