

package jetbrains.buildServer.clouds.azure.arm.types

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.*
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_DEPLOYMENT_TEMPLATE_POSRT_UPDATE_DISABLE
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import jetbrains.buildServer.serverSide.TeamCityProperties
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
        details.checkTemplate(exceptions)
        details.checkCustomTags(exceptions)
        exceptions
    }

    override suspend fun prepareBuilder(instance: AzureCloudInstance) = coroutineScope {
        val details = instance.image.imageDetails
        ArmTemplateBuilder(details.template!!, details.disableTemplateModification ?: false)
            .let {
                if (TeamCityProperties.getBoolean(TEAMCITY_CLOUDS_AZURE_DEPLOYMENT_TEMPLATE_POSRT_UPDATE_DISABLE)) {
                    it
                } else {
                    it.appendInnerTemplate(AzureUtils.getResourceAsString("/templates/os-disk-deleteOption-template.json"))
                }
            }
            .setParameterValue("vmName", instance.name)
    }

    override suspend fun getImageHash(details: AzureCloudImageDetails) = coroutineScope {
        Integer.toHexString(details.template!!.hashCode())!!
    }

    companion object {
        internal val LOG = Logger.getInstance(AzureTemplateHandler::class.java.name)
    }
}
