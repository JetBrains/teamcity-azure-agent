package jetbrains.buildServer.clouds.azure.arm.types

import jetbrains.buildServer.clouds.azure.arm.AzureCloudImage
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails
import jetbrains.buildServer.clouds.azure.arm.AzureCloudInstance
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.async
import java.util.*

class AzureContainerHandler : AzureHandler {
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
        if (details.osType.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Invalid OS Type value"))
        }

        if (details.imageId.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Docker image is empty"))
        }

        exceptions
    }

    override fun prepareBuilderAsync(instance: AzureCloudInstance) = async(CommonPool) {
        val details = instance.image.imageDetails
        val template = AzureUtils.getResourceAsString("/templates/container-template.json")
        val builder = ArmTemplateBuilder(template)

        builder.setParameterValue("containerName", instance.name)
                .setParameterValue(AzureConstants.IMAGE_ID, details.imageId!!.trim())
                .setParameterValue(AzureConstants.OS_TYPE, details.osType!!)
                .setParameterValue(AzureConstants.NUMBER_CORES, details.numberCores!!)
                .setParameterValue(AzureConstants.MEMORY, details.memory!!)
                .addContainer(instance.name)
    }

    override fun getImageHashAsync(details: AzureCloudImageDetails) =
            CompletableDeferred(Integer.toHexString(details.imageId!!.hashCode())!!)
}
