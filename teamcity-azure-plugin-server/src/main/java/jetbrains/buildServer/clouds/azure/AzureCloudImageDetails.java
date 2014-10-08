package jetbrains.buildServer.clouds.azure;

import java.io.File;
import jetbrains.buildServer.clouds.base.beans.AbstractCloudImageDetails;
import jetbrains.buildServer.clouds.base.types.CloudCloneType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private final int myMaxInstancesCount;
  private final CloudCloneType myCloneType;
  private final File myImageIdxFile;


  @Nullable
  public static AzureCloudImageDetails fromString(@NotNull final String s, @NotNull final File azureStorage){
    final String[] split = s.split(";");
    if (split.length != 8 && split.length != 10){
      return null;
    }
    final CloudCloneType cloudCloneType = CloudCloneType.valueOf(split[0]);
    if (cloudCloneType.isUseOriginal()) {
      return new AzureCloudImageDetails(cloudCloneType, azureStorage, split[1], split[2], split[3]);
    } else {
      int maxInstancesCount = Integer.parseInt(split[6]);

      if (split.length == 8) {
        return new AzureCloudImageDetails(cloudCloneType, azureStorage, split[1], split[2], split[3], split[4], split[5], maxInstancesCount);
      } else if (split.length == 10) {
        return new AzureCloudImageDetails(cloudCloneType, azureStorage, split[1], split[2], split[3], split[4], split[5], maxInstancesCount, split[7], split[8], split[9]);
      } else {
        return null;
      }
    }
  }


  private AzureCloudImageDetails(final CloudCloneType cloneTypeName,
                                 final File azureStorage,
                                 final String serviceName,
                                 final String deploymentName,
                                 final String imageName,
                                 final String vmNamePrefix,
                                 final String vmSize,
                                 final int maxInstancesCount,
                                 final String osType,
                                 final String username,
                                 final String password){
    myCloneType = cloneTypeName;
    myImageName = imageName;
    myDeploymentName = deploymentName;
    myServiceName = serviceName;
    myVmNamePrefix = vmNamePrefix;
    myOsType = osType;
    myVmSize = vmSize;
    myUsername = username;
    myPassword = password;
    myMaxInstancesCount = maxInstancesCount;
    myImageIdxFile = new File(azureStorage, imageName + ".idx");
  }
  private AzureCloudImageDetails(final CloudCloneType cloneTypeName,
                                 final File azureStorage,
                                 final String serviceName,
                                 final String deploymentName,
                                 final String imageName,
                                 final String vmNamePrefix,
                                 final String vmSize,
                                 final int maxInstancesCount){
    this (cloneTypeName, azureStorage, serviceName, deploymentName, imageName, vmNamePrefix, vmSize, maxInstancesCount, null, null, null);
  }
  private AzureCloudImageDetails(final CloudCloneType cloneTypeName,
                                 final File azureStorage,
                                 final String serviceName,
                                 final String deploymentName,
                                 final String imageName){
    this (cloneTypeName, azureStorage, serviceName, deploymentName, imageName, null, null, 1, null, null, null);
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

  public int getMaxInstancesCount() {
    return myMaxInstancesCount;
  }

  public CloudCloneType getCloneType() {
    return myCloneType;
  }

  public File getImageIdxFile() {
    return myImageIdxFile;
  }
}
