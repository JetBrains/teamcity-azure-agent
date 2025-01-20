package jetbrains.buildServer.clouds.azure.arm.resourceGraph

import com.microsoft.azure.management.resources.fluentcore.model.HasInner

class QueryResponse(
    private val inner: QueryResponseInner
) : HasInner<QueryResponseInner> {
    val table: Table

    init {
        table = Table(inner.data!!)
    }

    override fun inner(): QueryResponseInner = inner

    val totalRecords: Long = inner.totalRecords

    val count: Long = inner.count

    val resultTruncated: String = inner.resultTruncated

    val skipToken: String? = inner.skipToken
}
