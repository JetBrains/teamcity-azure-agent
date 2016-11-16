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
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.QuotaException;
import jetbrains.buildServer.clouds.azure.AzureUtils;
import jetbrains.buildServer.clouds.azure.IdProvider;
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.azure.arm.connector.AzureInstance;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Azure cloud image.
 */
public class AzureCloudImage extends AbstractCloudImage<AzureCloudInstance, AzureCloudImageDetails> {

    private static final Logger LOG = Logger.getInstance(AzureCloudImage.class.getName());
    private final AzureCloudImageDetails myImageDetails;
    private final AzureApiConnector myApiConnector;
    private final IdProvider myIdProvider;

    AzureCloudImage(@NotNull final AzureCloudImageDetails imageDetails,
                    @NotNull final AzureApiConnector apiConnector,
                    @NotNull final IdProvider idProvider) {
        super(imageDetails.getSourceName(), imageDetails.getSourceName());
        myImageDetails = imageDetails;
        myApiConnector = apiConnector;
        myIdProvider = idProvider;

        final Map<String, AzureInstance> realInstances;
        try {
            realInstances = myApiConnector.fetchInstances(this);
        } catch (CheckedCloudException e) {
            final String message = String.format("Failed to get instances for image %s: %s", getName(), e.getMessage());
            LOG.warnAndDebugDetails(message, e);
            updateErrors(TypedCloudErrorInfo.fromException(e));
            return;
        }

        for (AzureInstance azureInstance : realInstances.values()) {
            AzureCloudInstance instance = createInstanceFromReal(azureInstance);
            instance.setStatus(azureInstance.getInstanceStatus());
            myInstances.put(instance.getInstanceId(), instance);
        }
    }

    public AzureCloudImageDetails getImageDetails() {
        return myImageDetails;
    }

    @Override
    protected AzureCloudInstance createInstanceFromReal(AbstractInstance realInstance) {
        return new AzureCloudInstance(this, realInstance.getName());
    }

    @Override
    public boolean canStartNewInstance() {
        return myInstances.size() < myImageDetails.getMaxInstances();
    }

    @Override
    public AzureCloudInstance startNewInstance(@NotNull final CloudInstanceUserData userData) {
        if (!canStartNewInstance()) {
            throw new QuotaException("Unable to start more instances. Limit reached");
        }

        final String name = myImageDetails.getVmNamePrefix().toLowerCase() + myIdProvider.getNextId();
        final AzureCloudInstance instance = new AzureCloudInstance(this, name);
        instance.setStatus(InstanceStatus.SCHEDULED_TO_START);

        final CloudInstanceUserData data = AzureUtils.setVmNameForTag(userData, name);
        myApiConnector.createVmAsync(instance, data).done(new DoneCallback<Void>() {
            @Override
            public void onDone(Void result) {
                LOG.info(String.format("Virtual machine %s has been successfully created", name));
            }
        }).fail(new FailCallback<Throwable>() {
            @Override
            public void onFail(Throwable result) {
                LOG.warn(result);
                instance.setStatus(InstanceStatus.ERROR);
                instance.updateErrors(TypedCloudErrorInfo.fromException(result));

                LOG.info("Removing allocated resources for virtual machine " + name);
                myApiConnector.deleteVmAsync(instance).done(new DoneCallback<Void>() {
                    @Override
                    public void onDone(Void result) {
                        LOG.info(String.format("Allocated resources for virtual machine %s have been removed", name));
                    }
                }).fail(new FailCallback<Throwable>() {
                    @Override
                    public void onFail(Throwable t) {
                        final String message = String.format("Failed to delete allocated resources for virtual machine %s: %s", name, t.getMessage());
                        LOG.warnAndDebugDetails(message, t);
                    }
                });
            }
        });

        myInstances.put(instance.getInstanceId(), instance);

        return instance;
    }

    @Override
    public void restartInstance(@NotNull final AzureCloudInstance instance) {
        instance.setStatus(InstanceStatus.RESTARTING);

        myApiConnector.restartVmAsync(instance).done(new DoneCallback<Void>() {
            @Override
            public void onDone(Void result) {
                LOG.info(String.format("Virtual machine %s has been successfully restarted", instance.getName()));
            }
        }).fail(new FailCallback<Throwable>() {
            @Override
            public void onFail(Throwable result) {
                LOG.warn(result);
                instance.setStatus(InstanceStatus.ERROR);
                instance.updateErrors(TypedCloudErrorInfo.fromException(result));
            }
        });
    }

    @Override
    public void terminateInstance(@NotNull final AzureCloudInstance instance) {
        instance.setStatus(InstanceStatus.SCHEDULED_TO_STOP);

        myApiConnector.deleteVmAsync(instance).done(new DoneCallback<Void>() {
            @Override
            public void onDone(Void result) {
                instance.setStatus(InstanceStatus.STOPPED);
                myInstances.remove(instance.getInstanceId());
                LOG.info(String.format("Virtual machine %s has been successfully stopped", instance.getName()));
            }
        }).fail(new FailCallback<Throwable>() {
            @Override
            public void onFail(Throwable result) {
                LOG.warn(result);
                instance.setStatus(InstanceStatus.ERROR);
                instance.updateErrors(TypedCloudErrorInfo.fromException(result));
            }
        });
    }

    @Nullable
    @Override
    public Integer getAgentPoolId() {
        return myImageDetails.getAgentPoolId();
    }
}
