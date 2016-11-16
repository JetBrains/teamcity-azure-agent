/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.asm;

import com.intellij.openapi.diagnostic.Logger;
import com.microsoft.windowsazure.core.OperationResponse;
import com.microsoft.windowsazure.core.OperationStatus;
import com.microsoft.windowsazure.core.OperationStatusResponse;
import com.microsoft.windowsazure.exception.ServiceException;
import jetbrains.buildServer.TeamCityRuntimeException;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.azure.AzureUtils;
import jetbrains.buildServer.clouds.azure.IdProvider;
import jetbrains.buildServer.clouds.azure.asm.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.azure.asm.connector.AzureInstance;
import jetbrains.buildServer.clouds.azure.connector.ActionIdChecker;
import jetbrains.buildServer.clouds.azure.connector.ProvisionActionsQueue;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Date;
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
    private boolean myGeneralized;

    AzureCloudImage(@NotNull final AzureCloudImageDetails imageDetails,
                    @NotNull final ProvisionActionsQueue actionsQueue,
                    @NotNull final AzureApiConnector apiConnector,
                    @NotNull final IdProvider idProvider) {
        super(imageDetails.getSourceName(), imageDetails.getSourceName());
        myImageDetails = imageDetails;
        myActionsQueue = actionsQueue;
        myApiConnector = apiConnector;
        myIdProvider = idProvider;

        myGeneralized = !myImageDetails.getBehaviour().isUseOriginal() && apiConnector.isImageGeneralized(imageDetails.getSourceName());
        if (myGeneralized) {
            if (StringUtil.isEmpty(imageDetails.getUsername()) || StringUtil.isEmpty(imageDetails.getPassword())) {
                throw new TeamCityRuntimeException("No credentials supplied for VM creation");
            }
        }

        final Map<String, AzureInstance> instances = apiConnector.listImageInstances(this);
        for (AzureInstance azureInstance : instances.values()) {
            final AzureCloudInstance instance = createInstanceFromReal(azureInstance);
            instance.setStatus(azureInstance.getInstanceStatus());
            myInstances.put(instance.getInstanceId(), instance);
        }
        if (myImageDetails.getBehaviour().isUseOriginal() && myInstances.size() != 1) {
            throw new TeamCityRuntimeException("Unable to find Azure Virtual Machine " + myImageDetails.getSourceName());
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
        if (myImageDetails.getBehaviour().isUseOriginal()) {
            final AzureCloudInstance singleInstance = myInstances.get(myImageDetails.getSourceName());
            return singleInstance != null && singleInstance.getStatus() == InstanceStatus.STOPPED;
        } else {
            return myInstances.size() < myImageDetails.getMaxInstances()
                    && myActionsQueue.isLocked(myImageDetails.getServiceName());
        }
    }

    @Override
    public void terminateInstance(@NotNull final AzureCloudInstance instance) {
        try {
            instance.setStatus(InstanceStatus.STOPPING);
            myActionsQueue.queueAction(myImageDetails.getServiceName(), new ProvisionActionsQueue.InstanceAction() {
                private String myRequestId;

                @NotNull
                public String getName() {
                    return "stop instance " + instance.getName();
                }

                @NotNull
                public String action() throws ServiceException, IOException {
                    final OperationResponse operationResponse = myApiConnector.stopVM(instance);
                    myRequestId = operationResponse.getRequestId();
                    return myRequestId;
                }

                @NotNull
                public ActionIdChecker getActionIdChecker() {
                    return myApiConnector;
                }

                public void onFinish() {
                    try {
                        final OperationStatusResponse statusResponse = myApiConnector.getOperationStatus(myRequestId);
                        instance.setStatus(InstanceStatus.STOPPED);
                        if (statusResponse.getStatus() == OperationStatus.Succeeded) {
                            if (myImageDetails.getBehaviour().isDeleteAfterStop()) {
                                deleteInstance(instance);
                            }
                        } else if (statusResponse.getStatus() == OperationStatus.Failed) {
                            instance.setStatus(InstanceStatus.ERROR_CANNOT_STOP);
                            final OperationStatusResponse.ErrorDetails error = statusResponse.getError();
                            instance.updateErrors(new TypedCloudErrorInfo(error.getCode(), error.getMessage()));
                        }
                    } catch (Exception e) {
                        instance.setStatus(InstanceStatus.ERROR_CANNOT_STOP);
                        instance.updateErrors(new TypedCloudErrorInfo(e.getMessage(), e.toString()));
                    }
                }

                public void onError(final Throwable th) {
                    instance.setStatus(InstanceStatus.ERROR);
                    instance.updateErrors(new TypedCloudErrorInfo(th.getMessage(), th.getMessage()));
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

    private void deleteInstance(@NotNull final AzureCloudInstance instance) {
        myActionsQueue.queueAction(myImageDetails.getServiceName(), new ProvisionActionsQueue.InstanceAction() {
            private String myRequestId;

            @NotNull
            public String getName() {
                return "delete instance " + instance.getName();
            }

            @NotNull
            public String action() throws ServiceException, IOException {
                final OperationResponse operationResponse = myApiConnector.deleteVmOrDeployment(instance);
                myRequestId = operationResponse.getRequestId();
                return myRequestId;
            }

            @NotNull
            public ActionIdChecker getActionIdChecker() {
                return myApiConnector;
            }

            public void onFinish() {
                myInstances.remove(instance.getInstanceId());
            }

            public void onError(final Throwable th) {
                instance.setStatus(InstanceStatus.ERROR);
                instance.updateErrors(new TypedCloudErrorInfo(th.getMessage(), th.getMessage()));
            }
        });

    }

    @Override
    public AzureCloudInstance startNewInstance(@NotNull final CloudInstanceUserData tag) {
        final AzureCloudInstance instance;
        final String vmName;

        if (myImageDetails.getBehaviour().isUseOriginal()) {
            vmName = myImageDetails.getSourceName();
            instance = myInstances.get(myImageDetails.getSourceName());
        } else {
            vmName = String.format("%s-%d", myImageDetails.getVmNamePrefix().toLowerCase(), myIdProvider.getNextId());
            instance = new AzureCloudInstance(this, vmName);
            if (myInstances.size() >= myImageDetails.getMaxInstances()) {
                throw new TeamCityRuntimeException("Unable to start more instances. Limit reached");
            }
            myInstances.put(instance.getInstanceId(), instance);
        }

        // We should reset start date since Azure Classic API
        // does not provide info about instance startup time.
        instance.setStartDate(new Date());
        instance.setStatus(InstanceStatus.SCHEDULED_TO_START);

        try {
            myActionsQueue.queueAction(
                    myImageDetails.getServiceName(), new ProvisionActionsQueue.InstanceAction() {
                        private String operationId = null;

                        @NotNull
                        public String getName() {
                            return "start new instance: " + instance.getName();
                        }

                        @NotNull
                        public String action() throws ServiceException, IOException {
                            final OperationResponse response;
                            if (myImageDetails.getBehaviour().isUseOriginal()) {
                                response = myApiConnector.startVM(AzureCloudImage.this);
                            } else {
                                final CloudInstanceUserData data = AzureUtils.setVmNameForTag(tag, vmName);
                                response = myApiConnector.createVmOrDeployment(AzureCloudImage.this, vmName, data, myGeneralized);
                            }
                            instance.setStatus(InstanceStatus.STARTING);
                            operationId = response.getRequestId();
                            return operationId;
                        }

                        @NotNull
                        public ActionIdChecker getActionIdChecker() {
                            return myApiConnector;
                        }

                        public void onFinish() {
                            try {
                                final OperationStatusResponse operationStatus = myApiConnector.getOperationStatus(operationId);
                                if (operationStatus.getStatus() == OperationStatus.Succeeded) {
                                    instance.setStatus(InstanceStatus.RUNNING);
                                } else if (operationStatus.getStatus() == OperationStatus.Failed) {
                                    instance.setStatus(InstanceStatus.ERROR);
                                    final OperationStatusResponse.ErrorDetails error = operationStatus.getError();
                                    instance.updateErrors(new TypedCloudErrorInfo(error.getCode(), error.getMessage()));
                                    LOG.warn(error.getMessage());
                                }
                            } catch (Exception e) {
                                LOG.warn(e.toString(), e);
                                instance.setStatus(InstanceStatus.ERROR);
                                instance.updateErrors(new TypedCloudErrorInfo(e.getMessage(), e.toString()));
                            }
                        }

                        public void onError(final Throwable th) {
                            instance.setStatus(InstanceStatus.ERROR);
                            instance.updateErrors(new TypedCloudErrorInfo(th.getMessage(), th.getMessage()));
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return instance;
    }

    @Nullable
    @Override
    public Integer getAgentPoolId() {
        return myImageDetails.getAgentPoolId();
    }
}
