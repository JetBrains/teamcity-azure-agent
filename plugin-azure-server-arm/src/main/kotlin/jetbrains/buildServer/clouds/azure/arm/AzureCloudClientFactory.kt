/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm

import jetbrains.buildServer.clouds.CloudClientParameters
import jetbrains.buildServer.clouds.CloudRegistrar
import jetbrains.buildServer.clouds.CloudState
import jetbrains.buildServer.clouds.azure.AzurePropertiesNames
import jetbrains.buildServer.clouds.azure.AzureUtils
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnectorImpl
import jetbrains.buildServer.clouds.base.AbstractCloudClientFactory
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.serverSide.AgentDescription
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.serverSide.ServerPaths
import jetbrains.buildServer.serverSide.ServerSettings
import jetbrains.buildServer.web.openapi.PluginDescriptor
import java.io.File
import java.util.*

/**
 * Constructs Azure ARM cloud clients.
 */
class AzureCloudClientFactory(cloudRegistrar: CloudRegistrar,
                              private val myPluginDescriptor: PluginDescriptor,
                              serverPaths: ServerPaths,
                              private val mySettings: ServerSettings)
    : AbstractCloudClientFactory<AzureCloudImageDetails, AzureCloudClient>(cloudRegistrar) {

    private val myAzureStorage: File

    init {
        myAzureStorage = File(serverPaths.pluginDataDirectory, "cloud-$cloudCode/indices")
        if (!myAzureStorage.exists()) {
            myAzureStorage.mkdirs()
        }
    }

    override fun createNewClient(state: CloudState,
                                 images: Collection<AzureCloudImageDetails>,
                                 params: CloudClientParameters): AzureCloudClient {
        return createNewClient(state, params, arrayOf<TypedCloudErrorInfo>())
    }

    override fun createNewClient(state: CloudState,
                                 params: CloudClientParameters,
                                 errors: Array<TypedCloudErrorInfo>): AzureCloudClient {
        val tenantId = getParameter(params, AzureConstants.TENANT_ID)
        val clientId = getParameter(params, AzureConstants.CLIENT_ID)
        val clientSecret = getParameter(params, AzureConstants.CLIENT_SECRET)
        val subscriptionId = getParameter(params, AzureConstants.SUBSCRIPTION_ID)
        val location = getParameter(params, AzureConstants.LOCATION)

        val apiConnector = AzureApiConnectorImpl(tenantId, clientId, clientSecret)
        apiConnector.setSubscriptionId(subscriptionId)
        apiConnector.setServerId(mySettings.serverUUID)
        apiConnector.setProfileId(state.profileId)
        apiConnector.setLocation(location)

        val azureCloudClient = AzureCloudClient(params, apiConnector, myAzureStorage)
        azureCloudClient.updateErrors(*errors)

        return azureCloudClient
    }

    private fun getParameter(params: CloudClientParameters, parameter: String): String {
        return params.getParameter(parameter) ?: throw RuntimeException(parameter + " must not be empty")
    }

    override fun parseImageData(params: CloudClientParameters): Collection<AzureCloudImageDetails> {
        return AzureUtils.parseImageData(AzureCloudImageDetails::class.java, params)
    }

    override fun checkClientParams(params: CloudClientParameters): Array<TypedCloudErrorInfo>? {
        return emptyArray()
    }

    override fun getCloudCode(): String {
        return "arm"
    }

    override fun getDisplayName(): String {
        return "Azure Resource Manager"
    }

    override fun getEditProfileUrl(): String? {
        return myPluginDescriptor.getPluginResourcesPath("settings.html")
    }

    override fun getInitialParameterValues(): Map<String, String> {
        return emptyMap()
    }

    override fun getPropertiesProcessor(): PropertiesProcessor {
        return PropertiesProcessor { properties ->
            properties.keys
                    .filter { SKIP_PARAMETERS.contains(it) }
                    .forEach { properties.remove(it) }

            emptyList()
        }
    }

    override fun canBeAgentOfType(description: AgentDescription): Boolean {
        return description.configurationParameters.containsKey(AzurePropertiesNames.INSTANCE_NAME)
    }

    companion object {
        private val SKIP_PARAMETERS = Arrays.asList(
                AzureConstants.IMAGE_URL, AzureConstants.OS_TYPE,
                AzureConstants.MAX_INSTANCES_COUNT, AzureConstants.VM_NAME_PREFIX,
                AzureConstants.VM_USERNAME, AzureConstants.VM_PASSWORD)
    }
}
