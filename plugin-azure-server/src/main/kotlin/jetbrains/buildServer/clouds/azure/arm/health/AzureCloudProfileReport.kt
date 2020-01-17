/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.azure.arm.health

import jetbrains.buildServer.clouds.azure.AzureMetadata
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.executors.ExecutorServices
import jetbrains.buildServer.serverSide.healthStatus.*
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
