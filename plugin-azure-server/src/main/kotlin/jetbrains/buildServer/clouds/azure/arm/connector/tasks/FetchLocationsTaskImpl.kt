package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

data class FetchLocationsTaskLocationDescriptor(val name: String, val displayName: String)

class FetchLocationsTaskImpl : AzureThrottlerCacheableTaskBaseImpl<Unit, List<FetchLocationsTaskLocationDescriptor>>() {
    override fun createQuery(api: AzureApi, taskContext: AzureTaskContext, parameter: Unit): Single<List<FetchLocationsTaskLocationDescriptor>> {
        return api
                .subscriptions()
                .getByIdAsync(api.subscriptionId())
                .last()
                .map { subscription -> subscription.listLocations().map { FetchLocationsTaskLocationDescriptor(it.name(), it.displayName()) } }
                .toSingle()
    }
}
