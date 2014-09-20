package jetbrains.buildServer.clouds.base;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.base.errors.CloudErrorMap;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.base.errors.UpdatableCloudErrorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:50 PM
 */
public abstract class AbstractCloudImage<T extends AbstractCloudInstance> implements CloudImage, UpdatableCloudErrorProvider {
  protected final UpdatableCloudErrorProvider myErrorProvider;
  protected final Map<String, T> myInstances;
  private final String myName;
  private final String myId;

  protected AbstractCloudImage(String name, String id) {
    myName = name;
    myId = id;
    myErrorProvider = new CloudErrorMap();
    myInstances = new HashMap<String, T>();
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public void updateErrors(@Nullable final Collection<TypedCloudErrorInfo> errors) {
    myErrorProvider.updateErrors(errors);
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorProvider.getErrorInfo();
  }

  @NotNull
  public Collection<T> getInstances() {
    return Collections.unmodifiableCollection(myInstances.values());
  }

  @Nullable
  public T findInstanceById(@NotNull final String id) {
    return myInstances.get(id);
  }

  public void removeInstance(@NotNull final String instanceName){
    myInstances.remove(instanceName);
  }

  public abstract boolean canStartNewInstance();

  public abstract void terminateInstance(@NotNull final T instance);

  public abstract void restartInstance(@NotNull final T instance);

  public abstract T startNewInstance(@NotNull final CloudInstanceUserData tag);

}
