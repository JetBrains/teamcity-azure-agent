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

package jetbrains.buildServer.clouds.azure.asm;

import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.azure.AzurePropertiesNames;
import jetbrains.buildServer.clouds.azure.AzureUtils;
import jetbrains.buildServer.clouds.azure.asm.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.azure.asm.errors.InvalidCertificateException;
import jetbrains.buildServer.clouds.base.AbstractCloudClientFactory;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.server.impl.CloudManagerBase;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author Sergey.Pak
 *         Date: 7/31/2014
 *         Time: 4:35 PM
 */
public class AzureCloudClientFactory extends AbstractCloudClientFactory<AzureCloudImageDetails, AzureCloudClient> {

  private final String myHtmlPath;
  private final File myAzureStorage;


  public AzureCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                 @NotNull final EventDispatcher<BuildServerListener> serverDispatcher,
                                 @NotNull final CloudManagerBase cloudManager,
                                 @NotNull final PluginDescriptor pluginDescriptor,
                                 @NotNull final ServerPaths serverPaths) {
    super(cloudRegistrar);

    myAzureStorage = new File(serverPaths.getPluginDataDirectory(), "azureIdx");
    if (!myAzureStorage.exists()) {
      //noinspection ResultOfMethodCallIgnored
      myAzureStorage.mkdirs();
    }

    myHtmlPath = pluginDescriptor.getPluginResourcesPath("azure-settings.html");
    serverDispatcher.addListener(new BuildServerAdapter() {
      @Override
      public void agentStatusChanged(@NotNull final SBuildAgent agent, final boolean wasEnabled, final boolean wasAuthorized) {
        if (!agent.isAuthorized() || wasAuthorized)
          return;

        final Map<String, String> config = agent.getConfigurationParameters();
        if (config.containsKey(AzurePropertiesNames.INSTANCE_NAME) && !config.containsKey(CloudContants.PROFILE_ID)) {
          // windows azure agent connected
          for (CloudProfile profile : cloudManager.listProfiles()) {
            final CloudClientEx existingClient = cloudManager.getClientIfExists(profile.getProfileId());
            if (existingClient == null)
              continue;
            final CloudInstance instanceByAgent = existingClient.findInstanceByAgent(agent);
            if (instanceByAgent != null) {
              // we found instance and profile. Now updating parameters
              return;
            }
          }
        }
      }
    });
  }

  @Override
  public AzureCloudClient createNewClient(@NotNull final CloudState state, @NotNull final Collection<AzureCloudImageDetails> imageDetailsList, @NotNull final CloudClientParameters params) {
    final String managementCertificate = params.getParameter("managementCertificate");
    final String subscriptionId = params.getParameter("subscriptionId");
    final AzureApiConnector apiConnector;
    try {
      apiConnector = new AzureApiConnector(subscriptionId, managementCertificate);
    } catch (InvalidCertificateException e) {
      throw new RuntimeException(e);
    }
    return new AzureCloudClient(params, imageDetailsList, apiConnector, myAzureStorage);
  }

  @Override
  public AzureCloudClient createNewClient(@NotNull final CloudState state, @NotNull final CloudClientParameters params, final TypedCloudErrorInfo[] profileErrors) {
    final String managementCertificate = params.getParameter("managementCertificate");
    final String subscriptionId = params.getParameter("subscriptionId");
    final AzureApiConnector apiConnector;
    try {
      apiConnector = new AzureApiConnector(subscriptionId, managementCertificate);
    } catch (InvalidCertificateException e) {
      throw new RuntimeException(e);
    }
    final AzureCloudClient azureCloudClient = new AzureCloudClient(params, Collections.<AzureCloudImageDetails>emptyList(), apiConnector, myAzureStorage);
    azureCloudClient.updateErrors(Arrays.asList(profileErrors));
    return azureCloudClient;
  }


  @Override
  public Collection<AzureCloudImageDetails> parseImageData(final CloudClientParameters params) {
    return AzureUtils.parseImageData(AzureCloudImageDetails.class, params);
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
    return "Azure Service Management";
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
    return description.getConfigurationParameters().containsKey(AzurePropertiesNames.INSTANCE_NAME);
  }
}
