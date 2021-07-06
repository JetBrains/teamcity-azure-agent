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

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

data class StorageAccountTaskAccountDescriptor(
        val id: String,
        val name: String,
        val regionName: String,
        val resourceGroupName: String,
        val skuTypeName: String,
        var keys: List<String>)

class FetchStorageAccountsTaskImpl : AzureThrottlerCacheableTaskBaseImpl<Unit, List<StorageAccountTaskAccountDescriptor>>() {
    override fun createQuery(api: Azure, parameter: Unit): Single<List<StorageAccountTaskAccountDescriptor>> {
        return api
                .storageAccounts()
                .listAsync()
                .map { storageAccount ->
                    StorageAccountTaskAccountDescriptor(
                            storageAccount.id(),
                            storageAccount.name(),
                            storageAccount.regionName(),
                            storageAccount.resourceGroupName(),
                            storageAccount.skuType().name().toString(),
                            try {storageAccount.keys.map { it.value() }}catch(t: Throwable) { listOf("")}
                    )
                }
                .toList()
                .last()
                .toSingle()
    }
}
