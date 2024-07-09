package jetbrains.buildServer.clouds.azure.arm.web.update

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.CloudProfile
import jetbrains.buildServer.clouds.CloudProfileData
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.web.SettingsController
import jetbrains.buildServer.clouds.azure.arm.web.update.handler.ImageUpdateHandler
import jetbrains.buildServer.clouds.azure.arm.web.update.handler.ImageUpdateType
import jetbrains.buildServer.clouds.server.CloudManagerBase
import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.serverSide.identifiers.ProjectIdentifiersManager

class UpdateImageProcessor(
    private val myCloudManagerBase: CloudManagerBase,
    private val myIdManager: ProjectIdentifiersManager,
    actionHandlers: List<ImageUpdateHandler>
) {

    companion object {
        private val log = Logger.getInstance(SettingsController::class.java.name)
    }

    private val updateHandlersMap: Map<ImageUpdateType, ImageUpdateHandler> =
        actionHandlers.associateBy { it.type() }

    fun updateImage(props: BasePropertiesBean) {
        log.debug { "Received a request to update image = [${props.properties["image"]}]" }

        val profileId = props.properties[AzureConstants.PROFILE_ID]!!
        val projectId = props.properties[AzureConstants.PROJECT_ID]!!

        val profile = getCloudProfile(projectId, profileId)

        if (profile == null) {
            log.debug { "Profile $profileId not found, update aborted" }
            return
        }

        val updatedProfileData = processImageUpdate(props, profile)
        myCloudManagerBase.updateProfile()
    }

    private fun processImageUpdate(
        props: BasePropertiesBean,
        profile: CloudProfile
    ): CloudProfileData {
        val action = props.properties[AzureConstants.IMAGE_UPDATE_TYPE]!!
        val handler = updateHandlersMap[ImageUpdateType.fromValue(action)]!!

        log.debug { "Processing action = [$action] for ${profile.profileId}" }

        return handler.processUpdate(profile, props)
    }

    private fun getCloudProfile(
        projectId: String,
        profileId: String
    ): CloudProfile? {
        val internalProjectId = getInternalProjectId(projectId)
        return myCloudManagerBase.findProfileById(internalProjectId, profileId)
    }

    private fun getInternalProjectId(projectId: String) =
        if (!myIdManager.isExternalIdAlias(projectId)) {
            myIdManager.externalToInternal(projectId)
                ?: throw IllegalStateException("Failed to get internal ID for $projectId")
        } else projectId
}
