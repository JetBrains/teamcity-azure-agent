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

package jetbrains.buildServer.clouds.azure.arm.connector;

import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImage;
import jetbrains.buildServer.clouds.azure.arm.AzureCloudInstance;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import org.jdeferred.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Azure ARM API connector.
 */
public interface AzureApiConnector extends CloudApiConnector<AzureCloudImage, AzureCloudInstance> {
    Promise<Void, Throwable, Void> createVmAsync(AzureCloudInstance instance, CloudInstanceUserData userData);

    Promise<Void, Throwable, Void> deleteVmAsync(@NotNull AzureCloudInstance instance);

    Promise<Void, Throwable, Void> restartVmAsync(@NotNull AzureCloudInstance instance);

    Promise<Void, Throwable, Void> startVmAsync(@NotNull AzureCloudInstance instance);

    Promise<Void, Throwable, Void> stopVmAsync(@NotNull AzureCloudInstance instance);

    Promise<Map<String, String>, Throwable, Void> getSubscriptionsAsync();

    Promise<Map<String, String>, Throwable, Void> getLocationsAsync(String subscription);

    Promise<List<String>, Throwable, Void> getVmSizesAsync();

    Promise<Map<String, List<String>>, Throwable, Void> getNetworksAsync();

    Promise<String, Throwable, Void> getVhdOsTypeAsync(@NotNull String imageUrl);
}
