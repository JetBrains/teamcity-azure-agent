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
import jetbrains.buildServer.clouds.azure.AzureCloudClientBase;
import jetbrains.buildServer.clouds.azure.FileIdProvider;
import jetbrains.buildServer.clouds.azure.IdProvider;
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.serverSide.crypt.EncryptUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * ARM cloud client.
 */
class AzureCloudClient extends AzureCloudClientBase<AzureCloudInstance, AzureCloudImage, AzureCloudImageDetails> {

    private final File myAzureIdxStorage;

    AzureCloudClient(@NotNull final CloudClientParameters params,
                     @NotNull final CloudApiConnector<AzureCloudImage, AzureCloudInstance> apiConnector,
                     @NotNull final File azureIdxStorage) {
        super(params, apiConnector);
        myAzureIdxStorage = azureIdxStorage;
    }

    @Override
    protected AzureCloudImage checkAndCreateImage(@NotNull AzureCloudImageDetails imageDetails) {
        final String fileName = EncryptUtil.md5(imageDetails.getSourceName()) + ".idx";
        final IdProvider idProvider = new FileIdProvider(new File(myAzureIdxStorage, fileName));
        return new AzureCloudImage(imageDetails, (AzureApiConnector) myApiConnector, idProvider);
    }
}
