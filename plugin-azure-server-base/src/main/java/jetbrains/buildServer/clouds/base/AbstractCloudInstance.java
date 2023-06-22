/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

  @NotNull
  private T myImage;

  private volatile String myName;
  private volatile String myInstanceId;

  @NotNull
  private final AtomicReference<InstanceState> myState = new AtomicReference<InstanceState>(new InstanceState());

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
    return myState.get().getStatus();
  }

  public Boolean getProvisioningInProgress() { return myState.get().getProvisioningInProgress(); }

  public void setStatus(@NotNull final InstanceStatus status) {
    myState.updateAndGet(state -> {
      if (state.getStatus() == status) {
        return state;
      }

      LOG.info(String.format("Changing %s(%x) status from %s to %s ", getName(), hashCode(), state.getStatus(), status));

      InstanceState result = state.withStatus(status);
      if (status == InstanceStatus.RUNNING) {
        result = result.withStartDate(new Date());
      }
      return result;
    });
  }

  public Boolean compareAndSetStatus(@NotNull final InstanceStatus expected, @NotNull final InstanceStatus newStatus) {
    final InstanceState state = myState.get();
    if (state.getStatus() != expected) return false;

    InstanceState newState = state.withStatus(newStatus);
    if (newStatus == InstanceStatus.RUNNING) {
      newState = newState.withStartDate(new Date());
    }

    return myState.compareAndSet(state, newState);
  }

  @NotNull
  public Date getStartedTime() {
    final InstanceState state = myState.get();
    if (state.getStatus().isStartingOrStarted() && state.getStatus() != InstanceStatus.RUNNING) {
      return new Date();
    }
    return state.getStartDate();
  }

  public void setStartDate(@NotNull final Date startDate) {
    myState.updateAndGet(state -> {
      if (startDate.after(state.getStartDate())) {
        return state.withStartDate(startDate);
      }
      if (startDate.before(state.getStartDate())) {
        LOG.debug(String.format("Attempted to set start date to %s from %s for %s(%x)", startDate, state.getStartDate(), getName(), hashCode()));
      }
      return state;
    });
  }

  @NotNull
  public Date getStatusUpdateTime() {
    return myState.get().getStatusUpdateTime();
  }

  public void setNetworkIdentify(@NotNull final String networkIdentify) {
    myState.updateAndGet(state -> state.withNetworkIdentity(networkIdentify));
  }

  public void setProvisioningInProgress(@NotNull final Boolean provisioningInProgress) {
    myState.updateAndGet(state -> state.withProvisioningInProgress(provisioningInProgress));
  }

  @Nullable
  public String getNetworkIdentity() {
    return myState.get().getNetworkIdentify();
  }

  public abstract boolean canBeCollected();

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + "myName='" + getInstanceId() + '\'' + '}';
  }

  public String describe() { return String.format("%s(%x)", getName(), hashCode()); }

  protected void setInstanceState(@NotNull final InstanceState state) {
    myState.set(state);
  }

  protected class InstanceState {
    @NotNull
    private Date myStartDate = new Date();

    @NotNull
    private Date myStatusUpdateTime = new Date();

    @Nullable
    private String myNetworkIdentify = null;

    @NotNull
    private InstanceStatus myStatus = InstanceStatus.UNKNOWN;

    @NotNull
    private Boolean myProvisioningInProgress = false;

    public InstanceState() {
    }

    private InstanceState(InstanceState state) {
      myStartDate = state.myStartDate;
      myStatusUpdateTime = state.myStatusUpdateTime;
      myNetworkIdentify = state.myNetworkIdentify;
      myStatus = state.myStatus;
      myProvisioningInProgress = state.myProvisioningInProgress;
    }

    @NotNull
    public Date getStartDate() {
      return myStartDate;
    }

    @NotNull
    public Date getStatusUpdateTime() {
      return myStatusUpdateTime;
    }

    @Nullable
    public String getNetworkIdentify() {
      return myNetworkIdentify;
    }

    @NotNull
    public InstanceStatus getStatus() {
      return myStatus;
    }

    @NotNull
    public Boolean getProvisioningInProgress() { return myProvisioningInProgress; }

    @NotNull
    public InstanceState withStatus(@NotNull final InstanceStatus status) {
      final InstanceState result = new InstanceState(this);
      result.myStatus = status;
      result.myStatusUpdateTime = new Date();
      return result;
    }

    @NotNull
    public InstanceState withStartDate(@NotNull final Date startDate) {
      final InstanceState result = new InstanceState(this);
      result.myStartDate = startDate;
      return result;
    }

    @NotNull
    public InstanceState withNetworkIdentity(@Nullable final String networkIdentity) {
      final InstanceState result = new InstanceState(this);
      result.myNetworkIdentify = networkIdentity;
      return result;
    }

    @NotNull
    public InstanceState withProvisioningInProgress(@Nullable final Boolean provisioningInProgress) {
      final InstanceState result = new InstanceState(this);
      result.myProvisioningInProgress = provisioningInProgress;
      return result;
    }
  }
}
