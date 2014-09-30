package jetbrains.buildServer.clouds.base.tasks;

import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:52 PM
 */
public class UpdateInstancesTask<G extends AbstractCloudInstance<T>, T extends AbstractCloudImage<G>, F extends AbstractCloudClient<G, T, ?>> implements Runnable {
  private static final Logger LOG = Logger.getInstance(UpdateInstancesTask.class.getName());

  @NotNull private final CloudApiConnector myConnector;
  protected final F myClient;


  public UpdateInstancesTask(@NotNull final CloudApiConnector connector, final F client) {
    myConnector = connector;
    myClient = client;
  }

  public void run() {
    try {
      final Collection<T> images = myClient.getImages();
      for (final T image : images) {
        image.updateErrors(myConnector.checkImage(image));
        final Map<String, AbstractInstance> realInstances = myConnector.listImageInstances(image);
        for (String realInstanceName : realInstances.keySet()) {
          final G instance = image.findInstanceById(realInstanceName);
          if (instance == null) {
            //LOG.warn("Unable to find just created instance " + realInstanceName);
            continue;
          }
          final InstanceStatus realInstanceStatus = myConnector.getInstanceStatus(instance);
          LOG.info(String.format("Found instance: %s. Status: %s", realInstanceName, realInstanceStatus.getText()));
          if (realInstanceStatus != null && isStatusPermanent(instance.getStatus()) && isStatusPermanent(realInstanceStatus) && realInstanceStatus != instance.getStatus()) {
            LOG.info(String.format("Updated instance '%s' status to %s based on API information", realInstanceName, realInstanceStatus));
            instance.setStatus(realInstanceStatus);
          }
        }

        final Collection<G> instances = image.getInstances();
        List<String> instancesToRemove = new ArrayList<String>();
        for (final G cloudInstance : instances) {
          final String instanceName = cloudInstance.getName();
          final AbstractInstance instance = realInstances.get(instanceName);
          if (instance == null) {
            if (cloudInstance.getStatus() != InstanceStatus.SCHEDULED_TO_START) {
              instancesToRemove.add(instanceName);
            }
            continue;
          }

          cloudInstance.updateErrors(myConnector.checkInstance(cloudInstance));
          if (instance.getStartDate() != null) {
            cloudInstance.setStartDate(instance.getStartDate());
          }
          if (instance.getIpAddress() != null) {
            cloudInstance.setNetworkIdentify(instance.getIpAddress());
          }
        }
        for (String instanceName : instancesToRemove) {
          image.removeInstance(instanceName);
        }
      }
    } catch (Exception ex){
      LOG.warn(ex.toString(), ex);
    }
  }

  private static boolean isStatusPermanent(InstanceStatus status){
    return status == InstanceStatus.STOPPED || status == InstanceStatus.RUNNING;
  }

  private static InstanceStatus calculateStatus(@NotNull final InstanceStatus currentStatus, @Nullable final InstanceStatus realStatus){
    switch (currentStatus){
      case UNKNOWN:
        return realStatus == null ? InstanceStatus.UNKNOWN : realStatus;
      case SCHEDULED_TO_START:
        if (realStatus == InstanceStatus.RUNNING){
          return InstanceStatus.RUNNING;
        }
        return InstanceStatus.SCHEDULED_TO_START;
      case STARTING:
        if (realStatus == InstanceStatus.STOPPED){

        }
    }
    return null;
  }
}
