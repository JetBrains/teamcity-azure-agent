/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

/**
 * Azure ARM API connector.
 */
interface AzureApiConnector : CloudApiConnector<AzureCloudImage, AzureCloudInstance> {
    suspend fun createInstance(instance: AzureCloudInstance, userData: CloudInstanceUserData)

    suspend fun deleteInstance(instance: AzureCloudInstance)

    suspend fun restartInstance(instance: AzureCloudInstance)

    suspend fun startInstance(instance: AzureCloudInstance, userData: CloudInstanceUserData)

    suspend fun stopInstance(instance: AzureCloudInstance)

    suspend fun getSubscriptions(): Map<String, String>

    suspend fun getRegions(): Map<String, String>

    suspend fun getResourceGroups(): Map<String, String>

    suspend fun getInstances(): List<AzureApiVMInstance>

    suspend fun hasInstance(image: AzureCloudImage): Boolean

    suspend fun getImageName(imageId: String): String

    suspend fun getImages(region: String): Map<String, List<String>>

    suspend fun getVmSizes(region: String): List<String>

    suspend fun getStorageAccounts(region: String): List<String>

    suspend fun getNetworks(region: String): Map<String, List<String>>

    suspend fun getVhdOsType(imageUrl: String, region: String): String?

    suspend fun getVhdMetadata(imageUrl: String, region: String): Map<String, String>?

    suspend fun getServices(region: String): Map<String, Set<String>>

    suspend fun deleteVmBlobs(instance: AzureCloudInstance)

    fun start()
}

data class AzureApiVMInstance(
    val id: String,
    val description: String,
    val osType: String?
)
