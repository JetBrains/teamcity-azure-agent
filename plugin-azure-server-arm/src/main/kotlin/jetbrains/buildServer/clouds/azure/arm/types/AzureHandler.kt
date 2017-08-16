package jetbrains.buildServer.clouds.azure.arm.types

import jetbrains.buildServer.clouds.azure.arm.AzureCloudImage
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails
import jetbrains.buildServer.clouds.azure.arm.AzureCloudInstance
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import kotlinx.coroutines.experimental.Deferred

interface AzureHandler {
    fun checkImageAsync(image: AzureCloudImage): Deferred<List<Throwable>>
    fun prepareBuilderAsync(instance: AzureCloudInstance): Deferred<ArmTemplateBuilder>
    fun getImageHashAsync(details: AzureCloudImageDetails): Deferred<String>
}
