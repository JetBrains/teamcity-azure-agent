package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.resources.fluentcore.arm.Region
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

data class FetchServicesTaskServiceDescriptor(val namespace: String, val resourceTypes: List<String>)

class FetchServicesTaskImpl : AzureThrottlerCacheableTaskBaseImpl<String, List<FetchServicesTaskServiceDescriptor>>() {
    override fun create(api: Azure, parameter: String): Single<List<FetchServicesTaskServiceDescriptor>> {
        return api
                .providers()
                .listAsync()
                .filter { it.registrationState() == REGISTERED }
                .withLatestFrom(
                        api
                                .subscriptions()
                                .getByIdAsync(api.subscriptionId())
                                .map { it.getLocationByRegion(Region.fromName(parameter)).displayName() }
                ) {
                    provider, locationName ->
                        val resourceTypes =
                            provider
                                    .resourceTypes()
                                    .filter { it.locations().contains(locationName) }
                                    .map { it.resourceType() }
                    FetchServicesTaskServiceDescriptor(
                            provider.namespace(),
                            resourceTypes
                    )
                }
                .filter { it.resourceTypes.isNotEmpty() }
                .toList()
                .last()
                .toSingle()
    }

    companion object {
        private const val REGISTERED = "Registered"
    }
}
