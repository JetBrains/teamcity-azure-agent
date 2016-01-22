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

import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.azure.connector.AzureArmApiConnector;
import jetbrains.buildServer.clouds.base.AbstractCloudClientFactory;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
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
 * Constructs Azure ARM cloud clients.
 */
public class AzureArmCloudClientFactory extends AbstractCloudClientFactory<AzureCloudImageDetails, AzureArmCloudClient> {

  private final File myAzureStorage;
  private final PluginDescriptor myPluginDescriptor;

  public AzureArmCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                    @NotNull final EventDispatcher<BuildServerListener> serverDispatcher,
                                    @NotNull final CloudManagerBase cloudManager,
                                    @NotNull final PluginDescriptor pluginDescriptor,
                                    @NotNull final ServerPaths serverPaths) {
    super(cloudRegistrar);
    myAzureStorage = new File(serverPaths.getPluginDataDirectory(), "azureIdx");
    if (!myAzureStorage.exists()) {
      myAzureStorage.mkdirs();
    }

    myPluginDescriptor = pluginDescriptor;

    serverDispatcher.addListener(new BuildServerAdapter() {
      @Override
      public void agentStatusChanged(@NotNull final SBuildAgent agent, final boolean wasEnabled, final boolean wasAuthorized) {
        if (!agent.isAuthorized() || wasAuthorized) {
          return;
        }

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
  public AzureArmCloudClient createNewClient(@NotNull final CloudState state, @NotNull final Collection<AzureCloudImageDetails> images, @NotNull final CloudClientParameters params) {
    final CloudApiConnector apiConnector = new AzureArmApiConnector();
    return new AzureArmCloudClient(params, images, apiConnector);
  }

  @Override
  public AzureArmCloudClient createNewClient(@NotNull final CloudState state, @NotNull final CloudClientParameters params, final TypedCloudErrorInfo[] errors) {
    final CloudApiConnector apiConnector = new AzureArmApiConnector();
    final AzureArmCloudClient azureCloudClient = new AzureArmCloudClient(params, Collections.<AzureCloudImageDetails>emptyList(), apiConnector);
    azureCloudClient.updateErrors(Arrays.asList(errors));
    return azureCloudClient;
  }


  @Override
  public Collection<AzureCloudImageDetails> parseImageData(final CloudClientParameters params) {
    return AzureUtils.parseImageData(params);
  }

  @Nullable
  @Override
  protected TypedCloudErrorInfo[] checkClientParams(@NotNull final CloudClientParameters params) {
    return new TypedCloudErrorInfo[0];
  }

  @NotNull
  public String getCloudCode() {
    return "cloud-azure-arm";
  }

  @NotNull
  public String getDisplayName() {
    return "Azure Resource Manager";
  }

  @Nullable
  public String getEditProfileUrl() {
    return myPluginDescriptor.getPluginResourcesPath("azure-settings.html");
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
