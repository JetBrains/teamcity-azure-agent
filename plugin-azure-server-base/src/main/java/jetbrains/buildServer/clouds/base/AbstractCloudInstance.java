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

import com.intellij.openapi.diagnostic.Logger;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.errors.CloudErrorMap;
import jetbrains.buildServer.clouds.base.errors.DefaultErrorMessageUpdater;
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
  private static final Logger LOG = Logger.getInstance(AbstractCloudInstance.class.getName());
  private static final AtomicInteger STARTING_INSTANCE_IDX = new AtomicInteger(0);

  private final UpdatableCloudErrorProvider myErrorProvider;
  private final AtomicReference<InstanceStatus> myStatus = new AtomicReference<>(InstanceStatus.UNKNOWN);

  @NotNull
  private T myImage;
  private final AtomicReference<Date> myStartDate = new AtomicReference<Date>(new Date());
  private final AtomicReference<Date> myStatusUpdateTime = new AtomicReference<>(new Date());
  private final AtomicReference<String> myNetworkIdentify = new AtomicReference<String>();

  private volatile String myName;
  private volatile String myInstanceId;

  protected AbstractCloudInstance(@NotNull final T image) {
    this(image, "Initializing...", String.format("%s-%d", image.getName(), STARTING_INSTANCE_IDX.incrementAndGet()));
  }

  protected AbstractCloudInstance(@NotNull final T image, @NotNull final String name, @NotNull final String instanceId) {
    myImage = image;
    myName = name;
    myInstanceId = instanceId;
    myErrorProvider = new CloudErrorMap(new DefaultErrorMessageUpdater());
  }

  public void setName(@NotNull final String name) {
    myName = name;
  }

  public void setInstanceId(@NotNull final String instanceId) {
    myImage.removeInstance(myInstanceId);
    myInstanceId = instanceId;
    myImage.addInstance(this);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getInstanceId() {
    return myInstanceId;
  }


  public void updateErrors(TypedCloudErrorInfo... errors) {
    myErrorProvider.updateErrors(errors);
  }

  @NotNull
  public T getImage() {
    return myImage;
  }

  public void setImage(@NotNull final T image) {
    myImage = image;
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
    return myStatus.get();
  }

  public void setStatus(@NotNull final InstanceStatus status) {
    if (myStatus.get() == status) {
      return;
    }
    LOG.info(String.format("Changing %s(%x) status from %s to %s ", getName(), hashCode(), myStatus, status));
    myStatus.set(status);
    myStatusUpdateTime.set(new Date());
  }

  @NotNull
  public Date getStartedTime() {
    return myStartDate.get();
  }

  public void setStartDate(@NotNull final Date startDate) {
    if (startDate.after(myStartDate.get())) {
      myStartDate.set(startDate);
    } else if (startDate.before(myStartDate.get())) {
      LOG.debug(String.format("Attempted to set start date to %s from %s", startDate.toString(), myStartDate.get().toString()));
    }
  }

  @NotNull
  public Date getStatusUpdateTime() {
    return myStatusUpdateTime.get();
  }

  public void setNetworkIdentify(@NotNull final String networkIdentify) {
    myNetworkIdentify.set(networkIdentify);
  }

  @Nullable
  public String getNetworkIdentity() {
    return myNetworkIdentify.get();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + "myName='" + getInstanceId() + '\'' + '}';
  }
}
