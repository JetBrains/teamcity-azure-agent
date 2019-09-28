package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

data class FetchSubscriptionsTaskSubscriptionDescriptor(val subscriptionId: String, val displayName: String)

class FetchSubscriptionsTaskImpl : AzureThrottlerCacheableTaskBaseImpl<Unit, List<FetchSubscriptionsTaskSubscriptionDescriptor>>() {
    override fun create(api: Azure, parameter: Unit): Single<List<FetchSubscriptionsTaskSubscriptionDescriptor>> {
        return api
                .subscriptions()
                .listAsync()
                .map {
                    FetchSubscriptionsTaskSubscriptionDescriptor(
                            it.subscriptionId(),
                            it.displayName()
                    )
                }
                .toList()
                .last()
                .toSingle()
    }
}

