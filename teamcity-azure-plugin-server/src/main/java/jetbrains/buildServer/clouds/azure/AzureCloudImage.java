package jetbrains.buildServer.clouds.azure;

import com.intellij.openapi.diagnostic.Logger;
import com.microsoft.windowsazure.core.OperationResponse;
import com.microsoft.windowsazure.core.OperationStatus;
import com.microsoft.windowsazure.core.OperationStatusResponse;
import com.microsoft.windowsazure.exception.ServiceException;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.parsers.ParserConfigurationException;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.azure.connector.*;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXException;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * @author Sergey.Pak
 *         Date: 7/31/2014
 *         Time: 5:18 PM
 */
public class AzureCloudImage extends AbstractCloudImage<AzureCloudInstance> {

  private static final Logger LOG = Logger.getInstance(AzureCloudImage.class.getName());

  private final AzureCloudImageDetails myImageDetails;
  @NotNull private final File myIdxFile;
  private final AzureApiConnector myApiConnector;
  private boolean myGeneralized;

  protected AzureCloudImage(@NotNull final AzureCloudImageDetails imageDetails,
                            @NotNull final AzureApiConnector apiConnector) {
    super(imageDetails.getImageName(), imageDetails.getImageName());
    myImageDetails = imageDetails;
    myIdxFile = imageDetails.getImageIdxFile();
    if (!myIdxFile.exists()){
      try {
        FileUtil.writeFileAndReportErrors(myIdxFile, "1");
      } catch (IOException e) {
        LOG.warn(String.format("Unable to write idx file '%s': %s", myIdxFile.getAbsolutePath(), e.toString()));
      }
    }
    myApiConnector = apiConnector;
    if (myImageDetails.getCloneType().isUseOriginal()) {
      myGeneralized = false;
    } else {
      myGeneralized = apiConnector.isImageGeneralized(imageDetails.getImageName());
    }
    final Map<String, AzureInstance> instances = apiConnector.listImageInstances(this);
    for (AzureInstance azureInstance : instances.values()) {
      final AzureCloudInstance cloudInstance = new AzureCloudInstance(this, azureInstance.getName(), azureInstance.getName());
      cloudInstance.setStatus(azureInstance.getInstanceStatus());
      myInstances.put(azureInstance.getName(), cloudInstance);
    }
  }

  public AzureCloudImageDetails getImageDetails() {
    return myImageDetails;
  }

  @Override
  public boolean canStartNewInstance() {
    if (myImageDetails.getCloneType().isUseOriginal()) {
      return myInstances.get(myImageDetails.getImageName()).getStatus() == InstanceStatus.STOPPED;
    } else {
      return myInstances.size() < myImageDetails.getMaxInstancesCount()
             && ProvisionActionsQueue.isLocked(myImageDetails.getServiceName(), myImageDetails.getDeploymentName());
    }
  }

  @Override
  public void terminateInstance(@NotNull final AzureCloudInstance instance) {
    try {
      instance.setStatus(InstanceStatus.STOPPING);
      ProvisionActionsQueue.queueAction(myImageDetails.getServiceName(), myImageDetails.getDeploymentName(), new ProvisionActionsQueue.InstanceAction() {
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
              if (myImageDetails.getCloneType().isDeleteAfterStop()) {
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
    ProvisionActionsQueue.queueAction(myImageDetails.getServiceName(), myImageDetails.getDeploymentName(), new ProvisionActionsQueue.InstanceAction() {
      private String myRequestId;

      @NotNull
      public String getName() {
        return "delete instance " + instance.getName();
      }

      @NotNull
      public String action() throws ServiceException, IOException {
        final OperationResponse operationResponse = myApiConnector.deleteVM(instance);
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
    });

  }

  @Override
  public AzureCloudInstance startNewInstance(@NotNull final CloudInstanceUserData tag) {
    final AzureCloudInstance instance;
    final String vmName;
    if (myImageDetails.getCloneType().isUseOriginal()) {
      vmName = myImageDetails.getImageName();
    } else {
      vmName = String.format("%s.%d", myImageDetails.getVmNamePrefix(), getNextIdx());
    }
    if (myImageDetails.getCloneType().isUseOriginal()) {
      instance = myInstances.get(myImageDetails.getImageName());
    } else {
      instance = new AzureCloudInstance(this, vmName);
      myInstances.put(instance.getInstanceId(), instance);
    }
    instance.setStatus(InstanceStatus.SCHEDULED_TO_START);
    instance.refreshStartDate();
    try {
      ProvisionActionsQueue.queueAction(
        myImageDetails.getServiceName(), myImageDetails.getDeploymentName(), new ProvisionActionsQueue.InstanceAction() {
          private String operationId = null;

          @NotNull
          public String getName() {
            return "start new instance: " + instance.getName();
          }

          @NotNull
          public String action() throws ServiceException, IOException {
            final OperationResponse response;
            if (myImageDetails.getCloneType().isUseOriginal()) {
              response = myApiConnector.startVM(AzureCloudImage.this);
            } else {
              if (myInstances.size() >= myImageDetails.getMaxInstancesCount()){
                throw new ServiceException("Unable to start more instances. Limit reached");
              }
              response = myApiConnector.createAndStartVM(AzureCloudImage.this, vmName, tag, myGeneralized);
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
}
