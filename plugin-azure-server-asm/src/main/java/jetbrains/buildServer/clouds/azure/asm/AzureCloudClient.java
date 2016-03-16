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

import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.azure.AzureCloudClientBase;
import jetbrains.buildServer.clouds.azure.FileIdProvider;
import jetbrains.buildServer.clouds.azure.IdProvider;
import jetbrains.buildServer.clouds.azure.asm.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.azure.connector.ProvisionActionsQueue;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * @author Sergey.Pak
 *         Date: 7/31/2014
 *         Time: 4:28 PM
 */
public class AzureCloudClient extends AzureCloudClientBase<AzureCloudInstance, AzureCloudImage, AzureCloudImageDetails> {

    private final File myAzureIdxStorage;

    private final ProvisionActionsQueue myActionsQueue;

    public AzureCloudClient(@NotNull final CloudClientParameters params,
                            @NotNull final Collection<AzureCloudImageDetails> images,
                            @NotNull final AzureApiConnector apiConnector,
                            @NotNull final File azureIdxStorage) {
        super(params, images, apiConnector);
        myAzureIdxStorage = azureIdxStorage;
        myActionsQueue = new ProvisionActionsQueue(myAsyncTaskExecutor);
        myAsyncTaskExecutor.scheduleWithFixedDelay(myActionsQueue.getRequestCheckerCleanable(apiConnector), 0, 20, TimeUnit.SECONDS);
    }

    @Override
    protected AzureCloudImage checkAndCreateImage(@NotNull final AzureCloudImageDetails imageDetails) {
        final IdProvider idProvider = new FileIdProvider(new File(myAzureIdxStorage, imageDetails.getSourceName() + ".idx"));
        return new AzureCloudImage(imageDetails, myActionsQueue, (AzureApiConnector) myApiConnector, idProvider);
    }
}
