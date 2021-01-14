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

package jetbrains.buildServer.clouds.base.tasks;

import com.intellij.openapi.diagnostic.Logger;

import java.util.*;

import jetbrains.buildServer.Used;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:52 PM
 */
public class UpdateInstancesTask<G extends AbstractCloudInstance<T>,
  T extends AbstractCloudImage<G, ?>,
  F extends AbstractCloudClient<G, T, ?>
  > implements Runnable {
  private static final Logger LOG = Logger.getInstance(UpdateInstancesTask.class.getName());

  private static final long STUCK_STATUS_TIME = 10 * 60 * 1000l; // 2 minutes;

  @NotNull
  protected final CloudApiConnector<T, G> myConnector;
  @NotNull
  protected final F myClient;

  @Used("Tests")
  private final long myStuckTime;
  @Used("Tests")
  private final boolean myRethrowException;


  public UpdateInstancesTask(@NotNull final CloudApiConnector<T, G> connector,
                             @NotNull final F client) {
    this(connector, client, STUCK_STATUS_TIME, false);
  }

  @Used("Tests")
  public UpdateInstancesTask(@NotNull final CloudApiConnector<T, G> connector,
                             @NotNull final F client,
                             @Used("Tests") final long stuckTimeMillis,
                             @Used("Tests") final boolean rethrowException) {
    myConnector = connector;
    myClient = client;
    myStuckTime = stuckTimeMillis;
    myRethrowException = rethrowException;
  }

  public void run() {
    final Map<InstanceStatus, List<String>> instancesByStatus = new HashMap<InstanceStatus, List<String>>();
    try {
      List<T> goodImages = new ArrayList<>();
      final Collection<T> images = getImages();
      for (final T image : images) {
        image.updateErrors(myConnector.checkImage(image));
        if (image.getErrorInfo() != null) {
          continue;
        }
        goodImages.add(image);
      }

      final Map<T, Map<String, AbstractInstance>> groupedInstances = myConnector.fetchInstances(goodImages);
      for (Map.Entry<T, Map<String, AbstractInstance>> entry : groupedInstances.entrySet()) {
        LOG.debug(String.format("Instances for [%s]:[%s]", entry.getKey().getId(), StringUtil.join(",", entry.getValue().keySet())));
      }

      for (T image : goodImages) {
        Map<String, AbstractInstance> realInstances = groupedInstances.get(image);
        if (realInstances == null) {
          realInstances = Collections.emptyMap();
        }
        for (String realInstanceName : realInstances.keySet()) {
          final G instance = image.findInstanceById(realInstanceName);
          final AbstractInstance realInstance = realInstances.get(realInstanceName);
          if (instance == null) {
            continue;
          }
          final InstanceStatus realInstanceStatus = realInstance.getInstanceStatus();
          if (!instancesByStatus.containsKey(realInstanceStatus)) {
            instancesByStatus.put(realInstanceStatus, new ArrayList<String>());
          }
          instancesByStatus.get(realInstanceStatus).add(realInstanceName);

          if ((isStatusPermanent(instance.getStatus()) || isStuck(instance))
            && isStatusPermanent(realInstanceStatus)
            && realInstanceStatus != instance.getStatus()) {
            LOG.info(String.format("Updated instance '%s' status to %s based on API information", realInstanceName, realInstanceStatus));
            instance.setStatus(realInstanceStatus);
          }
        }

        final Collection<G> instances = image.getInstances();
        for (final G cloudInstance : instances) {
          try {
            final String instanceName = cloudInstance.getName();
            final AbstractInstance instance = realInstances.get(instanceName);
            if (instance == null) {
              if (cloudInstance.getStatus() != InstanceStatus.SCHEDULED_TO_START && cloudInstance.getStatus() != InstanceStatus.STARTING) {
                image.removeInstance(cloudInstance.getInstanceId());
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
          } catch (Exception ex) {
            LOG.debug("Error processing VM " + cloudInstance.getName() + ": " + ex.toString());
          }
        }
        image.detectNewInstances(realInstances);
      }
      myClient.updateErrors();
    } catch (Exception ex) {
      if (myRethrowException) {
        // for tests
        throw new RuntimeException(ex);
      }
      LOG.warn(ex.toString(), ex);
    } finally {
      //logging here:
      for (InstanceStatus instanceStatus : instancesByStatus.keySet()) {
        LOG.debug(String.format("Instances in '%s' status: %s", instanceStatus.getText(), Arrays.toString(instancesByStatus.get(instanceStatus).toArray())));
      }
    }
  }

  @NotNull
  protected Collection<T> getImages() {
    return myClient.getImages();
  }

  private static boolean isStatusPermanent(InstanceStatus status) {
    return status == InstanceStatus.STOPPED || status == InstanceStatus.RUNNING;
  }

  private boolean isStuck(G instance) {
    return (System.currentTimeMillis() - instance.getStatusUpdateTime().getTime()) > myStuckTime &&
      (instance.getStatus() == InstanceStatus.STOPPING
        || instance.getStatus() == InstanceStatus.STARTING
        || instance.getStatus() == InstanceStatus.SCHEDULED_TO_STOP
        || instance.getStatus() == InstanceStatus.SCHEDULED_TO_START
      );
  }
}
