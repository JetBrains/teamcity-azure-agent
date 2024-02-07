

package jetbrains.buildServer.clouds.azure

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.agent.BuildAgent
import jetbrains.buildServer.util.EventDispatcher

/**
 * Updates agent properties.
 */
class AzurePropertiesReader(events: EventDispatcher<AgentLifeCycleListener>,
                            private val myUnixCustomDataReader: UnixCustomDataReader,
                            private val myWindowsCustomDataReader: WindowsCustomDataReader,
                            private val myMetadataReader: AzureMetadataReader,
                            private val myEnvironmentReader: AzureEnvironmentReader) {

    init {
        LOG.info("Azure plugin initializing...")

        events.addListener(object : AgentLifeCycleAdapter() {
            override fun afterAgentConfigurationLoaded(agent: BuildAgent) {
                fetchConfiguration()
            }
        })
    }

    private fun fetchConfiguration() {
        // Try to get machine details from Instance Metadata Service
        myMetadataReader.process()

        // Try to use environment variables
        if (myEnvironmentReader.process()) {
            return
        }

        // Then override them by custom data if available
        when {
            SystemInfo.isUnix -> myUnixCustomDataReader.process()
            SystemInfo.isWindows -> myWindowsCustomDataReader.process()
            else -> {
                LOG.warn("Azure integration is disabled: unsupported OS family ${SystemInfo.OS_ARCH}(${SystemInfo.OS_VERSION})")
                return
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(AzurePropertiesReader::class.java.name)
    }
}
