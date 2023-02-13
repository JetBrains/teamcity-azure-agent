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

import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.compute.VirtualMachine
import com.microsoft.azure.management.storage.StorageAccount
import com.microsoft.azure.management.storage.StorageAccountKey
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
                .flatMap { storageAccount ->
                    storageAccount
                            .keysAsync
                            .last()
                            .materialize()
                            .filter {
                                if (it.isOnError) LOG.info("Could not read StorageAccount keys. StorageAccountId: ${storageAccount.id()}. Error: ", it.throwable)
                                !it.isOnError
                            }
                            .dematerialize<List<StorageAccountKey>>()
                            .map {
                                storageAccount to it
                            }
                }
                .map { (storageAccount, keys) ->
                    StorageAccountTaskAccountDescriptor(
                            storageAccount.id(),
                            storageAccount.name(),
                            storageAccount.regionName(),
                            storageAccount.resourceGroupName(),
                            storageAccount.skuType().name().toString(),
                            keys.map { it.value() }
                    )
                }
                .toList()
                .last()
                .toSingle()
    }

    companion object {
        private val LOG = Logger.getInstance(FetchStorageAccountsTaskImpl::class.java.name)
    }
}
