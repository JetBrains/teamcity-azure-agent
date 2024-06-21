

package jetbrains.buildServer.clouds.azure.arm.types

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImage
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails
import jetbrains.buildServer.clouds.azure.arm.AzureCloudInstance
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.throttler.ThrottlerExecutionTaskException
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import kotlinx.coroutines.coroutineScope

class AzureInstanceHandler(private val connector: AzureApiConnector) : AzureHandler {
    override suspend fun checkImage(image: AzureCloudImage) = coroutineScope {
        val exceptions = ArrayList<Throwable>()
        val details = image.imageDetails

        if (details.instanceId.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Invalid instance id"))
        } else {
            try {
                if (!connector.hasInstance(image)) {
                    exceptions.add(CheckedCloudException("Virtual machine ${details.sourceId} is not found"))
                }
            } catch (e: ThrottlerExecutionTaskException) {
                exceptions.add(CheckedCloudException("Could not update virtual machines list. Please wait"))
            } catch (e: Throwable) {
                LOG.infoAndDebugDetails("Failed to get list of virtual machines", e)
                exceptions.add(e)
            }
        }

        exceptions
    }

    override suspend fun prepareBuilder(instance: AzureCloudInstance): ArmTemplateBuilder {
        throw NotImplementedError()
    }

    override suspend fun getImageHash(details: AzureCloudImageDetails) = coroutineScope {
        throw NotImplementedError()
    }

    companion object {
        private val LOG = Logger.getInstance(AzureInstanceHandler::class.java.name)
    }
}
