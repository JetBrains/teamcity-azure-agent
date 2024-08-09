package jetbrains.buildServer.clouds.azure.arm.web.update

import jetbrains.buildServer.clouds.azure.arm.web.update.handler.ImageUpdateHandler
import jetbrains.buildServer.clouds.azure.arm.web.update.handler.ImageUpdateType
import jetbrains.buildServer.controllers.BasePropertiesBean

class UpdateImageProcessor(
    actionHandlers: List<ImageUpdateHandler>
) {

    private val updateHandlersMap: Map<ImageUpdateType, ImageUpdateHandler> =
        actionHandlers.associateBy { it.type() }

    fun processImageUpdate(props: BasePropertiesBean): UpdateImageResult {
        val updateType = props.properties[ImageUpdateConstants.IMAGE_UPDATE_TYPE]!!
        val handler = updateHandlersMap[ImageUpdateType.fromValue(updateType.lowercase())]!!

        return handler.processUpdate(props)
    }
}
