package jetbrains.buildServer.clouds.azure.arm.web.update.handler

import jetbrains.buildServer.clouds.CloudProfile
import jetbrains.buildServer.clouds.CloudProfileData
import jetbrains.buildServer.controllers.BasePropertiesBean

class AddImageHandler : ImageUpdateHandler {
    override fun processUpdate(cloudProfile: CloudProfile, props: BasePropertiesBean): CloudProfileData {
        return null
    }

    override fun type(): ImageUpdateType {
        return ImageUpdateType.ADD
    }
}
