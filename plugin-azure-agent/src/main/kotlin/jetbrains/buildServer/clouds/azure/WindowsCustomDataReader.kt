package jetbrains.buildServer.clouds.azure

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.BuildAgentConfigurationEx

class WindowsCustomDataReader(agentConfiguration: BuildAgentConfigurationEx,
                              idleShutdown: IdleShutdown,
                              fileUtils: FileUtils)
    : AzureCustomDataReader(agentConfiguration, idleShutdown, fileUtils) {

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
