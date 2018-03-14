package jetbrains.buildServer.clouds.azure.arm.types

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.*
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.async
import java.util.*

class AzureTemplateHandler : AzureHandler {
    @Suppress("UselessCallOnNotNull")
    override fun checkImageAsync(image: AzureCloudImage) = async(CommonPool) {
        val exceptions = ArrayList<Throwable>()
        val details = image.imageDetails
        if (details.sourceId.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Invalid source id"))
        }
        if (details.region.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Invalid region"))
        }

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

    override fun prepareBuilderAsync(instance: AzureCloudInstance) = async(CommonPool) {
        val details = instance.image.imageDetails
        ArmTemplateBuilder(details.template!!)
                .setParameterValue("vmName", instance.name)
    }

    override fun getImageHashAsync(details: AzureCloudImageDetails) =
            CompletableDeferred(Integer.toHexString(details.template!!.hashCode())!!)

    companion object {
        private val LOG = Logger.getInstance(AzureTemplateHandler::class.java.name)
    }
}
