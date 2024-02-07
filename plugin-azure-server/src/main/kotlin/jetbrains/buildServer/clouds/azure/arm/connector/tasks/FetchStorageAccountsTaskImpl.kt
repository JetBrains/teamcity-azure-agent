

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.management.storage.StorageAccountKey
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
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
    override fun createQuery(api: AzureApi, taskContext: AzureTaskContext, parameter: Unit): Single<List<StorageAccountTaskAccountDescriptor>> {
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
