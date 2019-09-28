package jetbrains.buildServer.clouds.azure.arm.throttler

import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue

class AzureThrottlerTaskQueueCallHistoryImpl : AzureThrottlerTaskQueueCallHistory {
    private val historyTable = ConcurrentLinkedQueue<HistoryItem>()

    override fun addRequestCall() {
        historyTable.add(HistoryItem(LocalDateTime.now(Clock.systemUTC()), true, null))
        cleanup()
    }

    override fun addExecutionCall(readsCount: Long?) {
        historyTable.add(HistoryItem(LocalDateTime.now(Clock.systemUTC()), false, readsCount))
        cleanup()
    }

    override fun getStatistics(startDateTime: LocalDateTime): AzureThrottlerTaskQueueCallHistoryStatistics {
       var requestCallCount : Long? = null
        var executionCallCount : Long? = null
        var requestsCount : Long? = null
        historyTable
                .filter { it.dateTime >= startDateTime }
                .forEach {
                    requestCallCount = (requestCallCount ?: 0) + if (it.isRequestCall) 1 else 0
                    executionCallCount = (executionCallCount ?: 0) + if (it.isRequestCall) 0 else 1
                    requestsCount = if (it.readsCount != null) (requestsCount ?: 0) + it.readsCount else requestsCount
                }
        return AzureThrottlerTaskQueueCallHistoryStatistics(requestCallCount, executionCallCount, requestsCount)
    }

    private fun cleanup() {
        val currentDate = LocalDateTime.now(Clock.systemUTC()).minusHours(1)
        historyTable.removeAll(historyTable.filter { it.dateTime < currentDate })
    }

    data class HistoryItem(
            val dateTime: LocalDateTime,
            val isRequestCall: Boolean,
            val readsCount: Long?)
}
