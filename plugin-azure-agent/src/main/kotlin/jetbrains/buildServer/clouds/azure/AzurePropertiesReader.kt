package jetbrains.buildServer.clouds.azure

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.agent.BuildAgent
import jetbrains.buildServer.agent.BuildAgentConfigurationEx
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.util.EventDispatcher

/**
 * Updates agent properties.
 */
class AzurePropertiesReader(
    events: EventDispatcher<AgentLifeCycleListener>,
    private val myUnixCustomDataReader: UnixCustomDataReader,
    private val myWindowsCustomDataReader: WindowsCustomDataReader,
    private val myMetadataReader: AzureMetadataReader,
    private val myEnvironmentReader: AzureEnvironmentReader,
    private val configuration: BuildAgentConfigurationEx,
    private val spotTerminationChecker: SpotInstanceTerminationChecker
) {
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
        myMetadataReader
            .process()
            .onSuccess { data ->
                if (data.hasNewIntegrationMarkerTags) {
                    LOG.info("Azure integration is disabled. There are tags for new Azure integration on the VM.")
                    return
                }

                // Priority to config in the environment variables
                if (processConfigFromEnvironmentVar()) {
                    reportAzureEnvironmentParametersToConfiguration(data, false)
                    runSpotInstanceCheckerIfNeed(data)
                    logDoneAgentConfiguration()
                    return@onSuccess
                }

                val userData = data
                    .getUserData()
                    .onFailure { e ->
                        when(e) {
                            is UserDataIsEmptyException -> LOG.info("No Azure userData provided")
                            is UserDataDeserializationException -> LOG.warnAndDebugDetails("Could not deserialize Azure userData.", e)
                            is UserDataIncorrectCodeException ->  {
                                LOG.info("Azure integration is disabled. ${e.message}")
                                return
                            }
                            else -> LOG.warnAndDebugDetails("Could not get Azure user data.", e)
                        }
                    }
                    .getOrNull()

                if (!processCustomDataFile(data, userData)) {
                    logSkipAgentConfiguration()
                } else {
                    logDoneAgentConfiguration()
                }
            }
            .onFailure { e ->
                LOG.info("Azure instance metadata is not available: " + e.message)
                LOG.debug(e)

                // Possible run in container
                if (!processConfigFromEnvironmentVar()) {
                    if (!processConfigFromCustomDataFile(false)) {
                        logSkipAgentConfiguration()
                        return@onFailure
                    }
                }
                logDoneAgentConfiguration()
            }
    }

    private fun processCustomDataFile(data: AzureMetadataReaderData, userData: AzureUserData? = null): Boolean {
        if (!processConfigFromCustomDataFile(data.hasMarkerTags)) {
            LOG.info("Azure CustomData file is unavailable or invalid.")

            if (configuration.configurationParameters[AzureProperties.INSTANCE_NAME] != null) {
                LOG.info("Azure instance name is provided by configuration parameters.")
            } else {
                return false
            }
        }
        reportAzureEnvironmentParametersToConfiguration(data)
        runSpotInstanceCheckerIfNeed(data)
        userData?.let(::updateConfigurationFromUserData)

        return true
    }

    private fun logDoneAgentConfiguration() {
        LOG.info("Azure plugin configuration is done")
    }

    private fun logSkipAgentConfiguration() {
        LOG.info("Azure plugin skips further agent configuration.")
    }

    private fun runSpotInstanceCheckerIfNeed(data: AzureMetadataReaderData) {
        val compute = data.metadata.compute
        if (compute != null) {
            if (compute.priority?.equals("Spot", true) == true) {
                val name = compute.name
                if (name != null) {
                    LOG.info("Spot instance detected. Starting spot instance termination checker.")
                    spotTerminationChecker.start(name)
                } else {
                    LOG.info("Spot instance detected. But VNM name is empty. Skipping spot instance termination checker.")
                }
            } else {
                LOG.info("The VM is not spot (${compute.priority}). Skipping spot instance termination checker.")
            }
        } else {
            LOG.info("Compute section of VM metadata is empty. Skipping spot instance termination checker.")
        }
    }

    private fun reportAzureEnvironmentParametersToConfiguration(data: AzureMetadataReaderData, updateConfigurationName: Boolean = true) {
        data.updateConfiguration(configuration, updateConfigurationName)
    }

    private fun processConfigFromCustomDataFile(shouldRetryOnFail: Boolean): Boolean {
        LOG.info("Processing customData file.")
        return when {
            SystemInfo.isUnix -> myUnixCustomDataReader.process(shouldRetryOnFail)
            SystemInfo.isWindows -> myWindowsCustomDataReader.process(shouldRetryOnFail)
            else -> {
                LOG.warn("Unsupported OS family ${SystemInfo.OS_ARCH}(${SystemInfo.OS_VERSION})")
                false
            }
        }
    }

    private fun processConfigFromEnvironmentVar(): Boolean =
        myEnvironmentReader.process().also {
            if (it) LOG.info("Processed customData from environment variables")
        }

    private fun updateConfigurationFromUserData(userData: AzureUserData) {
        val data = CloudInstanceUserData.deserialize(userData.cloudInstanceUserData)
        if (data == null) {
            LOG.error("Unable to deserialize userData.cloudInstanceUserData value: '${userData.cloudInstanceUserData}'")
            return
        }

        data.customAgentConfigurationParameters[STARTING_INSTANCE_ID]?.let {
            configuration.addConfigurationParameter(STARTING_INSTANCE_ID, it)
            LOG.info("Updated configuration parameter: {$STARTING_INSTANCE_ID, $it}")
        }
    }

    companion object {
        private const val STARTING_INSTANCE_ID = "teamcity.agent.startingInstanceId"
        private val LOG = Logger.getInstance(AzurePropertiesReader::class.java.name)
    }
}
