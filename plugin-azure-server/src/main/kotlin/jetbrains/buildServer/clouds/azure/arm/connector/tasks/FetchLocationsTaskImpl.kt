package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

data class FetchLocationsTaskLocationDescriptor(val name: String, val displayName: String)

class FetchLocationsTaskImpl : AzureThrottlerCacheableTaskBaseImpl<Unit, List<FetchLocationsTaskLocationDescriptor>>() {
    override fun create(api: Azure, parameter: Unit): Single<List<FetchLocationsTaskLocationDescriptor>> {
        return api
                .subscriptions()
                .getByIdAsync(api.subscriptionId())
                .last()
                .map { subscription -> subscription.listLocations().map { FetchLocationsTaskLocationDescriptor(it.name(), it.displayName()) } }
                .toSingle()
    }
}
