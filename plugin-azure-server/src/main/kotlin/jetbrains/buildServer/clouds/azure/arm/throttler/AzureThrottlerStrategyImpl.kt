package jetbrains.buildServer.clouds.azure.arm.throttler

import com.intellij.openapi.diagnostic.Logger
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong

class AzureThrottlerStrategyImpl<A, I>(
        private val adapter: AzureThrottlerAdapter<A>,
        private val randomTasksResourceReservationInPercents: Int,
        private val resourceReservationInPercents: Int,
        private val enableAggressiveThrottlingWhenReachLimitInPercents: Int
) : AzureThrottlerStrategy<I> {
    private val myFlow = AtomicReference(AzureThrottlerFlow.Normal)
    private lateinit var taskContainer: AzureThrottlerStrategyTaskContainer<I>

    override fun getFlow(): AzureThrottlerFlow {
        return myFlow.get()
    }

    override fun applyTaskChanges() {
        val taskList = taskContainer.getTaskList()

        val currentResourceRequestsCount = adapter.getDefaultReads()
        val operableResourceRequestsCount = (currentResourceRequestsCount * (1 - resourceReservationInPercents/100.0)).roundToLong()

        val currentRemainingRequestsCount = adapter.getRemainingReads()
        val operableRemainingRequestsCount = (currentRemainingRequestsCount * (1 - resourceReservationInPercents/100.0)).roundToLong()

        val windowStartTime = adapter.getWindowStartDateTime()
        val windowWidthInSeconds = adapter.getWindowWidthInSeconds()
        val windowTimePassedInSeconds = max(1, LocalDateTime.now(Clock.systemUTC()).toEpochSecond(ZoneOffset.UTC) - windowStartTime.toEpochSecond(ZoneOffset.UTC))

        if (operableRemainingRequestsCount > 0) {
            val randomTasksStatistics = taskList
                    .filter { it.timeExecutionType == AzureThrottlerTaskTimeExecutionType.Random }
                    .map { it.getStatistics(windowStartTime) }

            val currentRandomResourceRequestsCount = randomTasksStatistics.map { it.resourceRequestsCount ?: 0 }.sum()
            val totalRandomResourceRequestsReservation = max(
                    currentRandomResourceRequestsCount,
                    operableResourceRequestsCount * randomTasksResourceReservationInPercents / 100)
            val remainingRandomResourceRequestsReservation = max(totalRandomResourceRequestsReservation - currentRandomResourceRequestsCount, 0)
            val remainingPeriodicalResourceRequestsCount = operableRemainingRequestsCount - remainingRandomResourceRequestsReservation

            if (remainingPeriodicalResourceRequestsCount > 0) {
                val periodicalTasksStatistics = taskList
                        .filter { it.timeExecutionType == AzureThrottlerTaskTimeExecutionType.Periodical }
                        .map { it to it.getStatistics(windowStartTime) }
                        .sortedBy { it.second.resourceRequestsCount ?: 0 }

                if (periodicalTasksStatistics.isNotEmpty()) {
                    val remainingPeriodicalResourceRequestsCountPerTask = remainingPeriodicalResourceRequestsCount / periodicalTasksStatistics.size

                    for ((task, statistics) in periodicalTasksStatistics) {
                        val executionCallCount = statistics.executionCallCount ?: 0
                        val resourceRequestsCount = statistics.resourceRequestsCount ?: 0
                        if (executionCallCount > 0) {
                            val callCount = ceil(remainingPeriodicalResourceRequestsCountPerTask / (1.0 * resourceRequestsCount / executionCallCount)).toLong()
                            val taskTimeout = windowWidthInSeconds / (callCount + 1)
                            LOG.info("Trying to set cache timeout for periodical task ${task.taskId} to $taskTimeout sec")
                            task.setCacheTimeout(taskTimeout, AzureThrottlingSource.Throttler)
                        }
                    }
                }
            }
        }

        if (currentRemainingRequestsCount > 0 && currentResourceRequestsCount > 0 && currentRemainingRequestsCount * 100 / currentResourceRequestsCount <= 100 - enableAggressiveThrottlingWhenReachLimitInPercents) {
            val timeoutInMilliseconds = 1000 * windowWidthInSeconds / currentRemainingRequestsCount
            adapter.setThrottlerTime(timeoutInMilliseconds)
        } else {
            adapter.setThrottlerTime(0)
        }
    }

    override fun notifyCompleted() {
        if (myFlow.get() == AzureThrottlerFlow.Suspended) {
            LOG.info("Task completed successfully, switching Suspended state to Normal")

            myFlow.set(AzureThrottlerFlow.Normal)

            val taskList = taskContainer.getTaskList()
            for(task in taskList) {
                task.setCacheTimeout(0, AzureThrottlingSource.Throttler)
                task.invalidateCache()
            }
        }
    }

    override fun notifyRateLimitReached(retryAfterTimeoutInSeconds: Long) {
        LOG.info("Task completed with Rate Limit Reached exception, switching ${myFlow.get()} state to Suspended")

        myFlow.set(AzureThrottlerFlow.Suspended)

        val taskList = taskContainer.getTaskList()
        for(task in taskList) {
            task.setCacheTimeout(retryAfterTimeoutInSeconds + RETRY_AFTER_TIMEOUT_DELTA, AzureThrottlingSource.Adapter)
        }
    }

    override fun setContainer(container: AzureThrottlerStrategyTaskContainer<I>) {
        taskContainer = container
    }

    companion object {
        private val LOG = Logger.getInstance(AzureThrottlerStrategyImpl::class.java.name)
        private const val RETRY_AFTER_TIMEOUT_DELTA = 5
    }
}
