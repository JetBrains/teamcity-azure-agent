

package jetbrains.buildServer.clouds.azure

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.BuildAgentConfigurationEx

class WindowsCustomDataReader(agentConfiguration: BuildAgentConfigurationEx,
                              fileUtils: FileUtils)
    : AzureCustomDataReader(agentConfiguration, fileUtils) {

    override val customDataFileName: String
        get() = WINDOWS_CUSTOM_DATA_FILE

    override fun parseCustomData(customData: String): MetadataReaderResult {
        // Process custom data
        return try {
            processCustomData(customData)
        } catch (e: Exception) {
            LOG.warnAndDebugDetails(String.format(UNABLE_TO_READ_CUSTOM_DATA_FILE, customDataFileName), e)
            MetadataReaderResult.SKIP
        }
    }

    companion object {
        private val LOG = Logger.getInstance(WindowsCustomDataReader::class.java.name)
        private val SYSTEM_DRIVE = System.getenv("SystemDrive")
        private val WINDOWS_CUSTOM_DATA_FILE = "$SYSTEM_DRIVE\\AzureData\\CustomData.bin"
    }
}
