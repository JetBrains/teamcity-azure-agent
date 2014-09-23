package jetbrains.buildServer.clouds.azure;

import jetbrains.buildServer.clouds.base.beans.AbstractCloudImageDetails;
import jetbrains.buildServer.clouds.base.types.CloudCloneType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 8/1/2014
 *         Time: 4:45 PM
 */
public class AzureCloudImageDetails extends AbstractCloudImageDetails {

  private final String myImageName;
  private final String myDeploymentName;
  private final String myServiceName;
  private final String myVmNamePrefix;
  private final String myOsType;
  private final String myVmSize;
  private final String myUsername;
  private final String myPassword;
  private final CloudCloneType myCloneType;

  public static AzureCloudImageDetails fromString(@NotNull final String s){
    final String[] split = s.split(";");
    if (split.length == 4) {
      return new AzureCloudImageDetails(split[0], split[1], split[2], split[3]);
    } else if (split.length == 6) {
      return new AzureCloudImageDetails(split[0], split[1], split[2], split[3], split[4], split[5]);
    } else if (split.length == 9){
      return new AzureCloudImageDetails(split[0], split[1], split[2], split[3], split[4], split[5], split[6], split[7], split[8]);
    } else {
      return null;
    }
  }


  private AzureCloudImageDetails(final String cloneTypeName,
                                 final String serviceName,
                                 final String deploymentName,
                                 final String imageName,
                                 final String vmNamePrefix,
                                 final String vmSize,
                                 final String osType,
                                 final String username,
                                 final String password){
    myCloneType = CloudCloneType.valueOf(cloneTypeName);
    myImageName = imageName;
    myDeploymentName = deploymentName;
    myServiceName = serviceName;
    myVmNamePrefix = vmNamePrefix;
    myOsType = osType;
    myVmSize = vmSize;
    myUsername = username;
    myPassword = password;
  }
  private AzureCloudImageDetails(final String cloneTypeName,
                                 final String serviceName,
                                 final String deploymentName,
                                 final String imageName,
                                 final String vmNamePrefix,
                                 final String vmSize){
    this (cloneTypeName, serviceName, deploymentName, imageName, vmNamePrefix, vmSize, null, null, null);
  }
  private AzureCloudImageDetails(final String cloneTypeName,
                                 final String serviceName,
                                 final String deploymentName,
                                 final String imageName){
    this (cloneTypeName, serviceName, deploymentName, imageName, null, null, null, null, null);
  }

  public String getImageName() {
    return myImageName;
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

  public String getDeploymentName() {
    return myDeploymentName;
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

  public CloudCloneType getCloneType() {
    return myCloneType;
  }
}
