package jetbrains.buildServer.clouds.azure

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.BuildAgentConfigurationEx
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.serverSide.TeamCityProperties
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.math.pow

abstract class AzureCustomDataReader(private val myAgentConfiguration: BuildAgentConfigurationEx,
                                     private val myFileUtils: FileUtils) {

    protected abstract val customDataFileName: String

    fun process(shouldRetryOnFail: Boolean) : MetadataReaderResult {
        val customDataFile = File(customDataFileName)
        val maxDelay = TeamCityProperties.getLong("teamcity.azure.customData.retry.maxDelay", 30_000)
        val maxRetries = if (shouldRetryOnFail) TeamCityProperties.getInteger("teamcity.azure.customData.retry.maxRetries", 7) else 0
        val delay = TeamCityProperties.getLong("teamcity.azure.customData.retry.delay", 5_000)
        val customData = try {
            Retry<String>()
                .maxDelay(maxDelay)
                .maxRetries(maxRetries)
                .intervalFunction { attemptNo ->
                    delay * BACKOFF_FACTOR.pow(attemptNo - 1).toLong()
                }
                .logRetry { attemptNo, throwable ->
                    LOG.warnAndDebugDetails("Failed to read Azure custom data file $customDataFile attempt $attemptNo", throwable)
                }
                .retryOn(IOException::class.java, EmptyCustomDataFileException::class.java)
                .block {
                    myFileUtils
                        .readFile(customDataFile)
                        .also {
                            if (it.isBlank()) {
                                throw EmptyCustomDataFileException("Azure custom data file $customDataFile is empty")
                            }
                        }
                }
        } catch (e: FileNotFoundException) {
            val message = AzureUtils.getFileNotFoundMessage(e)
            LOG.info(String.format(FAILED_TO_READ_CUSTOM_DATA_FILE, customDataFile, message))
            LOG.debug(e)
            return MetadataReaderResult.SKIP
        } catch (e: EmptyCustomDataFileException) {
            LOG.info(e.message)
            return MetadataReaderResult.SKIP
        } catch (e: Exception) {
            LOG.info(String.format(FAILED_TO_READ_CUSTOM_DATA_FILE, customDataFile, e.message))
            LOG.debug(e)
            return MetadataReaderResult.SKIP
        }

        return parseCustomData(customData)
    }

    protected abstract fun parseCustomData(customData: String): MetadataReaderResult

    protected fun processCustomData(serializedCustomData: String) : MetadataReaderResult {
        val data = CloudInstanceUserData.deserialize(serializedCustomData)
        if (data == null) {
            LOG.info("Unable to deserialize customData: '$serializedCustomData'")
            return MetadataReaderResult.SKIP
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

        data.customAgentConfigurationParameters.forEach { (key, value) ->
            myAgentConfiguration.addConfigurationParameter(key, value)
            LOG.info("Added configuration parameter: {$key, $value}")
        }
        return MetadataReaderResult.PROCESSED
    }

    class EmptyCustomDataFileException(message: String) : Exception(message)

    companion object {
        private val LOG = Logger.getInstance(AzureCustomDataReader::class.java.name)
        private const val FAILED_TO_READ_CUSTOM_DATA_FILE = "Failed to read azure custom data file %s: %s"
        const val UNABLE_TO_READ_CUSTOM_DATA_FILE = "Unable to read azure custom data file %s: will use existing parameters"
        private const val BACKOFF_FACTOR = 2.0
    }
}
