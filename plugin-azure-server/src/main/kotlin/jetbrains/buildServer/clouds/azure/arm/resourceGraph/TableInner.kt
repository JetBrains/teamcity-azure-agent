package jetbrains.buildServer.clouds.azure.arm.resourceGraph

import com.fasterxml.jackson.annotation.JsonProperty

class TableInner(
    @JsonProperty(value = "columns", required = true)
    val columns: List<TableColumnInner>,

    @JsonProperty(value = "rows", required = true)
    val rows: List<List<Any>>
)

class TableColumnInner(
    @JsonProperty(value = "name", required = true)
    val name: String,

    @JsonProperty(value = "type", required = true)
    val type: String
)
