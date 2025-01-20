package jetbrains.buildServer.clouds.azure.arm.resourceGraph

import com.fasterxml.jackson.annotation.JsonProperty

class QueryRequest(
    query: String
) {
    @JsonProperty(value = "subscriptions", required = true)
    var subscriptions: List<String>? = null

    @JsonProperty(value = "query", required = true)
    var query: String = query

    @JsonProperty(value = "options")
    var options: QueryRequestOptions? = QueryRequestOptions()

    fun withSubscriptions(subscriptions: List<String>): QueryRequest {
        val result = QueryRequest(query)
        result.options = options
        result.subscriptions = subscriptions
        return result
    }
}

