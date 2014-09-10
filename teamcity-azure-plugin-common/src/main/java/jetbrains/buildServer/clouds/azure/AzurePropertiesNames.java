package jetbrains.buildServer.clouds.azure;

/**
 * @author Sergey.Pak
 *         Date: 4/23/2014
 *         Time: 6:42 PM
 */
public interface AzurePropertiesNames {
  public static final String AGENT_NAME = "azure.agent.name";
  public static final String AUTH_TOKEN = "azure.auth.token";
  public static final String SERVER_URL = "azure.teamcity.server.url";

  public static final String INSTANCE_NAME = "azure.instance.name";
  public static final String ENDPOINT_NAME = "TC Agent";
  public static final String IMAGE_NAME = "azure.image.name";
  public static final String USER_DATA = "azure.user.data";
  public static final String PORT_NUMBER = "azure.self.port.number";
}
