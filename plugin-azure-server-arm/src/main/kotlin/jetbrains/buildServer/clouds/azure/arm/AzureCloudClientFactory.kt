/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm

import jetbrains.buildServer.clouds.*
import jetbrains.buildServer.clouds.azure.AzureCloudImagesHolder
import jetbrains.buildServer.clouds.azure.AzurePropertiesNames
import jetbrains.buildServer.clouds.azure.AzureUtils
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnectorImpl
import jetbrains.buildServer.clouds.base.AbstractCloudClientFactory
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.serverSide.AgentDescription
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.serverSide.ServerSettings
import jetbrains.buildServer.web.openapi.PluginDescriptor
import java.util.*

/**
 * Constructs Azure ARM cloud clients.
 */
class AzureCloudClientFactory(cloudRegistrar: CloudRegistrar,
                              private val myPluginDescriptor: PluginDescriptor,
                              private val mySettings: ServerSettings,
                              private val myImagesHolder: AzureCloudImagesHolder)
    : AbstractCloudClientFactory<AzureCloudImageDetails, AzureCloudClient>(cloudRegistrar) {

    override fun createNewClient(state: CloudState,
                                 images: Collection<AzureCloudImageDetails>,
                                 params: CloudClientParameters): AzureCloudClient {
        return createNewClient(state, params, arrayOf())
    }

    override fun createNewClient(state: CloudState,
                                 params: CloudClientParameters,
                                 errors: Array<TypedCloudErrorInfo>): AzureCloudClient {
        val tenantId = getParameter(params, AzureConstants.TENANT_ID)
        val clientId = getParameter(params, AzureConstants.CLIENT_ID)
        val clientSecret = getParameter(params, AzureConstants.CLIENT_SECRET)
        val subscriptionId = getParameter(params, AzureConstants.SUBSCRIPTION_ID)
        val environment = params.getParameter(AzureConstants.ENVIRONMENT)

        val apiConnector = AzureApiConnectorImpl(tenantId, clientId, clientSecret, environment)
        apiConnector.setSubscriptionId(subscriptionId)
        apiConnector.setServerId(mySettings.serverUUID)
        apiConnector.setProfileId(state.profileId)

        val azureCloudClient = AzureCloudClient(params, apiConnector, myImagesHolder)
        azureCloudClient.updateErrors(*errors)

        return azureCloudClient
    }

    private fun getParameter(params: CloudClientParameters, parameter: String): String {
        return params.getParameter(parameter) ?: throw RuntimeException(parameter + " must not be empty")
    }

    override fun parseImageData(params: CloudClientParameters): Collection<AzureCloudImageDetails> {
        if (!params.getParameter(CloudImageParameters.SOURCE_IMAGES_JSON).isNullOrEmpty()) {
            return AzureUtils.parseImageData(AzureCloudImageDetails::class.java, params)
        }

        return params.cloudImages.map {
            AzureCloudImageDetails(
                    it.id,
                    it.getParameter(AzureConstants.DEPLOY_TARGET)?.let {
                        AzureCloudDeployTarget.valueOf(it)
                    },
                    it.getParameter(AzureConstants.REGION),
                    it.getParameter(AzureConstants.GROUP_ID),
                    it.getParameter(AzureConstants.IMAGE_TYPE)?.let {
                        AzureCloudImageType.valueOf(it)
                    },
                    it.getParameter(AzureConstants.IMAGE_URL),
                    it.getParameter(AzureConstants.IMAGE_ID),
                    it.getParameter(AzureConstants.INSTANCE_ID),
                    it.getParameter(AzureConstants.OS_TYPE),
                    it.getParameter(AzureConstants.NETWORK_ID),
                    it.getParameter(AzureConstants.SUBNET_ID),
                    it.getParameter(AzureConstants.VM_NAME_PREFIX),
                    it.getParameter(AzureConstants.VM_SIZE),
                    (it.getParameter(AzureConstants.VM_PUBLIC_IP) ?: "").toBoolean(),
                    (it.getParameter(AzureConstants.MAX_INSTANCES_COUNT) ?: "1").toInt(),
                    it.getParameter(AzureConstants.VM_USERNAME),
                    it.getParameter(AzureConstants.STORAGE_ACCOUNT_TYPE),
                    it.getParameter(AzureConstants.TEMPLATE),
                    it.getParameter(AzureConstants.NUMBER_CORES),
                    it.getParameter(AzureConstants.MEMORY),
                    it.getParameter(AzureConstants.STORAGE_ACCOUNT),
                    it.agentPoolId,
                    it.getParameter(AzureConstants.PROFILE_ID),
                    (it.getParameter(AzureConstants.REUSE_VM) ?: "").toBoolean())
        }.apply {
            AzureUtils.setPasswords(AzureCloudImageDetails::class.java, params, this)
        }
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
                AzureConstants.MAX_INSTANCES_COUNT, AzureConstants.MAX_INSTANCES_COUNT,
                AzureConstants.VM_USERNAME, AzureConstants.VM_PASSWORD,
                CloudImageParameters.SOURCE_ID_FIELD)
    }
}
