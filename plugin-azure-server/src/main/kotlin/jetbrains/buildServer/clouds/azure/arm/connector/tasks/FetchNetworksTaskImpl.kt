

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

data class FetchNetworksTaskNetworkDescriptor(val id: String, val regionName: String, val subnets: List<String>)

class FetchNetworksTaskImpl : AzureThrottlerCacheableTaskBaseImpl<Unit, List<FetchNetworksTaskNetworkDescriptor>>() {
    override fun createQuery(api: AzureApi, taskContext: AzureTaskContext, parameter: Unit): Single<List<FetchNetworksTaskNetworkDescriptor>> {
        return api
                .networks()
                .listAsync()
                .map { FetchNetworksTaskNetworkDescriptor(it.id(), it.regionName(), it.subnets().keys.toList())}
                .toList()
                .last()
                .toSingle()
    }
}
