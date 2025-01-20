package jetbrains.buildServer.clouds.azure.arm.resourceGraph

import com.microsoft.azure.AzureClient
import com.microsoft.azure.AzureServiceClient
import com.microsoft.rest.RestClient
import com.microsoft.rest.credentials.ServiceClientCredentials

class ResourceGraphClientImpl : AzureServiceClient {
    private var mySubscriptionId: String? = null
    private lateinit var resources: ResourceProvidersInner
    public lateinit var azureClient: AzureClient

    public val acceptLanguage: String = "en-US"

    public val apiVersion: String = "2021-03-01"

    public val subscriptionId: String?
        get() { return mySubscriptionId }

    constructor(credentials: ServiceClientCredentials) : this("https://management.azure.com", credentials)

    constructor(baseUrl: String, credentials: ServiceClientCredentials) : super(baseUrl, credentials) {
        initialize();
    }

    constructor(restClient: RestClient) : super(restClient) {
        initialize();
    }

    override fun userAgent(): String {
        return "${super.userAgent()} (ResourceGraphManager, $apiVersion)"
    }

    public fun resources(): ResourceProvidersInner = resources

    private fun initialize() {
        azureClient = AzureClient(this)
        resources = ResourceProvidersInner(restClient().retrofit(), this)
    }

    fun withSubscription(subscriptionId: String?) {
        mySubscriptionId = subscriptionId
    }
}
