package jetbrains.buildServer.clouds.azure.arm.resourceGraph

import com.microsoft.azure.AzureEnvironment
import com.microsoft.azure.AzureResponseBuilder
import com.microsoft.azure.credentials.AzureTokenCredentials
import com.microsoft.azure.management.resources.fluentcore.arm.AzureConfigurable
import com.microsoft.azure.management.resources.fluentcore.arm.implementation.AzureConfigurableImpl
import com.microsoft.azure.management.resources.fluentcore.utils.ProviderRegistrationInterceptor
import com.microsoft.azure.management.resources.fluentcore.utils.ResourceManagerThrottlingInterceptor
import com.microsoft.azure.serializer.AzureJacksonAdapter
import com.microsoft.rest.RestClient

class ResourceGraph(
    private val restClient: RestClient,
    private val subscriptionId: String?
) {
    private var resourceGraphClient: ResourceGraphClientImpl

    init {
        resourceGraphClient = ResourceGraphClientImpl(restClient)
        resourceGraphClient.withSubscription(subscriptionId)
    }

    fun resources() : ResourceProvidersInner = resourceGraphClient.resources()

    interface Authenticated {
        fun withSubscription(subscriptionId: String?) : ResourceGraph
    }

    interface Configurable : AzureConfigurable<Configurable> {
        fun authenticate(credentials: AzureTokenCredentials): Authenticated
    }

    private class AuthenticatedImpl(
        private val restClient: RestClient
    ) : Authenticated {
        override fun withSubscription(subscriptionId: String?) : ResourceGraph = ResourceGraph(restClient, subscriptionId)
    }

    private class ConfigurableImpl : AzureConfigurableImpl<Configurable>(), Configurable {
        override fun authenticate(credentials: AzureTokenCredentials): Authenticated = ResourceGraph.authenticate(buildRestClient(credentials))
    }

    companion object {
        fun authenticate(credentials: AzureTokenCredentials) : Authenticated =
            AuthenticatedImpl(
                RestClient.Builder()
                    .withBaseUrl(credentials.environment(), AzureEnvironment.Endpoint.RESOURCE_MANAGER)
                    .withCredentials(credentials)
                    .withSerializerAdapter(AzureJacksonAdapter())
                    .withResponseBuilderFactory(AzureResponseBuilder.Factory())
                    .withInterceptor(ProviderRegistrationInterceptor(credentials))
                    .withInterceptor(ResourceManagerThrottlingInterceptor())
                    .build()
            )

        fun authenticate(restClient: RestClient): Authenticated =
            AuthenticatedImpl(restClient)
    }
}
