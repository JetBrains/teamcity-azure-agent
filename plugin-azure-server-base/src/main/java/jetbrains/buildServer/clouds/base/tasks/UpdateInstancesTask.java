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

package jetbrains.buildServer.clouds.base.tasks;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:52 PM
 */
public class UpdateInstancesTask<G extends AbstractCloudInstance<T>, T extends AbstractCloudImage<G,?>, F extends AbstractCloudClient<G, T, ?>> implements Runnable {
  private static final Logger LOG = Logger.getInstance(UpdateInstancesTask.class.getName());

  @NotNull private final CloudApiConnector myConnector;
  protected final F myClient;


  public UpdateInstancesTask(@NotNull final CloudApiConnector connector, final F client) {
    myConnector = connector;
    myClient = client;
  }

  public void run() {
    final Map<InstanceStatus, List<String>> instancesByStatus = new HashMap<InstanceStatus, List<String>>();
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
          if (!instancesByStatus.containsKey(realInstanceStatus)){
            instancesByStatus.put(realInstanceStatus, new ArrayList<String>());
          }
          instancesByStatus.get(realInstanceStatus).add(realInstanceName);

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
            if (cloudInstance.getStatus() != InstanceStatus.SCHEDULED_TO_START && cloudInstance.getStatus() != InstanceStatus.STARTING) {
              instancesToRemove.add(instanceName);
            }
            continue;
          }

          cloudInstance.updateErrors(myConnector.checkInstance(cloudInstance));
          if (instance.getStartDate() != null) {
            cloudInstance.setStartDate(instance.getStartDate());
          }
          final String ipAddress = instance.getIpAddress();
          if (ipAddress != null) {
            cloudInstance.setNetworkIdentify(ipAddress);
          }
        }
        for (String instanceName : instancesToRemove) {
          image.removeInstance(instanceName);
        }
        image.detectNewInstances(realInstances);
      }
    } catch (Exception ex){
      LOG.warn(ex.toString(), ex);
    } finally {
      //logging here:
      for (InstanceStatus instanceStatus : instancesByStatus.keySet()) {
        LOG.info(String.format("Instances in '%s' status: %s", instanceStatus.getText(), Arrays.toString(instancesByStatus.get(instanceStatus).toArray())));
      }
    }
  }

  private static boolean isStatusPermanent(InstanceStatus status){
    return status == InstanceStatus.STOPPED || status == InstanceStatus.RUNNING;
  }
}
