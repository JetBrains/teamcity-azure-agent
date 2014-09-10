package jetbrains.buildServer.clouds.azure;

import java.util.Date;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/31/2014
 *         Time: 7:15 PM
 */
public class AzureCloudInstance extends AbstractCloudInstance<AzureCloudImage> {

  protected AzureCloudInstance(@NotNull final AzureCloudImage image, String name, String id) {
    super(image, name, id);
  }


  public boolean containsAgent(@NotNull final AgentDescription agent) {
    final String agentInstanceName = agent.getConfigurationParameters().get(AzurePropertiesNames.INSTANCE_NAME);
    return getName().equals(agentInstanceName);
  }
}
