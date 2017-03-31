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

package jetbrains.buildServer.clouds.base;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.base.beans.CloudImageDetails;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.errors.CloudErrorMap;
import jetbrains.buildServer.clouds.base.errors.DefaultErrorMessageUpdater;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.base.errors.UpdatableCloudErrorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:50 PM
 */
public abstract class AbstractCloudImage<T extends AbstractCloudInstance, G extends CloudImageDetails> implements CloudImage, UpdatableCloudErrorProvider {
  protected final UpdatableCloudErrorProvider myErrorProvider = new CloudErrorMap(new DefaultErrorMessageUpdater());
  private final Map<String, T> myInstances = new ConcurrentHashMap<>();
  private final String myName;
  private final String myId;

  protected AbstractCloudImage(String name, String id) {
    myName = name;
    myId = id;
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public void updateErrors(TypedCloudErrorInfo... errors) {
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
  public T findInstanceById(@NotNull final String instanceId) {
    return myInstances.get(instanceId);
  }

  public void removeInstance(@NotNull final String instanceId) {
    myInstances.remove(instanceId);
  }

  public void addInstance(@NotNull final T instance) {
    instance.setImage(this);
    myInstances.put(instance.getInstanceId(), instance);
  }

  public abstract boolean canStartNewInstance();

  public abstract void terminateInstance(@NotNull final T instance);

  public abstract void restartInstance(@NotNull final T instance);

  public abstract T startNewInstance(@NotNull final CloudInstanceUserData tag);

  public abstract G getImageDetails();

  protected abstract T createInstanceFromReal(final AbstractInstance realInstance);

  public void detectNewInstances(final Map<String, ? extends AbstractInstance> realInstances) {
    for (String instanceName : realInstances.keySet()) {
      if (myInstances.get(instanceName) == null) {
        final AbstractInstance realInstance = realInstances.get(instanceName);
        final T newInstance = createInstanceFromReal(realInstance);
        newInstance.setStatus(realInstance.getInstanceStatus());
        myInstances.put(instanceName, newInstance);
      }
    }
  }

  public String toString() {
    return getClass().getSimpleName() + "{" + "myName='" + getId() + '\'' + '}';
  }
}
