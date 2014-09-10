package jetbrains.buildServer.clouds.azure;

import java.util.*;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudRegistrar;
import jetbrains.buildServer.clouds.CloudState;
import jetbrains.buildServer.clouds.azure.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.base.AbstractCloudClientFactory;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/31/2014
 *         Time: 4:35 PM
 */
public class AzureCloudClientFactory extends AbstractCloudClientFactory<AzureCloudImageDetails, AzureCloudImage, AzureCloudClient> {

  private final String myHtmlPath;

  public AzureCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                 @NotNull final PluginDescriptor pluginDescriptor) {
    super(cloudRegistrar);
    myHtmlPath = pluginDescriptor.getPluginResourcesPath("azure-settings.html");
  }

  @Override
  public AzureCloudClient createNewClient(@NotNull final CloudState state, @NotNull final Collection<AzureCloudImageDetails> imageDetailsList, @NotNull final CloudClientParameters params) {
    final String serverUrl = params.getParameter("serverUrl");
    final String managementCertificate = params.getParameter("managementCertificate");
    final String subscriptionId = params.getParameter("subscriptionId");
    final AzureApiConnector apiConnector = new AzureApiConnector(serverUrl, managementCertificate, subscriptionId);
    return new AzureCloudClient(params, imageDetailsList, apiConnector);
  }

  @Override
  public AzureCloudClient createNewClient(@NotNull final CloudState state, @NotNull final CloudClientParameters params, final TypedCloudErrorInfo[] profileErrors) {
    final String serverUrl = params.getParameter("serverUrl");
    final String managementCertificate = params.getParameter("managementCertificate");
    final String subscriptionId = params.getParameter("subscriptionId");
    final AzureApiConnector apiConnector = new AzureApiConnector(serverUrl, managementCertificate, subscriptionId);
    return new AzureCloudClient(params, Collections.<AzureCloudImageDetails>emptyList(), apiConnector);
  }


  @Override
  public Collection<AzureCloudImageDetails> parseImageData(final String imageData) {
    final String[] split = imageData.split(";X;");
    List<AzureCloudImageDetails> images = new ArrayList<AzureCloudImageDetails>();
    for (String s : split) {
      images.add(AzureCloudImageDetails.fromString(s));
    }
    return images;
  }

  @Nullable
  @Override
  protected TypedCloudErrorInfo[] checkClientParams(@NotNull final CloudClientParameters params) {
    return new TypedCloudErrorInfo[0];
  }

  @NotNull
  public String getCloudCode() {
    return "azure";
  }

  @NotNull
  public String getDisplayName() {
    return "Azure";
  }

  @Nullable
  public String getEditProfileUrl() {
    return myHtmlPath;
  }

  @NotNull
  public Map<String, String> getInitialParameterValues() {
    return Collections.emptyMap();
  }

  @NotNull
  public PropertiesProcessor getPropertiesProcessor() {
    return new PropertiesProcessor() {
      public Collection<InvalidProperty> process(Map<String, String> stringStringMap) {
        return Collections.emptyList();
      }
    };
  }

  public boolean canBeAgentOfType(@NotNull final AgentDescription description) {
    return true;
  }
}
