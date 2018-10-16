package jetbrains.buildServer.clouds.azure.arm.types

import jetbrains.buildServer.clouds.azure.arm.AzureCloudDeployTarget
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException

@Suppress("UselessCallOnNotNull")
fun AzureCloudImageDetails.checkSourceId(errors: MutableList<Throwable>) {
    if (sourceId.isNullOrEmpty()) {
        errors.add(CheckedCloudException("Invalid source id"))
    }
}

fun AzureCloudImageDetails.checkRegion(errors: MutableList<Throwable>) {
    if (region.isNullOrEmpty()) {
        errors.add(CheckedCloudException("Invalid region"))
    }
}

fun AzureCloudImageDetails.checkOsType(errors: MutableList<Throwable>) {
    if (osType.isNullOrEmpty()) {
        errors.add(CheckedCloudException("Invalid OS Type value"))
    }
}

fun AzureCloudImageDetails.checkImageId(errors: MutableList<Throwable>) {
    if (imageId.isNullOrEmpty()) {
        errors.add(CheckedCloudException("Docker image is empty"))
    }
}

fun AzureCloudImageDetails.checkNetworkId(errors: MutableList<Throwable>) {
    if (networkId.isNullOrEmpty() || subnetId.isNullOrEmpty()) {
        errors.add(CheckedCloudException("Invalid network settings"))
    }
}

suspend fun AzureCloudImageDetails.checkResourceGroup(connector: AzureApiConnector, errors: MutableList<Throwable>) {
    if (target == AzureCloudDeployTarget.SpecificGroup) {
        if (groupId == null) {
            errors.add(CheckedCloudException("Resource group name is empty"))
        } else if (!connector.getResourceGroups().containsKey(groupId)) {
            errors.add(CheckedCloudException("Resource group \"$groupId\" does not exist"))
        }
    }
}
