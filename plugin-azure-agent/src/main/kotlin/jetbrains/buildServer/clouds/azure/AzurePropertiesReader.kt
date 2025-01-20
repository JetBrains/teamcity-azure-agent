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
        val userDataProcessResult = myMetadataReader.process()
        if (userDataProcessResult == MetadataReaderResult.PROCESSED) {
            LOG.info("Processed customData from IMDS userData")
            return
        }

        // Try to use environment variables
        if (myEnvironmentReader.process()) {
            LOG.info("Processed customData from environment variables")
            return
        }

        // Then override them by custom data if available
        val fileMetadataResult = when {
            SystemInfo.isUnix -> myUnixCustomDataReader.process()
            SystemInfo.isWindows -> myWindowsCustomDataReader.process()
            else -> {
                LOG.warn("Unsupported OS family ${SystemInfo.OS_ARCH}(${SystemInfo.OS_VERSION})")
                MetadataReaderResult.SKIP
            }
        }
        if (fileMetadataResult == MetadataReaderResult.PROCESSED) {
            LOG.info("Processed customData from Azure binary file")
        }

        if (userDataProcessResult == MetadataReaderResult.NEED_POST_PROCESS) {
            myMetadataReader.postProcess()
            LOG.info("Post-processed IMDS userData")
        } else {
            if (fileMetadataResult != MetadataReaderResult.PROCESSED) {
                LOG.info("Azure integration is disabled.")
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(AzurePropertiesReader::class.java.name)
    }
}
