/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.TeamCityRuntimeException;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.azure.IdProvider;
import jetbrains.buildServer.clouds.azure.connector.ActionIdChecker;
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.azure.arm.connector.AzureInstance;
import jetbrains.buildServer.clouds.azure.connector.ProvisionActionsQueue;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * @author Sergey.Pak
 *         Date: 7/31/2014
 *         Time: 5:18 PM
 */
public class AzureCloudImage extends AbstractCloudImage<AzureCloudInstance, AzureCloudImageDetails> {

    private static final Logger LOG = Logger.getInstance(AzureCloudImage.class.getName());

    private final AzureCloudImageDetails myImageDetails;
    private final ProvisionActionsQueue myActionsQueue;
    private final AzureApiConnector myApiConnector;
    private final IdProvider myIdProvider;

    protected AzureCloudImage(@NotNull final AzureCloudImageDetails imageDetails,
                              @NotNull final ProvisionActionsQueue actionsQueue,
                              @NotNull final AzureApiConnector apiConnector,
                              @NotNull final IdProvider idProvider) {
        super(imageDetails.getSourceName(), imageDetails.getSourceName());
        myImageDetails = imageDetails;
        myActionsQueue = actionsQueue;
        myApiConnector = apiConnector;
        myIdProvider = idProvider;

        if (StringUtil.isEmpty(imageDetails.getUsername()) || StringUtil.isEmpty(imageDetails.getPassword())) {
            throw new TeamCityRuntimeException("No credentials supplied for VM creation");
        }

        final Map<String, AzureInstance> instances = apiConnector.listImageInstances(this);
        for (AzureInstance azureInstance : instances.values()) {
            final AzureCloudInstance cloudInstance = new AzureCloudInstance(this, azureInstance.getName(), azureInstance.getName());
            cloudInstance.setStatus(azureInstance.getInstanceStatus());
            myInstances.put(azureInstance.getName(), cloudInstance);
        }
        if (myImageDetails.getBehaviour().isUseOriginal() && myInstances.size() != 1) {
            throw new TeamCityRuntimeException("Unable to find Azure Virtual Machine " + myImageDetails.getSourceName());
        }
    }

    public AzureCloudImageDetails getImageDetails() {
        return myImageDetails;
    }

    @Override
    public void detectNewInstances(final Map<String, AbstractInstance> realInstances) {
        for (String instanceName : realInstances.keySet()) {
            if (myInstances.get(instanceName) == null) {
                final AbstractInstance realInstance = realInstances.get(instanceName);
                final AzureCloudInstance newInstance = new AzureCloudInstance(this, instanceName);
                newInstance.setStatus(realInstance.getInstanceStatus());
                myInstances.put(instanceName, newInstance);
            }
        }
    }

    @Override
    public boolean canStartNewInstance() {
        if (myImageDetails.getBehaviour().isUseOriginal()) {
            final AzureCloudInstance singleInstance = myInstances.get(myImageDetails.getSourceName());
            return singleInstance != null && singleInstance.getStatus() == InstanceStatus.STOPPED;
        } else {
            return myInstances.size() < myImageDetails.getMaxInstances()
                    && myActionsQueue.isLocked(myImageDetails.getGroupId());
        }
    }

    @Override
    public void terminateInstance(@NotNull final AzureCloudInstance instance) {
        try {
            instance.setStatus(InstanceStatus.STOPPING);
            myActionsQueue.queueAction(myImageDetails.getGroupId(), new ProvisionActionsQueue.InstanceAction() {

                @NotNull
                public String getName() {
                    return "stop instance " + instance.getName();
                }

                @NotNull
                public String action() throws Exception {
                    return null;
                }

                @NotNull
                public ActionIdChecker getActionIdChecker() {
                    return myApiConnector;
                }

                public void onFinish() {
                }

                public void onError(final Throwable th) {
                    instance.setStatus(InstanceStatus.ERROR);
                    instance.updateErrors(Collections.singletonList(new TypedCloudErrorInfo(th.getMessage(), th.getMessage())));
                }
            });
        } catch (Exception e) {
            instance.setStatus(InstanceStatus.ERROR);
        }
    }

    @Override
    public void restartInstance(@NotNull final AzureCloudInstance instance) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AzureCloudInstance startNewInstance(@NotNull final CloudInstanceUserData tag) {
        return null;
    }
}
