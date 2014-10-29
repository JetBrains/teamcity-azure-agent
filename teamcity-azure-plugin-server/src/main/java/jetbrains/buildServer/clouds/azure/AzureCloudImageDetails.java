/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package jetbrains.buildServer.clouds.azure;

import com.google.gson.annotations.SerializedName;
import jetbrains.buildServer.TeamCityRuntimeException;
import jetbrains.buildServer.clouds.base.beans.CloudImageDetails;
import jetbrains.buildServer.clouds.base.types.CloneBehaviour;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import jetbrains.buildServer.serverSide.crypt.RSACipher;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 8/1/2014
 *         Time: 4:45 PM
 */
public class AzureCloudImageDetails implements CloudImageDetails {

  @SerializedName("sourceName")
  private final String mySourceName;
  @SerializedName("serviceName")
  private final String myServiceName;
  @SerializedName("vmNamePrefix")
  private final String myVmNamePrefix;
  @SerializedName("osType")
  private final String myOsType;
  @SerializedName("vmSize")
  private final String myVmSize;
  @SerializedName("maxInstances")
  private final int myMaxInstances;
  @SerializedName("behaviour")
  private final CloneBehaviour myBehaviour;

  @SerializedName("username")
  private final String myUsername;

  private String myPassword = null;

  public AzureCloudImageDetails(@NotNull final CloneBehaviour cloneTypeName,
                                @Nullable final String serviceName,
                                @NotNull final String sourceName,
                                @Nullable final String vmNamePrefix,
                                @Nullable final String vmSize,
                                final int maxInstances,
                                @Nullable final String osType,
                                @Nullable final String username,
                                @Nullable final String password){
    myBehaviour = cloneTypeName;
    mySourceName = sourceName;
    myServiceName = serviceName;
    myVmNamePrefix = vmNamePrefix;
    myOsType = osType;
    myVmSize = vmSize;
    myUsername = username;
    myPassword = password;
    myMaxInstances = maxInstances;
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

  private void validateParams(){
    if (!myBehaviour.isUseOriginal()){
      check(StringUtil.isNotEmpty(myServiceName), "Service name is required");
      check(StringUtil.isNotEmpty(myVmNamePrefix), "Name prefix is required");
      check(StringUtil.isNotEmpty(myVmSize), "VM Size is required");
      check(StringUtil.isNotEmpty(myOsType), "Unable to determine OS Type");
      check(myMaxInstances>0, "Max instances is less than 1, no VMs of this Image will be started");
    }
  }

  private void check(boolean expression, String errorMessage){
    if (!expression){
      throw new TeamCityRuntimeException(errorMessage);
    }
  }
}
