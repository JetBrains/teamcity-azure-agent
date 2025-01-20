package jetbrains.buildServer.clouds.azure.arm.web.update

import jetbrains.buildServer.clouds.azure.arm.AzureConstants

enum class ImageUpdateProperties(val propertyName: String) {
    VM_NAME_PREFIX(AzureConstants.VM_NAME_PREFIX),
    UPDATE_TYPE(ImageUpdateConstants.IMAGE_UPDATE_TYPE),
    PASSWORDS_DATA(ImageUpdateConstants.PASSWORDS_DATA),
}
