package jetbrains.buildServer.clouds.azure;

import com.intellij.openapi.diagnostic.Logger;
import com.microsoft.windowsazure.core.OperationStatus;
import com.microsoft.windowsazure.core.OperationStatusResponse;
import com.microsoft.windowsazure.exception.ServiceException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.azure.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.azure.connector.AzureInstance;
import jetbrains.buildServer.clouds.azure.connector.AzureTaskWrapper;
import jetbrains.buildServer.clouds.azure.connector.ConditionalRunner;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.connector.AsyncCloudTask;
import jetbrains.buildServer.clouds.base.connector.CloudAsyncTaskExecutor;
import jetbrains.buildServer.clouds.base.connector.CloudTaskResult;
import jetbrains.buildServer.clouds.base.connector.TaskCallbackHandler;
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
  private final AzureApiConnector myApiConnector;
  private boolean myGeneralized;

  protected AzureCloudImage(@NotNull final AzureCloudImageDetails imageDetails,
                            @NotNull final AzureApiConnector apiConnector) {
    super(imageDetails.getImageName(), imageDetails.getImageName());
    myImageDetails = imageDetails;
    myApiConnector = apiConnector;
    myGeneralized = apiConnector.isImageGeneralized(imageDetails.getImageName());
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
    return myApiConnector.isServiceFree(myImageDetails.getServiceName());
  }

  @Override
  public void terminateInstance(@NotNull final AzureCloudInstance instance) {
    try {
      final OperationStatusResponse operationStatusResponse = myApiConnector.stopVM(instance);
      if (operationStatusResponse.getStatus()== OperationStatus.Succeeded) {
        myInstances.remove(instance.getInstanceId());
      }
      myApiConnector.deleteVM(instance);
    } catch (InterruptedException ignored) {

    } catch (ExecutionException e) {
      instance.updateErrors(null);
    } catch (ServiceException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void restartInstance(@NotNull final AzureCloudInstance instance) {
    throw new NotImplementedException();
  }

  @Override
  public AzureCloudInstance startNewInstance() {
    long time = System.currentTimeMillis();

    final String vmName = String.format("%s-%x", myImageDetails.getVmNamePrefix(), time/1000);
    final AzureCloudInstance instance = new AzureCloudInstance(this, vmName, vmName);
    instance.setStatus(InstanceStatus.SCHEDULED_TO_START);
    try {
      final OperationStatusResponse response = myApiConnector.createAndStartVM(AzureCloudImage.this, vmName, myGeneralized);
      instance.setStatus(InstanceStatus.STARTING);
      final String operationId = response.getId();
      ConditionalRunner.addConditional(new ConditionalRunner.Conditional() {
        private InstanceStatus myNewStatus = InstanceStatus.RUNNING;

        public boolean canExecute() throws Exception {
          try {
            return myApiConnector.getOperationStatus(operationId).getStatus() == OperationStatus.Succeeded;
          } catch (Exception ex){
            LOG.warn("Unable to get status of operation " + operationId);
            LOG.debug("Unable to get status of operation " + operationId, ex);
            myNewStatus = InstanceStatus.ERROR;
            return true;
          }
        }

        public void execute() {
          instance.setStatus(myNewStatus);
        }
      });
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    myInstances.put(instance.getInstanceId(), instance);
    return instance;
  }
}
