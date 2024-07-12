package jetbrains.buildServer.clouds.azure.arm.web.update.handler

import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.web.update.ImageUpdateConstants
import jetbrains.buildServer.controllers.BasePropertiesBean

class UpsertImageHandler : AbstractImageUpdateHandler() {
    override fun processUpdate(props: BasePropertiesBean): String {
        val passStruct = parseString(props.properties[ImageUpdateConstants.PASSWORDS_DATA]!!)!!
        passStruct.addProperty(props.properties[AzureConstants.VM_NAME_PREFIX], props.properties[ImageUpdateConstants.PASSWORD])
        return serialize(passStruct)
    }

    override fun type(): ImageUpdateType {
        return ImageUpdateType.UPSERT
    }
}
