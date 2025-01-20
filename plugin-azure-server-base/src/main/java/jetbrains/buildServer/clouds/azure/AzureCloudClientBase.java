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
import org.springframework.util.CollectionUtils;

import java.util.*;

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

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  protected UpdateInstancesTask<G, T, ?> createUpdateInstancesTask() {
    return new UpdateInstancesTask<>(myApiConnector, this);
  }

  @Nullable
  @Override
  public G findInstanceByAgent(@NotNull final AgentDescription agent) {
    String instanceName = agent.getAvailableParameters().get(AzureProperties.INSTANCE_NAME);
    if (instanceName == null) {
      try {
        final String compressedConfigurationParameters = agent.getAvailableParameters().get("env." + AzureProperties.INSTANCE_ENV_VAR);
        if (StringUtil.isEmpty(compressedConfigurationParameters)) {
          return null;
        }
        final Map<String, String> configurationParameters = AzureCompress.INSTANCE.decode(compressedConfigurationParameters);
        if (CollectionUtils.isEmpty(configurationParameters)) {
          return null;
        }
        instanceName = configurationParameters.get(AzureProperties.INSTANCE_NAME);
      } catch (Exception e) {
        LOG.warnAndDebugDetails("Got exception while trying to parse agent configuration parameters from [env.TEAMCITY_AZURE]", e);
      }
      if (instanceName == null) {
        return null;
      }
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
    final String instanceName = agent.getAvailableParameters().get(AzureProperties.INSTANCE_NAME);
    LOG.debug("Reported azure instance name: " + instanceName);
    return instanceName;
  }

  @SuppressWarnings("unchecked")
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
