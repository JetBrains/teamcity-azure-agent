package jetbrains.buildServer.clouds.azure.arm.types

import jetbrains.buildServer.clouds.azure.arm.AzureCloudImage
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails
import jetbrains.buildServer.clouds.azure.arm.AzureCloudInstance
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder

interface AzureHandler {
    suspend fun checkImage(image: AzureCloudImage): List<Throwable>
    suspend fun prepareBuilder(instance: AzureCloudInstance): ArmTemplateBuilder
    suspend fun getImageHash(details: AzureCloudImageDetails): String
}
