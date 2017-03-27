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

package jetbrains.buildServer.clouds.azure.asm;

import com.google.gson.annotations.SerializedName;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.azure.asm.web.AzureWebConstants;
import jetbrains.buildServer.clouds.base.beans.CloudImagePasswordDetails;
import jetbrains.buildServer.clouds.base.types.CloneBehaviour;
import jetbrains.buildServer.util.StringUtil;

/**
 * @author Sergey.Pak
 *         Date: 8/1/2014
 *         Time: 4:45 PM
 */
public class AzureCloudImageDetails implements CloudImagePasswordDetails {

    @SerializedName(CloudImageParameters.SOURCE_ID_FIELD)
    private String mySourceId;
    @SerializedName(AzureWebConstants.SOURCE_NAME)
    private String mySourceName;
    @SerializedName(AzureWebConstants.SERVICE_NAME)
    private String myServiceName;
    @SerializedName(AzureWebConstants.NAME_PREFIX)
    private String myVmNamePrefix;
    @SerializedName(AzureWebConstants.VNET_NAME)
    private String myVnetName;
    @SerializedName(AzureWebConstants.OS_TYPE)
    private String myOsType;
    @SerializedName(AzureWebConstants.VM_SIZE)
    private String myVmSize;
    @SerializedName(AzureWebConstants.MAX_INSTANCES_COUNT)
    private int myMaxInstances;
    @SerializedName(AzureWebConstants.BEHAVIOUR)
    private CloneBehaviour myBehaviour;
    @SerializedName(AzureWebConstants.PROVISION_USERNAME)
    private String myUsername;
    @SerializedName(AzureWebConstants.PUBLIC_IP)
    private boolean myPublicIp;
    @SerializedName(CloudImageParameters.AGENT_POOL_ID_FIELD)
    private Integer myAgentPoolId;
    @SerializedName(AzureWebConstants.PROFILE_ID)
    private String myProfileId;

    private String myPassword = null;

    public String getSourceName() {
        return StringUtil.notEmpty(mySourceId, StringUtil.emptyIfNull(mySourceName));
    }

    public String getServiceName() {
        return myServiceName;
    }

    public String getOsType() {
        return myOsType;
    }

    public String getVmSize() {
        return myVmSize;
    }

    public String getVmNamePrefix() {
        return myVmNamePrefix;
    }

    public String getVnetName() {
        return myVnetName;
    }

    public String getUsername() {
        return myUsername;
    }

    public String getPassword() {
        return myPassword;
    }

    public void setPassword(final String password) {
        myPassword = password;
    }

    public int getMaxInstances() {
        return myMaxInstances;
    }

    public CloneBehaviour getBehaviour() {
        return myBehaviour;
    }

    public boolean getPublicIp() {
        return myPublicIp;
    }

    public Integer getAgentPoolId() {
        return myAgentPoolId;
    }

    public String getProfileId() {
        return myProfileId;
    }
}
