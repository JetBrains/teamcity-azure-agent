package jetbrains.buildServer.clouds.azure.arm.web.update

import jetbrains.buildServer.clouds.azure.arm.AzureConstants

enum class RequiredBasicUpdateProperties(val propertyName: String) {
    PROFILE_ID(AzureConstants.PROFILE_ID),
    PROJECT_ID(AzureConstants.PROJECT_ID),
    VM_NAME_PREFIX(AzureConstants.VM_NAME_PREFIX),
    UPDATE_TYPE(AzureConstants.IMAGE_UPDATE_TYPE)
}
