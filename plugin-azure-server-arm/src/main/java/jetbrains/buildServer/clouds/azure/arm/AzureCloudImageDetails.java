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

package jetbrains.buildServer.clouds.azure.arm;

import com.google.gson.annotations.SerializedName;
import jetbrains.buildServer.clouds.base.beans.CloudImagePasswordDetails;
import jetbrains.buildServer.clouds.base.types.CloneBehaviour;
import org.jetbrains.annotations.NotNull;

/**
 * ARM cloud image details.
 */
public class AzureCloudImageDetails implements CloudImagePasswordDetails {

    @SerializedName(AzureConstants.GROUP_ID)
    private final String myGroupId;
    @SerializedName(AzureConstants.STORAGE_ID)
    private final String myStorageId;
    @SerializedName(AzureConstants.NETWORK_ID)
    private final String myNetworkId;
    @SerializedName(AzureConstants.SUBNET_ID)
    private final String mySubnetId;
    @SerializedName(AzureConstants.IMAGE_PATH)
    private final String myImagePath;
    @SerializedName(AzureConstants.OS_TYPE)
    private final String myOsType;
    @SerializedName(AzureConstants.MAX_INSTANCES_COUNT)
    private final int myMaxInstances;
    @SerializedName(AzureConstants.VM_SIZE)
    private final String myVmSize;
    @SerializedName(AzureConstants.VM_NAME_PREFIX)
    private final String myVmNamePrefix;
    @SerializedName(AzureConstants.VM_PUBLIC_IP)
    private final boolean myVmPublicIp;
    @SerializedName(AzureConstants.VM_USERNAME)
    private final String myUsername;
    private String myPassword = null;

    public AzureCloudImageDetails(@NotNull final String groupId,
                                  @NotNull final String storageId,
                                  @NotNull final String networkId,
                                  @NotNull final String subnetId,
                                  @NotNull final String imagePath,
                                  @NotNull final String osType,
                                  @NotNull final String vmNamePrefix,
                                  @NotNull final String vmSize,
                                  final boolean vmPublicIp,
                                  final int maxInstances,
                                  @NotNull final String username) {
        myGroupId = groupId;
        myStorageId = storageId;
        myNetworkId = networkId;
        mySubnetId = subnetId;
        myImagePath = imagePath;
        myOsType = osType;
        myVmNamePrefix = vmNamePrefix;
        myVmSize = vmSize;
        myVmPublicIp = vmPublicIp;
        myMaxInstances = maxInstances;
        myUsername = username;
    }

    public String getGroupId() {
        return myGroupId;
    }

    public String getStorageId() {
        return myStorageId;
    }

    public String getNetworkId() {
        return myNetworkId;
    }

    public String getSubnetId() {
        return mySubnetId;
    }

    public String getImagePath() {
        return myImagePath;
    }

    public String getOsType() {
        return myOsType;
    }

    public String getVmNamePrefix() {
        return myVmNamePrefix;
    }

    public String getVmSize() {
        return myVmSize;
    }

    public boolean getVmPublicIp() {
        return myVmPublicIp;
    }

    public int getMaxInstances() {
        return myMaxInstances;
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

    @Override
    public String getSourceName() {
        return String.format("%s/%s", myGroupId, myVmNamePrefix);
    }

    public CloneBehaviour getBehaviour() {
        return CloneBehaviour.FRESH_CLONE;
    }
}
