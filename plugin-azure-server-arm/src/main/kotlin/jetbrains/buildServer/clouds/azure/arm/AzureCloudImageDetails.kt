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

import com.google.gson.annotations.SerializedName
import jetbrains.buildServer.clouds.CloudImageParameters
import jetbrains.buildServer.clouds.base.beans.CloudImagePasswordDetails
import jetbrains.buildServer.clouds.base.types.CloneBehaviour

/**
 * ARM cloud image details.
 */
class AzureCloudImageDetails(
        @SerializedName(AzureConstants.IMAGE_URL)
        val imageUrl: String,
        @SerializedName(AzureConstants.OS_TYPE)
        val osType: String,
        @SerializedName(AzureConstants.NETWORK_ID)
        val networkId: String,
        @SerializedName(AzureConstants.SUBNET_ID)
        val subnetId: String,
        @SerializedName(AzureConstants.VM_NAME_PREFIX)
        val vmNamePrefix: String,
        @SerializedName(AzureConstants.VM_SIZE)
        val vmSize: String,
        @SerializedName(AzureConstants.VM_PUBLIC_IP)
        val vmPublicIp: Boolean,
        @SerializedName(AzureConstants.MAX_INSTANCES_COUNT)
        private val myMaxInstances: Int,
        @SerializedName(AzureConstants.VM_USERNAME)
        val username: String,
        @SerializedName(CloudImageParameters.AGENT_POOL_ID_FIELD)
        val agentPoolId: Int?,
        @SerializedName(AzureConstants.REUSE_VM)
        private val myReuseVm: Boolean) : CloudImagePasswordDetails {

    private var myPassword: String? = null

    override fun getMaxInstances(): Int {
        return myMaxInstances
    }

    override fun getPassword(): String? {
        return myPassword
    }

    override fun setPassword(password: String) {
        myPassword = password
    }

    override fun getSourceName(): String {
        return vmNamePrefix
    }

    override fun getBehaviour(): CloneBehaviour {
        return if (myReuseVm) CloneBehaviour.ON_DEMAND_CLONE else CloneBehaviour.FRESH_CLONE
    }
}
