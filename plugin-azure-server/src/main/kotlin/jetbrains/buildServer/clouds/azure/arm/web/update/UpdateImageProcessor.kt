package jetbrains.buildServer.clouds.azure.arm.web.update

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.web.SettingsController
import jetbrains.buildServer.clouds.azure.arm.web.update.handler.ImageUpdateHandler
import jetbrains.buildServer.clouds.azure.arm.web.update.handler.ImageUpdateType
import jetbrains.buildServer.controllers.BasePropertiesBean

class UpdateImageProcessor(
    actionHandlers: List<ImageUpdateHandler>
) {

    companion object {
        private val log = Logger.getInstance(SettingsController::class.java.name)
    }

    private val updateHandlersMap: Map<ImageUpdateType, ImageUpdateHandler> =
        actionHandlers.associateBy { it.type() }

    fun processImageUpdate(props: BasePropertiesBean): String {
        log.debug { "Received a request to update image = [${props.properties["image"]}]" }

        val updateType = props.properties[ImageUpdateConstants.IMAGE_UPDATE_TYPE]!!
        val handler = updateHandlersMap[ImageUpdateType.fromValue(updateType.lowercase())]!!

        return handler.processUpdate(props)
    }
}
