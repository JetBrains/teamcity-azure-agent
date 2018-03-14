package jetbrains.buildServer.clouds.azure

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.BuildAgentConfigurationEx
import jetbrains.buildServer.clouds.CloudInstanceUserData

import java.io.File
import java.io.FileNotFoundException

abstract class AzureCustomDataReader(private val myAgentConfiguration: BuildAgentConfigurationEx,
                                     private val myIdleShutdown: IdleShutdown,
                                     private val myFileUtils: FileUtils) {

    protected abstract val customDataFileName: String

    fun process() {
        val customDataFile = File(customDataFileName)
        val customData = try {
            myFileUtils.readFile(customDataFile)
        } catch (e: FileNotFoundException) {
            val message = AzureUtils.getFileNotFoundMessage(e)
            LOG.info(String.format(FAILED_TO_READ_CUSTOM_DATA_FILE, customDataFile, message))
            LOG.debug(e)
            return
        } catch (e: Exception) {
            LOG.info(String.format(FAILED_TO_READ_CUSTOM_DATA_FILE, customDataFile, e.message))
            LOG.debug(e)
            return
        }

        if (customData.isBlank()) {
            LOG.info("Azure custom data file $customDataFile is empty")
        } else {
            parseCustomData(customData)
        }
    }

    protected abstract fun parseCustomData(customData: String)

    protected fun processCustomData(serializedCustomData: String) {
        val data = CloudInstanceUserData.deserialize(serializedCustomData)
        if (data == null) {
            LOG.info("Unable to deserialize customData: '$serializedCustomData'")
            return
        }

        val serverAddress = data.serverAddress
        LOG.info("Set server URL to $serverAddress")
        myAgentConfiguration.serverUrl = serverAddress

        val agentName = data.agentName
        if (agentName.isNotBlank()) {
            LOG.info("Set azure instance name $agentName")
            myAgentConfiguration.name = agentName
            myAgentConfiguration.addConfigurationParameter(AzureProperties.INSTANCE_NAME, agentName)
        }

        data.idleTimeout?.let {
            LOG.info("Set idle timeout to $it")
            myIdleShutdown.setIdleTime(it)
        }

        data.customAgentConfigurationParameters.forEach { (key, value) ->
            myAgentConfiguration.addConfigurationParameter(key, value)
            LOG.info("Added configuration parameter: {$key, $value}")
        }
    }

    companion object {
        private val LOG = Logger.getInstance(AzureCustomDataReader::class.java.name)
        private const val FAILED_TO_READ_CUSTOM_DATA_FILE = "Azure integration is disabled. Failed to read azure custom data file %s: %s"
        const val UNABLE_TO_READ_CUSTOM_DATA_FILE = "Unable to read azure custom data file %s: will use existing parameters"
    }
}
