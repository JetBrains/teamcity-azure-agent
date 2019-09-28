package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

class FetchResourceGroupsMapTaskImpl : AzureThrottlerCacheableTaskBaseImpl<Unit, Map<String, String>>() {
    override fun create(api: Azure, parameter: Unit): Single<Map<String, String>> {
        return api
                .resourceGroups()
                .listAsync()
                .toList()
                .last()
                .map { list ->
                    list.asSequence()
                            .sortedBy { it.name() }
                            .associateBy(
                                    { it.name() },
                                    { it.regionName() }
                            )
                }
                .toSingle()
    }
}
