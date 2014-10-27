/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package jetbrains.buildServer.clouds.azure;

import com.intellij.openapi.diagnostic.Logger;
import com.microsoft.windowsazure.core.OperationResponse;
import com.microsoft.windowsazure.core.OperationStatus;
import com.microsoft.windowsazure.core.OperationStatusResponse;
import com.microsoft.windowsazure.exception.ServiceException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import jetbrains.buildServer.TeamCityRuntimeException;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.azure.connector.*;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * @author Sergey.Pak
 *         Date: 7/31/2014
 *         Time: 5:18 PM
 */
public class AzureCloudImage extends AbstractCloudImage<AzureCloudInstance, AzureCloudImageDetails> {

  private static final Logger LOG = Logger.getInstance(AzureCloudImage.class.getName());

  private final AzureCloudImageDetails myImageDetails;
  @NotNull private final ProvisionActionsQueue myActionsQueue;
  @NotNull private final File myIdxFile;
  private final AzureApiConnector myApiConnector;
  private boolean myGeneralized;

  protected AzureCloudImage(@NotNull final AzureCloudImageDetails imageDetails,
                            @NotNull final ProvisionActionsQueue actionsQueue,
                            @NotNull final AzureApiConnector apiConnector,
                            @NotNull final File azureStorage) {
    super(imageDetails.getSourceName(), imageDetails.getSourceName());
    myImageDetails = imageDetails;
    myActionsQueue = actionsQueue;
    myIdxFile = new File(azureStorage, imageDetails.getSourceName() + ".idx");
    if (!myIdxFile.exists()){
      try {
        FileUtil.writeFileAndReportErrors(myIdxFile, "1");
      } catch (IOException e) {
        LOG.warn(String.format("Unable to write idx file '%s': %s", myIdxFile.getAbsolutePath(), e.toString()));
      }
    }
    myApiConnector = apiConnector;
    if (myImageDetails.getBehaviour().isUseOriginal()) {
      myGeneralized = false;
    } else {
      myGeneralized = apiConnector.isImageGeneralized(imageDetails.getSourceName());
    }
    if (myGeneralized){
      if (StringUtil.isEmpty(imageDetails.getUsername()) || StringUtil.isEmpty(imageDetails.getPassword())){
        throw new TeamCityRuntimeException("No credentials supplied for VM creation");
      }
    }
    final Map<String, AzureInstance> instances = apiConnector.listImageInstances(this);
    for (AzureInstance azureInstance : instances.values()) {
      final AzureCloudInstance cloudInstance = new AzureCloudInstance(this, azureInstance.getName(), azureInstance.getName());
      cloudInstance.setStatus(azureInstance.getInstanceStatus());
      myInstances.put(azureInstance.getName(), cloudInstance);
    }
    if (myImageDetails.getBehaviour().isUseOriginal() && myInstances.size() != 1){
      throw new TeamCityRuntimeException("Unable to find Azure Virtual Machine " + myImageDetails.getSourceName());
    }
  }

  public AzureCloudImageDetails getImageDetails() {
    return myImageDetails;
  }

  @Override
  public boolean canStartNewInstance() {
    if (myImageDetails.getBehaviour().isUseOriginal()) {
      return myInstances.get(myImageDetails.getSourceName()).getStatus() == InstanceStatus.STOPPED;
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
            if (statusResponse.getStatus()== OperationStatus.Succeeded) {
              if (myImageDetails.getBehaviour().isDeleteAfterStop()) {
                deleteInstance(instance);
              }
            } else if (statusResponse.getStatus() == OperationStatus.Failed) {
              instance.setStatus(InstanceStatus.ERROR_CANNOT_STOP);
              final OperationStatusResponse.ErrorDetails error = statusResponse.getError();
              instance.updateErrors(Collections.singleton(new TypedCloudErrorInfo(error.getCode(), error.getMessage())));
            }
          } catch (Exception e) {
            instance.setStatus(InstanceStatus.ERROR_CANNOT_STOP);
            instance.updateErrors(Collections.singleton(new TypedCloudErrorInfo(e.getMessage(), e.toString())));
          }
        }

        public void onError(final Throwable th) {
          instance.setStatus(InstanceStatus.ERROR);
          instance.updateErrors(Arrays.asList(new TypedCloudErrorInfo(th.getMessage(), th.getMessage())));
        }
      });
    } catch (Exception e) {
      instance.setStatus(InstanceStatus.ERROR);
    }
  }

  @Override
  public void restartInstance(@NotNull final AzureCloudInstance instance) {
    throw new NotImplementedException();
  }

  private void deleteInstance(@NotNull final AzureCloudInstance instance){
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
        instance.updateErrors(Arrays.asList(new TypedCloudErrorInfo(th.getMessage(), th.getMessage())));
      }
    });

  }

  @Override
  public AzureCloudInstance startNewInstance(@NotNull final CloudInstanceUserData tag) {
    final AzureCloudInstance instance;
    final String vmName;
    if (myImageDetails.getBehaviour().isUseOriginal()) {
      vmName = myImageDetails.getSourceName();
    } else {
      vmName = String.format("%s-%d", myImageDetails.getVmNamePrefix(), getNextIdx());
    }
    if (myImageDetails.getBehaviour().isUseOriginal()) {
      instance = myInstances.get(myImageDetails.getSourceName());
    } else {
      instance = new AzureCloudInstance(this, vmName);
      if (myInstances.size() >= myImageDetails.getMaxInstances()){
        throw new TeamCityRuntimeException("Unable to start more instances. Limit reached");
      }
      myInstances.put(instance.getInstanceId(), instance);
    }
    instance.setStatus(InstanceStatus.SCHEDULED_TO_START);
    instance.refreshStartDate();
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
              response = myApiConnector.createVmOrDeployment(AzureCloudImage.this, vmName, tag, myGeneralized);
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
              if (operationStatus.getStatus() == OperationStatus.Succeeded){
                instance.setStatus(InstanceStatus.RUNNING);
                instance.refreshStartDate();
              } else if (operationStatus.getStatus() == OperationStatus.Failed){
                instance.setStatus(InstanceStatus.ERROR);
                final OperationStatusResponse.ErrorDetails error = operationStatus.getError();
                instance.updateErrors(Collections.singleton(new TypedCloudErrorInfo(error.getCode(), error.getMessage())));
                LOG.warn(error.getMessage());
              }
            } catch (Exception e) {
              LOG.warn(e.toString(), e);
              instance.setStatus(InstanceStatus.ERROR);
              instance.updateErrors(Collections.singleton(new TypedCloudErrorInfo(e.getMessage(), e.toString())));
            }
          }

          public void onError(final Throwable th) {
            instance.setStatus(InstanceStatus.ERROR);
            instance.updateErrors(Arrays.asList(new TypedCloudErrorInfo(th.getMessage(), th.getMessage())));
          }
        });
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return instance;
  }

  private int getNextIdx(){
    try {
      final int nextIdx = Integer.parseInt(FileUtil.readText(myIdxFile));
      FileUtil.writeFileAndReportErrors(myIdxFile, String.valueOf(nextIdx+1));
      return nextIdx;
    } catch (Exception e) {
      LOG.warn("Unable to read idx file: " + e.toString());
      return 0;
    }
  }

  protected boolean isGeneralized(){
    return myGeneralized;
  }
}
