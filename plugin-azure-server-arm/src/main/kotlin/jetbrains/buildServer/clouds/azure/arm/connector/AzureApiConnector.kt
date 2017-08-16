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

package jetbrains.buildServer.clouds.azure.arm.connector

import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImage
import jetbrains.buildServer.clouds.azure.arm.AzureCloudInstance
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector
import kotlinx.coroutines.experimental.Deferred

/**
 * Azure ARM API connector.
 */
interface AzureApiConnector : CloudApiConnector<AzureCloudImage, AzureCloudInstance> {
    fun createVmAsync(instance: AzureCloudInstance, userData: CloudInstanceUserData): Deferred<*>

    fun deleteVmAsync(instance: AzureCloudInstance): Deferred<*>

    fun restartVmAsync(instance: AzureCloudInstance): Deferred<*>

    fun startVmAsync(instance: AzureCloudInstance): Deferred<*>

    fun stopVmAsync(instance: AzureCloudInstance): Deferred<*>

    fun getSubscriptionsAsync(): Deferred<Map<String, String>>

    fun getRegionsAsync(): Deferred<Map<String, String>>

    fun getResourceGroupsAsync(): Deferred<Map<String, String>>

    fun getImageNameAsync(imageId: String): Deferred<String>

    fun getImagesAsync(region: String): Deferred<Map<String, List<String>>>

    fun getVmSizesAsync(region: String): Deferred<List<String>>

    fun getNetworksAsync(region: String): Deferred<Map<String, List<String>>>

    fun getVhdOsTypeAsync(imageUrl: String, region: String): Deferred<String?>

    fun getVhdMetadataAsync(imageUrl: String, region: String): Deferred<Map<String, String>?>

    fun deleteVmBlobsAsync(instance: AzureCloudInstance): Deferred<*>
}
