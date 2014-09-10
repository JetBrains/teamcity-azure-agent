package jetbrains.buildServer.clouds.azure;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.azure.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.azure.connector.AzureInstance;
import jetbrains.buildServer.clouds.azure.connector.ConditionalRunner;
import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.beans.AbstractCloudImageDetails;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.connector.CloudAsyncTaskExecutor;
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

  public AzureCloudClient(@NotNull final CloudClientParameters params, @NotNull final Collection<AzureCloudImageDetails> images, final AzureApiConnector apiConnector) {
    super(params, images, apiConnector);
    myAsyncTaskExecutor.scheduleWithFixedDelay(new ConditionalRunner(), 0, 5, TimeUnit.SECONDS);
  }

  public void dispose() {
    super.dispose();
  }

  public boolean isInitialized() {
    return true;
  }

  @Override
  protected AzureCloudImage checkAndCreateImage(@NotNull final AzureCloudImageDetails imageDetails) {
    return new AzureCloudImage(imageDetails, (AzureApiConnector)myApiConnector, myAsyncTaskExecutor);
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
