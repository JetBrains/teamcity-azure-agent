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
import java.io.File;
import jetbrains.buildServer.clouds.base.beans.AbstractCloudImageDetails;
import jetbrains.buildServer.clouds.base.types.CloneBehaviour;

/**
 * @author Sergey.Pak
 *         Date: 8/1/2014
 *         Time: 4:45 PM
 */
public class AzureCloudImageDetails extends AbstractCloudImageDetails {

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
  @SerializedName("username")
  private final String myUsername;
  @SerializedName("password")
  private final String myPassword;
  @SerializedName("maxInstances")
  private final int myMaxInstances;
  @SerializedName("behaviour")
  private final CloneBehaviour myBehaviour;

  private transient File myImageIdxFile;


  public AzureCloudImageDetails(final CloneBehaviour cloneTypeName,
                                final String serviceName,
                                final String sourceName,
                                final String vmNamePrefix,
                                final String vmSize,
                                final int maxInstances,
                                final String osType,
                                final String username,
                                final String password){
    myBehaviour = cloneTypeName;
    mySourceName = sourceName;
    myServiceName = serviceName;
    myVmNamePrefix = vmNamePrefix;
    myOsType = osType;
    myVmSize = vmSize;
    myUsername = username;
    myPassword = password;
    myMaxInstances = maxInstances;

  }
  public String getSourceName() {
    return mySourceName;
  }

  public String getServiceName() {
    return myServiceName;
  }

  public String getOsType() {
    if (myOsType == null){
      throw new UnsupportedOperationException("Don't have enough data for this VM type");
    }
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

  public int getMaxInstances() {
    return myMaxInstances;
  }

  public CloneBehaviour getBehaviour() {
    return myBehaviour;
  }

  public File getImageIdxFile() {
    return myImageIdxFile;
  }

  public void setImageIdxFile(final File imageIdxFile) {
    myImageIdxFile = imageIdxFile;
  }
}
