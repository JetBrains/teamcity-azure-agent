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

package jetbrains.buildServer.clouds.azure.arm

import com.google.gson.annotations.SerializedName
import jetbrains.buildServer.clouds.CloudImageParameters
import jetbrains.buildServer.clouds.base.beans.CloudImagePasswordDetails
import jetbrains.buildServer.clouds.base.types.CloneBehaviour

/**
 * ARM cloud image details.
 */
class AzureCloudImageDetails(
        @SerializedName(CloudImageParameters.SOURCE_ID_FIELD)
        val mySourceId: String? = null,
        @SerializedName(AzureConstants.DEPLOY_TARGET)
        val deployTarget: AzureCloudDeployTarget?,
        @SerializedName(AzureConstants.REGION)
        private var regionId: String?,
        @SerializedName(AzureConstants.GROUP_ID)
        val groupId: String?,
        @SerializedName(AzureConstants.IMAGE_TYPE)
        val imageType: AzureCloudImageType?,
        @SerializedName(AzureConstants.IMAGE_URL)
        val imageUrl: String?,
        @SerializedName(AzureConstants.IMAGE_ID)
        val imageId: String?,
        @SerializedName(AzureConstants.INSTANCE_ID)
        val instanceId: String?,
        @SerializedName(AzureConstants.OS_TYPE)
        val osType: String?,
        @SerializedName(AzureConstants.NETWORK_ID)
        val networkId: String?,
        @SerializedName(AzureConstants.SUBNET_ID)
        val subnetId: String?,
        @SerializedName(AzureConstants.VM_NAME_PREFIX)
        val vmNamePrefix: String? = null,
        @SerializedName(AzureConstants.VM_SIZE)
        val vmSize: String?,
        @SerializedName(AzureConstants.VM_PUBLIC_IP)
        val vmPublicIp: Boolean?,
        @SerializedName(AzureConstants.MAX_INSTANCES_COUNT)
        private val myMaxInstances: Int,
        @SerializedName(AzureConstants.VM_USERNAME)
        val username: String?,
        @SerializedName(AzureConstants.STORAGE_ACCOUNT_TYPE)
        val storageAccountType: String?,
        @SerializedName(AzureConstants.TEMPLATE)
        val template: String?,
        @SerializedName(AzureConstants.NUMBER_CORES)
        val numberCores: String?,
        @SerializedName(AzureConstants.MEMORY)
        val memory: String?,
        @SerializedName(AzureConstants.STORAGE_ACCOUNT)
        val storageAccount: String?,
        @SerializedName(AzureConstants.REGISTRY_USERNAME)
        val registryUsername: String?,
        @SerializedName(CloudImageParameters.AGENT_POOL_ID_FIELD)
        val agentPoolId: Int?,
        @SerializedName(AzureConstants.PROFILE_ID)
        val profileId: String?,
        @SerializedName(AzureConstants.REUSE_VM)
        private val myReuseVm: Boolean,
        @SerializedName(AzureConstants.TEMPLATE_TAGS_AS_PARAMETERS)
        val templateTagsAsParameters: Boolean,
        @SerializedName(AzureConstants.CUSTOM_ENVIRONMENT_VARIABLES)
        val customEnvironmentVariables: String? = null) : CloudImagePasswordDetails {

    private var myPassword: String? = null

    override fun getMaxInstances(): Int = myMaxInstances

    override fun getPassword(): String? = myPassword

    override fun setPassword(password: String) {
        myPassword = password
    }

    override fun getSourceId(): String = mySourceId ?: vmNamePrefix ?: ""

    override fun getBehaviour(): CloneBehaviour {
        return if (target == AzureCloudDeployTarget.Instance) {
            CloneBehaviour.START_STOP
        } else {
            if (myReuseVm) CloneBehaviour.ON_DEMAND_CLONE else CloneBehaviour.FRESH_CLONE
        }
    }

    val target
        get(): AzureCloudDeployTarget = deployTarget ?: AzureCloudDeployTarget.NewGroup

    val type
        get(): AzureCloudImageType = imageType ?: AzureCloudImageType.Vhd

    var region: String?
        get() = regionId
        set(value) {
            regionId = value
        }
}
