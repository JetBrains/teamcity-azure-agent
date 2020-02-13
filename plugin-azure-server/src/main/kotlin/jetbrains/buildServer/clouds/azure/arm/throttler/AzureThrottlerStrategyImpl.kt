/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.azure.arm.throttler

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.floor
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
    private val myLock = ReentrantLock()

    override fun getFlow(): AzureThrottlerFlow {
        return myFlow.get()
    }

    override fun applyTaskChanges() {
        myLock.lock();
        try {
            val taskList = taskContainer.getTaskList()

            val currentResourceRequestsCount = adapter.getDefaultReads()
            val operableResourceRequestsCount = afterReservation(currentResourceRequestsCount)

            val currentRemainingRequestsCount = adapter.getRemainingReads()
            val operableRemainingRequestsCount = afterReservation(currentRemainingRequestsCount)

            val windowStartTime = adapter.getWindowStartDateTime()
            val windowWidthInMs = adapter.getWindowWidthInMilliseconds()

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
                            if (executionCallCount > 0 && resourceRequestsCount > 0) {
                                val callCount = floor(1.0 * remainingPeriodicalResourceRequestsCountPerTask * executionCallCount / resourceRequestsCount).toLong()
                                val taskTimeout = windowWidthInMs / (1000 * (callCount + 1))
                                LOG.info("Trying to set cache timeout for periodical task ${task.taskId} to $taskTimeout sec")
                                task.setCacheTimeout(taskTimeout, AzureThrottlingSource.Throttler)
                            } else {
                                LOG.info("Trying to reset cache timeout for periodical task ${task.taskId}")
                                task.setCacheTimeout(0, AzureThrottlingSource.Throttler)
                            }
                        }
                    }
                }
            }

            var adapterThrottlerTimeInMs = 0L
            if (currentRemainingRequestsCount > 0
                    && currentResourceRequestsCount > 0
                    && currentRemainingRequestsCount * 100 / currentResourceRequestsCount <= 100 - enableAggressiveThrottlingWhenReachLimitInPercents) {
                adapterThrottlerTimeInMs = windowWidthInMs / currentRemainingRequestsCount
            } else if (operableRemainingRequestsCount == 0L) {
                adapterThrottlerTimeInMs = windowWidthInMs
            }
            adapter.setThrottlerTime(adapterThrottlerTimeInMs)
        }
        finally {
            myLock.unlock()
        }
    }

    override fun notifyCompleted() {
        myLock.lock();
        try {
            if (myFlow.get() == AzureThrottlerFlow.Suspended) {
                LOG.info("Task completed successfully, switching Suspended state to Normal")

                myFlow.set(AzureThrottlerFlow.Normal)

                val taskList = taskContainer.getTaskList()
                for (task in taskList) {
                    task.resetCache(AzureThrottlingSource.Throttler)
                }
            }
        }
        finally {
            myLock.unlock()
        }
    }

    override fun notifyRateLimitReached(retryAfterTimeoutInSeconds: Long) {
        myLock.lock()
        try {
            LOG.info("Task completed with Rate Limit Reached exception, switching ${myFlow.get()} state to Suspended")

            myFlow.set(AzureThrottlerFlow.Suspended)

            val timeout = retryAfterTimeoutInSeconds + RETRY_AFTER_TIMEOUT_DELTA
            val taskList = taskContainer.getTaskList()
            for (task in taskList) {
                task.setCacheTimeout(timeout, AzureThrottlingSource.Adapter)
            }
        }
        finally {
            myLock.unlock()
        }
    }

    override fun setContainer(container: AzureThrottlerStrategyTaskContainer<I>) {
        taskContainer = container
    }

    private fun afterReservation(value: Long) : Long {
        return (value * (1 - resourceReservationInPercents/100.0)).roundToLong()
    }

    companion object {
        private val LOG = Logger.getInstance(AzureThrottlerStrategyImpl::class.java.name)
        private const val RETRY_AFTER_TIMEOUT_DELTA = 5
    }
}
