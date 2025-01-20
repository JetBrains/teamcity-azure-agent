package jetbrains.buildServer.clouds.azure.arm.web.update.handler

import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.web.update.ImageUpdateConstants
import jetbrains.buildServer.clouds.azure.arm.web.update.UpdateImageResult
import jetbrains.buildServer.controllers.BasePropertiesBean

class UpsertImageHandler : AbstractImageUpdateHandler() {
    override fun processUpdate(props: BasePropertiesBean): UpdateImageResult {
        var data = props.properties[ImageUpdateConstants.PASSWORDS_DATA]!!

        if (data == "") {
            data = "{}"
        }

        val passStruct = parseString(data)!!
        passStruct.addProperty(
            props.properties[AzureConstants.VM_NAME_PREFIX],
            props.properties[ImageUpdateConstants.PASSWORD]
        )

        return UpdateImageResult(serialize(passStruct))
    }

    override fun type(): ImageUpdateType {
        return ImageUpdateType.UPSERT
    }
}
