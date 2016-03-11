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
import jetbrains.buildServer.clouds.azure.FileIdProvider;
import jetbrains.buildServer.clouds.azure.IdProvider;
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * ARM cloud client.
 */
public class AzureCloudClient extends AbstractCloudClient<AzureCloudInstance, AzureCloudImage, AzureCloudImageDetails> {

    private final File myAzureIdxStorage;

    public AzureCloudClient(@NotNull final CloudClientParameters params,
                            @NotNull final Collection<AzureCloudImageDetails> images,
                            @NotNull final CloudApiConnector apiConnector,
                            @NotNull final File azureIdxStorage) {
        super(params, images, apiConnector);
        myAzureIdxStorage = azureIdxStorage;
    }

    @Override
    protected AzureCloudImage checkAndCreateImage(@NotNull AzureCloudImageDetails imageDetails) {
        final IdProvider idProvider = new FileIdProvider(new File(myAzureIdxStorage, imageDetails.getSourceName() + ".idx"));
        return new AzureCloudImage(imageDetails, (AzureApiConnector) myApiConnector, idProvider);
    }

    @Override
    protected UpdateInstancesTask<AzureCloudInstance, AzureCloudImage, ?> createUpdateInstancesTask() {
        return new UpdateInstancesTask<AzureCloudInstance, AzureCloudImage, AzureCloudClient>(myApiConnector, this);
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Nullable
    @Override
    public AzureCloudInstance findInstanceByAgent(@NotNull AgentDescription agent) {
        return null;
    }

    @Nullable
    @Override
    public String generateAgentName(@NotNull AgentDescription agentDescription) {
        return null;
    }
}
