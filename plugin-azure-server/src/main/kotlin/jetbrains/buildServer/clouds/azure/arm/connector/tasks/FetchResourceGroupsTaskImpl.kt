

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

class FetchResourceGroupsMapTaskImpl : AzureThrottlerCacheableTaskBaseImpl<Unit, Map<String, String>>() {
    override fun createQuery(api: AzureApi, taskContext: AzureTaskContext, parameter: Unit): Single<Map<String, String>> {
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
