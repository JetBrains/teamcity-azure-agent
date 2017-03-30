/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import jetbrains.buildServer.TeamCityRuntimeException;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.azure.AzureCloudClientBase;
import jetbrains.buildServer.clouds.azure.AzureCloudImagesHolder;
import jetbrains.buildServer.clouds.azure.FileIdProvider;
import jetbrains.buildServer.clouds.azure.IdProvider;
import jetbrains.buildServer.clouds.azure.asm.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.azure.connector.ProvisionActionsQueue;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
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
                          @NotNull final AzureApiConnector apiConnector,
                          @NotNull final File azureIdxStorage,
                          @NotNull final AzureCloudImagesHolder imagesHolder) {
    super(params, apiConnector, imagesHolder);
    myAzureIdxStorage = azureIdxStorage;
    myActionsQueue = new ProvisionActionsQueue(myAsyncTaskExecutor);
    myAsyncTaskExecutor.scheduleWithFixedDelay("Update instances", myActionsQueue.getRequestCheckerCleanable(apiConnector), 0, 20, TimeUnit.SECONDS);
  }

  @Override
  protected AzureCloudImage createImage(@NotNull final AzureCloudImageDetails imageDetails) {
    final AzureApiConnector apiConnector = (AzureApiConnector)myApiConnector;

    // Check that image is generalized
    final boolean isGeneralized = !imageDetails.getBehaviour().isUseOriginal() && apiConnector.isImageGeneralized(imageDetails.getSourceId());
    if (isGeneralized) {
      if (StringUtil.isEmpty(imageDetails.getUsername()) || StringUtil.isEmpty(imageDetails.getPassword())) {
        throw new TeamCityRuntimeException("No credentials supplied for VM creation");
      }
    }

    final IdProvider idProvider = new FileIdProvider(new File(myAzureIdxStorage, imageDetails.getSourceId() + ".idx"));
    return new AzureCloudImage(imageDetails, myActionsQueue, (AzureApiConnector) myApiConnector, idProvider);
  }
}
