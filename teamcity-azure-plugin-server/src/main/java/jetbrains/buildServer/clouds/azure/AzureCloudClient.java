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
import java.io.File;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.azure.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.azure.connector.ConditionalRunner;
import jetbrains.buildServer.clouds.azure.connector.ProvisionActionsQueue;
import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/31/2014
 *         Time: 4:28 PM
 */
public class AzureCloudClient extends AbstractCloudClient<AzureCloudInstance, AzureCloudImage, AzureCloudImageDetails> {

  private static final Logger LOG = Logger.getInstance(AzureCloudClient.class.getName());
  @NotNull private final File myAzureIdxStorage;

  private boolean myInitialized = false;
  private final ProvisionActionsQueue myActionsQueue;


  public AzureCloudClient(@NotNull final CloudClientParameters params,
                          @NotNull final Collection<AzureCloudImageDetails> images,
                          @NotNull final AzureApiConnector apiConnector,
                          @NotNull final File azureIdxStorage) {
    super(params, images, apiConnector);
    myAzureIdxStorage = azureIdxStorage;
    myActionsQueue = new ProvisionActionsQueue(myAsyncTaskExecutor);
    myAsyncTaskExecutor.scheduleWithFixedDelay(myActionsQueue.getRequestCheckerCleanable(apiConnector), 0, 20, TimeUnit.SECONDS);
    myInitialized = true;
  }

  public void dispose() {
    super.dispose();
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  @Override
  protected AzureCloudImage checkAndCreateImage(@NotNull final AzureCloudImageDetails imageDetails) {
    return new AzureCloudImage(imageDetails, myActionsQueue, (AzureApiConnector)myApiConnector, myAzureIdxStorage);
  }

  @Override
  protected UpdateInstancesTask<AzureCloudInstance, AzureCloudImage, ?> createUpdateInstancesTask() {
    return new UpdateInstancesTask<AzureCloudInstance, AzureCloudImage, AzureCloudClient>(myApiConnector, this);
  }

  @Nullable
  @Override
  public AzureCloudInstance findInstanceByAgent(@NotNull final AgentDescription agent) {
    final String instanceName = agent.getConfigurationParameters().get(AzurePropertiesNames.INSTANCE_NAME);
    if (instanceName == null)
      return null;
    for (AzureCloudImage image : myImageMap.values()) {
      final AzureCloudInstance instanceById = image.findInstanceById(instanceName);
      if (instanceById != null){
        return instanceById;
      }
    }
    return null;
  }

  @Nullable
  public String generateAgentName(@NotNull final AgentDescription agent) {
    return "aaaaa";
  }
}
