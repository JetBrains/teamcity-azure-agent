package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.resourceGraph.QueryRequest
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_TASKS_FETAHCSTORAGEACCOUNTS_RESOURCEGRAPH_DISABLE
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import jetbrains.buildServer.serverSide.TeamCityProperties
import rx.Single

data class StorageAccountTaskAccountDescriptor(
        val id: String,
        val name: String,
        val regionName: String,
        val resourceGroupName: String,
        val skuTypeName: String)

class FetchStorageAccountsTaskImpl : AzureThrottlerCacheableTaskBaseImpl<Unit, List<StorageAccountTaskAccountDescriptor>>() {
    override fun createQuery(api: AzureApi, taskContext: AzureTaskContext, parameter: Unit): Single<List<StorageAccountTaskAccountDescriptor>> =
        if (TeamCityProperties.getBoolean(TEAMCITY_CLOUDS_AZURE_TASKS_FETAHCSTORAGEACCOUNTS_RESOURCEGRAPH_DISABLE))
            createTask(api)
        else
            createResourceGraphTask(api)

    private fun createTask(api: AzureApi): Single<List<StorageAccountTaskAccountDescriptor>> =
        api
            .storageAccounts()
            .listAsync()
            .map { storageAccount ->
                StorageAccountTaskAccountDescriptor(
                    storageAccount.id(),
                    storageAccount.name(),
                    storageAccount.regionName(),
                    storageAccount.resourceGroupName(),
                    storageAccount.skuType().name().toString()
                )
            }
            .toList()
            .last()
            .toSingle()

    private fun createResourceGraphTask(api: AzureApi): Single<List<StorageAccountTaskAccountDescriptor>> =
        api
            .resourceGraph()
            .resources()
            .poolResourcesAsync(QueryRequest(FETCH_STORAGE_ACCOUNTS_SCRIPT))
            .flatMapIterable { table ->
                table.rows.map {
                    StorageAccountTaskAccountDescriptor(
                        id = it.getStringValue("id", isRequired = true)!!,
                        name = it.getStringValue("name", isRequired = true)!!,
                        regionName = it.getStringValue("location", isRequired = true)!!,
                        resourceGroupName = it.getStringValue("resourceGroup", isRequired = true)!!,
                        skuTypeName = it.getStringValue("skuTypeName", isRequired = true)!!
                    )
                }
            }
            .toList()
            .toSingle()

    companion object {
        private val LOG = Logger.getInstance(FetchStorageAccountsTaskImpl::class.java.name)
        private val FETCH_STORAGE_ACCOUNTS_SCRIPT = AzureUtils.getResourceAsString("/queries/fetch_storageAccounts.kusto")
    }
}
