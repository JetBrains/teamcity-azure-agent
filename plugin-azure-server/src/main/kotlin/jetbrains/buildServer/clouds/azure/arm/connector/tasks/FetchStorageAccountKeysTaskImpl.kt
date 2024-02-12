/*
 * Copyright 2000-2024 JetBrains s.r.o.
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
import com.microsoft.azure.management.storage.implementation.StorageAccountListKeysResultInner
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

data class FetchStorageAccountKeysTaskParameter(
    val resourceGroup: String,
    val accountName: String,
)

data class StorageAccountKeyDescriptor(
    val value: String
)
class FetchStorageAccountKeysTaskImpl : AzureThrottlerCacheableTaskBaseImpl<FetchStorageAccountKeysTaskParameter, List<StorageAccountKeyDescriptor>>() {
    override fun createQuery(api: AzureApi, taskContext: AzureTaskContext, parameter: FetchStorageAccountKeysTaskParameter): Single<List<StorageAccountKeyDescriptor>> {
        return api
            .storageAccounts()
            .inner()
            .listKeysAsync(parameter.resourceGroup, parameter.accountName)
            .materialize()
            .filter {
                if (it.isOnError) LOG.info("Could not read StorageAccount keys. ResourceGroup: ${parameter.resourceGroup}, Account Name: ${parameter.accountName}. CorellationId=${taskContext.corellationId}. Error: ", it.throwable)
                !it.isOnError
            }
            .dematerialize<StorageAccountListKeysResultInner>()
            .flatMapIterable { it.keys() }
            .map { StorageAccountKeyDescriptor(it.value()) }
            .toList()
            .last()
            .toSingle()
    }

    companion object {
        val LOG = Logger.getInstance(FetchStorageAccountKeysTaskImpl::class.java.name)
    }
}
