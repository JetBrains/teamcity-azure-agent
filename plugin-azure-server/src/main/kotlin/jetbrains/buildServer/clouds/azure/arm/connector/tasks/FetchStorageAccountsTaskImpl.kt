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
    override fun create(api: Azure, parameter: Unit): Single<List<StorageAccountTaskAccountDescriptor>> {
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
                            storageAccount.keys.map { it.value() }
                    )
                }
                .toList()
                .last()
                .toSingle()
    }
}
