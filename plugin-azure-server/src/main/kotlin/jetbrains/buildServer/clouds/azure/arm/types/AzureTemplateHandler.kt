package jetbrains.buildServer.clouds.azure.arm.types

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.*
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import kotlinx.coroutines.coroutineScope
import java.util.*

class AzureTemplateHandler(private val connector: AzureApiConnector) : AzureHandler {
    @Suppress("UselessCallOnNotNull")
    override suspend fun checkImage(image: AzureCloudImage) = coroutineScope {
        val exceptions = ArrayList<Throwable>()
        val details = image.imageDetails

        details.checkSourceId(exceptions)
        details.checkRegion(exceptions)
        details.checkResourceGroup(connector, exceptions)

        val template = details.template
        if (template == null || template.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Template is empty"))
        } else {
            try {
                AzureUtils.checkTemplate(template)
            } catch (e: Throwable) {
                LOG.infoAndDebugDetails("Invalid template", e)
                exceptions.add(e)
            }
        }

        exceptions
    }

    override suspend fun prepareBuilder(instance: AzureCloudInstance) = coroutineScope {
        val details = instance.image.imageDetails
        ArmTemplateBuilder(details.template!!)
                .setParameterValue("vmName", instance.name)
    }

    override suspend fun getImageHash(details: AzureCloudImageDetails) = coroutineScope {
        Integer.toHexString(details.template!!.hashCode())!!
    }

    companion object {
        private val LOG = Logger.getInstance(AzureTemplateHandler::class.java.name)
    }
}
