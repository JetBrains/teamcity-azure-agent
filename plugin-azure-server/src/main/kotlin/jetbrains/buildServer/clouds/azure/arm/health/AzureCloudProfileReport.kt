package jetbrains.buildServer.clouds.azure.arm.health

import jetbrains.buildServer.clouds.azure.AzureMetadata
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.executors.ExecutorServices
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusScope
import jetbrains.buildServer.serverSide.healthStatus.ProjectSuggestedItem
import jetbrains.buildServer.web.openapi.PagePlaces
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.healthStatus.suggestions.ProjectSuggestion

class AzureCloudProfileReport(pagePlaces: PagePlaces,
                              executorServices: ExecutorServices,
                              private val pluginDescriptor: PluginDescriptor)
    : ProjectSuggestion("addAzureCloudProfile", "Detect and report Azure cloud profile issues", pagePlaces) {

    private var azureMetadataAvailable = false

    init {
        // initializing report data in background, as it can take some time and we do not want to slow down server startup
        executorServices.lowPriorityExecutorService.submit {
            try {
                AzureMetadata.readInstanceMetadata().compute?.name?.let {
                    azureMetadataAvailable = true
                }
            } catch (ignored: Throwable) {
                return@submit
            }
        }
    }

    override fun getSuggestions(project: SProject): List<ProjectSuggestedItem> {
        if (!project.isRootProject || !azureMetadataAvailable) {
            return emptyList()
        }

        if (!containsCloudProfile(project, AzureConstants.CLOUD_CODE)) {
            return listOf(ProjectSuggestedItem(type, project, mapOf(
                    "projectId" to project.projectId,
                    "type" to AzureConstants.CLOUD_CODE
            )))
        }

        return emptyList()
    }

    override fun getViewUrl(): String {
        return pluginDescriptor.getPluginResourcesPath("health/addAzureCloudProfile.jsp")
    }

    override fun canReportItemsFor(scope: HealthStatusScope): Boolean {
        return true
    }

    private fun containsCloudProfile(project: SProject, cloudCode: String): Boolean {
        return project.getAvailableFeaturesOfType("CloudProfile").any {
            cloudCode == it.parameters["cloud-code"]
        } || project.projects.filter {
            !it.isArchived && !it.isReadOnly
        }.any {
            containsCloudProfile(it, cloudCode)
        }
    }
}
