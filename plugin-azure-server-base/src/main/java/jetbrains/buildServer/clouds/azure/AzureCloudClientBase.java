/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.clouds.base.beans.CloudImageDetails;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Azure cloud client base.
 */
public abstract class AzureCloudClientBase<G extends AbstractCloudInstance<T>, T extends AbstractCloudImage<G, D>, D extends CloudImageDetails>
  extends AbstractCloudClient<G, T, D> {

  private static final Logger LOG = Logger.getInstance(AzureCloudClientBase.class.getName());
  private final AzureCloudImagesHolder myImagesHolder;

  public AzureCloudClientBase(@NotNull final CloudClientParameters params,
                              @NotNull final CloudApiConnector<T, G> apiConnector,
                              @NotNull final AzureCloudImagesHolder imagesHolder) {
    super(params, apiConnector);
    myImagesHolder = imagesHolder;
  }

  @Override
  protected UpdateInstancesTask<G, T, ?> createUpdateInstancesTask() {
    return new UpdateInstancesTask<>(myApiConnector, this);
  }

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Nullable
  @Override
  public G findInstanceByAgent(@NotNull final AgentDescription agent) {
    final String instanceName = agent.getConfigurationParameters().get(AzureProperties.INSTANCE_NAME);
    if (instanceName == null) {
      return null;
    }

    for (T image : myImageMap.values()) {
      final G instanceById = image.findInstanceById(instanceName);
      if (instanceById != null) {
        return instanceById;
      }
    }

    return null;
  }

  @Nullable
  public String generateAgentName(@NotNull final AgentDescription agent) {
    final String instanceName = agent.getConfigurationParameters().get(AzureProperties.INSTANCE_NAME);
    LOG.debug("Reported azure instance name: " + instanceName);
    return instanceName;
  }

  @Override
  protected T checkAndCreateImage(@NotNull D imageDetails) {
    final String profileId = StringUtil.emptyIfNull(myParameters.getParameter("profileId"));
    final String sourceId = imageDetails.getSourceId();
    final T cloudImage = createImage(imageDetails);

    // Try to find existing images
    final T image = (T) myImagesHolder.findImage(profileId, sourceId);
    if (image != null) {
      for (G instance : image.getInstances()) {
        cloudImage.addInstance(instance);
      }
    } else {
      try {
        final Map<String, AbstractInstance> realInstances = myApiConnector.fetchInstances(cloudImage);
        cloudImage.detectNewInstances(realInstances);
      } catch (CheckedCloudException e) {
        final String message = String.format("Failed to get instances for image %s: %s", sourceId, e.getMessage());
        LOG.warnAndDebugDetails(message, e);
        cloudImage.updateErrors(TypedCloudErrorInfo.fromException(e));
      }
    }

    myImagesHolder.addImage(profileId, cloudImage);

    return cloudImage;
  }

  protected abstract T createImage(@NotNull D imageDetails);
}
