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

package jetbrains.buildServer.clouds.azure;

import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Dmitry.Tretyakov
 *         Date: 1/22/2016
 *         Time: 5:19 PM
 */
public class AzureArmCloudClient extends AbstractCloudClient<AzureArmCloudInstance, AzureArmCloudImage, AzureCloudImageDetails> {

  public AzureArmCloudClient(@NotNull final CloudClientParameters params,
                             @NotNull final Collection<AzureCloudImageDetails> images,
                             @NotNull final CloudApiConnector apiConnector) {
    super(params, images, apiConnector);

  }

  @Override
  protected AzureArmCloudImage checkAndCreateImage(@NotNull AzureCloudImageDetails imageDetails) {
    return null;
  }

  @Override
  protected UpdateInstancesTask<AzureArmCloudInstance, AzureArmCloudImage, ?> createUpdateInstancesTask() {
    return null;
  }

  @Override
  public boolean isInitialized() {
    return false;
  }

  @Nullable
  @Override
  public AzureArmCloudInstance findInstanceByAgent(@NotNull AgentDescription agent) {
    return null;
  }

  @Nullable
  @Override
  public String generateAgentName(@NotNull AgentDescription agentDescription) {
    return null;
  }
}
