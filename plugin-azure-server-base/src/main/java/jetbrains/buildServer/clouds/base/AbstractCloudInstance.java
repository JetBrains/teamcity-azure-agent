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
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudInstance;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.errors.CloudErrorMap;
import jetbrains.buildServer.clouds.base.errors.DefaultErrorMessageUpdater;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.base.errors.UpdatableCloudErrorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:51 PM
 */
public abstract class AbstractCloudInstance<T extends AbstractCloudImage> implements CloudInstance, UpdatableCloudErrorProvider {
    private static final Logger LOG = Logger.getInstance(AbstractCloudInstance.class.getName());

    private final UpdatableCloudErrorProvider myErrorProvider;
    protected InstanceStatus myStatus = InstanceStatus.UNKNOWN;

    @NotNull
    protected final T myImage;
    private Date myStartDate = new Date();
    private Date myStatusUpdateTime = new Date();
    private String myNetworkIdentify = null;
    private final String myName;
    private final String myInstanceId;

    protected AbstractCloudInstance(@NotNull final T image,
                                    @NotNull final String name,
                                    @NotNull final String instanceId) {
        myImage = image;
        myName = name;
        myInstanceId = instanceId;
        myErrorProvider = new CloudErrorMap(new DefaultErrorMessageUpdater(), "Unable to get instance details. See details");
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
        if (myStatus == status) {
            return;
        }
        LOG.info(String.format("Changing %s(%x) status from %s to %s ", getName(), hashCode(), myStatus, status));
        myStatus = status;
        myStatusUpdateTime = new Date();
    }

    @NotNull
    public Date getStartedTime() {
        return myStartDate;
    }

    public void setStartDate(final Date startDate) {
        if (startDate.after(myStartDate)) {
            myStartDate = startDate;
        } else if (startDate.before(myStartDate)) {
            LOG.debug(String.format("Attempted to set start date to %s from %s", startDate.toString(), myStartDate.toString()));
        }
    }

    public Date getStatusUpdateTime() {
        return myStatusUpdateTime;
    }

    public void setNetworkIdentify(final String networkIdentify) {
        myNetworkIdentify = networkIdentify;
    }

    @Nullable
    public String getNetworkIdentity() {
        return myNetworkIdentify;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + "myName='" + getInstanceId() + '\'' + '}';
    }
}
