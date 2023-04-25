package jetbrains.buildServer.clouds.azure.arm.resourceGraph

import com.fasterxml.jackson.annotation.JsonProperty

class QueryResponseInner {
    @JsonProperty(value = "totalRecords", required = true)
    var totalRecords: Long = 0

    @JsonProperty(value = "count", required = true)
    var count: Long = 0

    @JsonProperty(value = "resultTruncated", required = true)
    var resultTruncated: String = "false"

    @JsonProperty(value = "\$skipToken")
    var skipToken: String? = null

    @JsonProperty(value = "data", required = true)
    var data: TableInner? = null

//    @JsonProperty(value = "facets")
//    var facets: List<Facet>? = null
}
