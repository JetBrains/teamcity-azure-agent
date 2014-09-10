package jetbrains.buildServer.clouds.base;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.errors.CloudErrorMap;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.base.errors.UpdatableCloudErrorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:51 PM
 */
public abstract class AbstractCloudInstance<T extends AbstractCloudImage> implements CloudInstance, UpdatableCloudErrorProvider {

  private UpdatableCloudErrorProvider myErrorProvider;
  protected InstanceStatus myStatus = InstanceStatus.UNKNOWN;

  @NotNull
  protected final T myImage;
  private Date myStartDate = new Date();
  private String myNetworkIdentify = null;
  private final String myName;
  private final String myInstanceId;

  protected AbstractCloudInstance(@NotNull final T image, @NotNull final String name, @NotNull final String instanceId) {
    myImage = image;
    myName = name;
    myInstanceId = instanceId;
    myErrorProvider = new CloudErrorMap();
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getInstanceId() {
    return myInstanceId;
  }

  public void updateErrors(@Nullable final Collection<TypedCloudErrorInfo> errors) {
    myErrorProvider.updateErrors(errors);
  }

  @NotNull
  public T getImage() {
    return myImage;
  }

  @NotNull
  public String getImageId() {
    return myImage.getId();
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorProvider.getErrorInfo();
  }

  @NotNull
  public InstanceStatus getStatus() {
    return myStatus;
  }

  public void setStatus(@NotNull final InstanceStatus status) {
    myStatus = status;
  }

  @NotNull
  public Date getStartedTime() {
    return myStartDate;
  }

  public void setStartDate(final Date startDate) {
    myStartDate = startDate;
  }

  public void setNetworkIdentify(final String networkIdentify) {
    myNetworkIdentify = networkIdentify;
  }

  @Nullable
  public String getNetworkIdentity() {
    return null;
  }
}
