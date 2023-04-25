package jetbrains.buildServer.clouds.azure.arm.resourceGraph

import com.fasterxml.jackson.annotation.JsonProperty

class QueryRequestOptions {
    @JsonProperty(value = "\$skipToken")
    var skipToken: String? = null

    @JsonProperty(value = "\$top")
    var top: Int? = null

    @JsonProperty(value = "\$skip")
    var skip: Int? = null

    //'table', 'objectArray'
    @JsonProperty(value = "resultFormat")
    val resultFormat: String = "table"
}
