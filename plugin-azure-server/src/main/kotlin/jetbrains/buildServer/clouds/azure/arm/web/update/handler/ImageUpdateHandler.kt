package jetbrains.buildServer.clouds.azure.arm.web.update.handler

import jetbrains.buildServer.clouds.CloudProfile
import jetbrains.buildServer.clouds.CloudProfileData
import jetbrains.buildServer.controllers.BasePropertiesBean

interface ImageUpdateHandler {
    fun processUpdate(props: BasePropertiesBean): String

    fun type(): ImageUpdateType
}
