package jetbrains.buildServer.clouds.azure;

import com.intellij.openapi.diagnostic.Logger;
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

  private boolean myInitialized = false;

  public AzureCloudClient(@NotNull final CloudClientParameters params, @NotNull final Collection<AzureCloudImageDetails> images, final AzureApiConnector apiConnector) {
    super(params, images, apiConnector);
    myAsyncTaskExecutor.scheduleWithFixedDelay(new ConditionalRunner(), 0, 5, TimeUnit.SECONDS);
    myAsyncTaskExecutor.scheduleWithFixedDelay(ProvisionActionsQueue.getRequestCheckerCleanable(apiConnector), 0, 20, TimeUnit.SECONDS);
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
    return new AzureCloudImage(imageDetails, (AzureApiConnector)myApiConnector);
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
