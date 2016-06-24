/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import jetbrains.buildServer.TeamCityRuntimeException;
import jetbrains.buildServer.clouds.azure.asm.web.AzureWebConstants;
import jetbrains.buildServer.clouds.base.beans.CloudImagePasswordDetails;
import jetbrains.buildServer.clouds.base.types.CloneBehaviour;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 8/1/2014
 *         Time: 4:45 PM
 */
public class AzureCloudImageDetails implements CloudImagePasswordDetails {

    @SerializedName(AzureWebConstants.SOURCE_NAME)
    private final String mySourceName;
    @SerializedName(AzureWebConstants.SERVICE_NAME)
    private final String myServiceName;
    @SerializedName(AzureWebConstants.NAME_PREFIX)
    private final String myVmNamePrefix;
    @SerializedName(AzureWebConstants.VNET_NAME)
    private final String myVnetName;
    @SerializedName(AzureWebConstants.OS_TYPE)
    private final String myOsType;
    @SerializedName(AzureWebConstants.VM_SIZE)
    private final String myVmSize;
    @SerializedName(AzureWebConstants.MAX_INSTANCES_COUNT)
    private final int myMaxInstances;
    @SerializedName(AzureWebConstants.BEHAVIOUR)
    private final CloneBehaviour myBehaviour;
    @SerializedName(AzureWebConstants.PROVISION_USERNAME)
    private final String myUsername;
    @SerializedName(AzureWebConstants.PUBLIC_IP)
    private final boolean myPublicIp;

    private String myPassword = null;

    public AzureCloudImageDetails(@NotNull final CloneBehaviour cloneTypeName,
                                  @Nullable final String serviceName,
                                  @NotNull final String sourceName,
                                  @Nullable final String vmNamePrefix,
                                  @Nullable final String vnetName,
                                  @Nullable final String vmSize,
                                  final int maxInstances,
                                  @Nullable final String osType,
                                  @Nullable final String username,
                                  @Nullable final String password,
                                  final boolean publicIp) {
        myBehaviour = cloneTypeName;
        mySourceName = sourceName;
        myServiceName = serviceName;
        myVmNamePrefix = vmNamePrefix;
        myVnetName = vnetName;
        myOsType = osType;
        myVmSize = vmSize;
        myUsername = username;
        myPassword = password;
        myMaxInstances = maxInstances;
        myPublicIp = publicIp;
        validateParams();
    }

    public String getSourceName() {
        return mySourceName;
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

    private void validateParams() {
        if (!myBehaviour.isUseOriginal()) {
            check(StringUtil.isNotEmpty(myServiceName), "Service name is required");
            check(StringUtil.isNotEmpty(myVmNamePrefix), "Name prefix is required");
            check(StringUtil.isNotEmpty(myVmSize), "VM Size is required");
            check(StringUtil.isNotEmpty(myOsType), "Unable to determine OS Type");
            check(myMaxInstances > 0, "Max instances is less than 1, no VMs of this Image will be started");
        }
    }

    private void check(boolean expression, String errorMessage) {
        if (!expression) {
            throw new TeamCityRuntimeException(errorMessage);
        }
    }
}
