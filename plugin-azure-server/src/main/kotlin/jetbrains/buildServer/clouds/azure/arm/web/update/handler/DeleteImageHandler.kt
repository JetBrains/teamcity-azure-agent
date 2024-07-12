package jetbrains.buildServer.clouds.azure.arm.web.update.handler

import jetbrains.buildServer.clouds.CloudProfile
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.web.update.ImageUpdateConstants
import jetbrains.buildServer.controllers.BasePropertiesBean

class DeleteImageHandler : AbstractImageUpdateHandler() {
    override fun processUpdate(props: BasePropertiesBean): String {
        val passStruct = parseString(props.properties[ImageUpdateConstants.PASSWORDS_DATA]!!)!!
        passStruct.remove(props.properties[AzureConstants.VM_NAME_PREFIX])
        return serialize(passStruct)
    }

    override fun type(): ImageUpdateType {
        return ImageUpdateType.DELETE
    }
}
