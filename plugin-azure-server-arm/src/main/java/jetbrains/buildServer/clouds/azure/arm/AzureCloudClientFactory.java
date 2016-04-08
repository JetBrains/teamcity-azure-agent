/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm;

import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudRegistrar;
import jetbrains.buildServer.clouds.CloudState;
import jetbrains.buildServer.clouds.azure.AzurePropertiesNames;
import jetbrains.buildServer.clouds.azure.AzureUtils;
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnectorImpl;
import jetbrains.buildServer.clouds.base.AbstractCloudClientFactory;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.ServerSettings;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Constructs Azure ARM cloud clients.
 */
public class AzureCloudClientFactory extends AbstractCloudClientFactory<AzureCloudImageDetails, AzureCloudClient> {

    private final File myAzureStorage;
    private final PluginDescriptor myPluginDescriptor;
    private final ServerSettings mySettings;
    private static final List<String> SKIP_PARAMETERS = Arrays.asList(
            AzureConstants.IMAGE_URL, AzureConstants.OS_TYPE,
            AzureConstants.MAX_INSTANCES_COUNT, AzureConstants.VM_NAME_PREFIX,
            AzureConstants.VM_USERNAME, AzureConstants.VM_PASSWORD);

    public AzureCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                   @NotNull final PluginDescriptor pluginDescriptor,
                                   @NotNull final ServerPaths serverPaths,
                                   @NotNull final ServerSettings settings) {
        super(cloudRegistrar);
        mySettings = settings;
        myAzureStorage = new File(serverPaths.getPluginDataDirectory(), "cloud-" + getCloudCode() + "/indices");
        if (!myAzureStorage.exists()) {
            //noinspection ResultOfMethodCallIgnored
            myAzureStorage.mkdirs();
        }

        myPluginDescriptor = pluginDescriptor;
    }

    @Override
    public AzureCloudClient createNewClient(@NotNull final CloudState state,
                                            @NotNull final Collection<AzureCloudImageDetails> images,
                                            @NotNull final CloudClientParameters params) {

        return createNewClient(state, params, new TypedCloudErrorInfo[]{});
    }

    @Override
    public AzureCloudClient createNewClient(@NotNull final CloudState state,
                                            @NotNull final CloudClientParameters params,
                                            @NotNull final TypedCloudErrorInfo[] errors) {
        final String tenantId = getParameter(params, AzureConstants.TENANT_ID);
        final String clientId = getParameter(params, AzureConstants.CLIENT_ID);
        final String clientSecret = getParameter(params, AzureConstants.CLIENT_SECRET);
        final String subscriptionId = getParameter(params, AzureConstants.SUBSCRIPTION_ID);
        final String location = getParameter(params, AzureConstants.LOCATION);

        final AzureApiConnectorImpl apiConnector = new AzureApiConnectorImpl(tenantId, clientId, clientSecret);
        apiConnector.setSubscriptionId(subscriptionId);
        apiConnector.setServerId(mySettings.getServerUUID());
        apiConnector.setProfileId(state.getProfileId());
        apiConnector.setLocation(location);

        final AzureCloudClient azureCloudClient = new AzureCloudClient(params, apiConnector, myAzureStorage);
        azureCloudClient.updateErrors(errors);

        return azureCloudClient;
    }

    @NotNull
    private String getParameter(final CloudClientParameters params, final String parameter) {
        final String subscriptionId = params.getParameter(parameter);
        if (StringUtil.isEmpty(subscriptionId)) {
            throw new RuntimeException(parameter + " must not be empty");
        }
        return subscriptionId;
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
        return "arm";
    }

    @NotNull
    public String getDisplayName() {
        return "Azure Resource Manager";
    }

    @Nullable
    public String getEditProfileUrl() {
        return myPluginDescriptor.getPluginResourcesPath("settings.html");
    }

    @NotNull
    public Map<String, String> getInitialParameterValues() {
        return Collections.emptyMap();
    }

    @NotNull
    public PropertiesProcessor getPropertiesProcessor() {
        return new PropertiesProcessor() {
            public Collection<InvalidProperty> process(Map<String, String> properties) {
                final List<String> keys = new ArrayList<>(properties.keySet());
                for (String key : keys) {
                    if (SKIP_PARAMETERS.contains(key)) {
                        properties.remove(key);
                    }
                }

                return Collections.emptyList();
            }
        };
    }

    public boolean canBeAgentOfType(@NotNull final AgentDescription description) {
        return description.getConfigurationParameters().containsKey(AzurePropertiesNames.INSTANCE_NAME);
    }
}
