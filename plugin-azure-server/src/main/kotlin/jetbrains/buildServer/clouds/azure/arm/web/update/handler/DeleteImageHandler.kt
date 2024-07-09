package jetbrains.buildServer.clouds.azure.arm.web.update.handler

import jetbrains.buildServer.clouds.CloudProfile
import jetbrains.buildServer.controllers.BasePropertiesBean

class DeleteImageHandler : ImageUpdateHandler {
    override fun processUpdate(cloudProfile: CloudProfile, props: BasePropertiesBean) {
        TODO("Not yet implemented")
    }

    override fun type(): ImageUpdateType {
        return ImageUpdateType.DELETE
    }
}
