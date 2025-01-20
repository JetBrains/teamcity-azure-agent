package jetbrains.buildServer.clouds.azure.arm.web.update.handler

import jetbrains.buildServer.clouds.azure.arm.web.update.UpdateImageResult
import jetbrains.buildServer.controllers.BasePropertiesBean

interface ImageUpdateHandler {
    fun processUpdate(props: BasePropertiesBean): UpdateImageResult

    fun type(): ImageUpdateType
}
