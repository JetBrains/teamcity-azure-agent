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
import jetbrains.buildServer.serverSide.TeamCityProperties
import rx.Observable
import rx.Scheduler
import rx.Single
import rx.internal.util.SubscriptionList
import rx.subjects.PublishSubject
import rx.subjects.Subject
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

class AzureThrottlerTaskQueueImpl<A, I, P, T>(
        override val taskId: I,
        private val task: AzureThrottlerTask<A, P, T>,
        private val adapter: AzureThrottlerAdapter<A>,
        taskExecutionTimeType: AzureThrottlerTaskTimeExecutionType,
        private val defaultCacheTimeoutInSeconds: Long,
        private val taskCompletionResultNotifier: AzureThrottlerTaskCompletionResultNotifier,
        private val requestScheduler: Scheduler
) : AzureThrottlerTaskQueue<I, P, T> {
    private val myTaskQueue = ConcurrentLinkedQueue<QueueItem<T, P>>()
    private val myLastUpdatedDateTime = AtomicReference<LocalDateTime>(LocalDateTime.MIN)
    private val myCacheTimeoutInSeconds = AtomicLong(defaultCacheTimeoutInSeconds)
    private val myCallHistory = AzureThrottlerTaskQueueCallHistoryImpl()
    private var myTaskTimeExecutionType = AtomicReference(taskExecutionTimeType)
    private val myEnableRetryOnThrottle = AtomicBoolean(false)

    init {
        if (task is AzureThrottlerCacheableTask<A, P, T>) {
            task.setCacheTimeout(myCacheTimeoutInSeconds.get())
        }
    }

    override val timeExecutionType: AzureThrottlerTaskTimeExecutionType
        get() = myTaskTimeExecutionType.get()

    override val lastUpdatedDateTime: LocalDateTime
        get() = myLastUpdatedDateTime.get()

    override fun executeNext(): Boolean {
        val forceItem = myTaskQueue.firstOrNull { it.force }
        if (forceItem != null) {
            execute(forceItem)
            return true
        }
        val expiredItem = myTaskQueue.firstOrNull { it.timeToExecute <= LocalDateTime.now(Clock.systemUTC()) }
        if (expiredItem != null) {
            execute(expiredItem)
            return true
        }
        return false
    }

    override fun requestTask(flow: AzureThrottlerFlow, parameters: P): Single<AzureThrottlerAdapterResult<T>> {
        myCallHistory.addRequestCall()

        if (task is AzureThrottlerCacheableTask<A, P, T>) {
            if (flow == AzureThrottlerFlow.Normal && !isTimeToUpdate() || flow == AzureThrottlerFlow.Suspended) {
                LOG.debug("Trying to get data from cache for task $taskId, mode $flow.")
                val cacheValue = task.getFromCache(flow, parameters)
                if (cacheValue != null) {
                    LOG.debug("Returning value from cache for task $taskId, mode $flow.")
                    return Single.just(AzureThrottlerAdapterResult(cacheValue, null, true))
                }
            }
        }

        var timeToStart = LocalDateTime.now(Clock.systemUTC())
        if (flow == AzureThrottlerFlow.Suspended && myEnableRetryOnThrottle.get()) {
            timeToStart = timeToStart.plusSeconds(myCacheTimeoutInSeconds.get())
        }

        LOG.debug("There is no data in cache for task $taskId, mode $flow. Adding request to queue. Time to start: $timeToStart")

        val subject = PublishSubject.create<AzureThrottlerAdapterResult<T>>()
        myTaskQueue.add(QueueItem(
                timeToStart,
                parameters,
                false,
                true,
                subject,
                0,
                LocalDateTime.now(Clock.systemUTC())))
        return subject.toSingle()
    }

    override fun resetCache(source: AzureThrottlingSource) {
        if (task is AzureThrottlerCacheableTask<A, P, T>) {
            LOG.debug("Invalidating cache for $taskId task with timeout: $defaultCacheTimeoutInSeconds sec")
            task.invalidateCache()
            task.setCacheTimeout(defaultCacheTimeoutInSeconds)
        }
    }

    override fun setCacheTimeout(timeoutInSeconds: Long, source: AzureThrottlingSource) {
        val timeout = max(defaultCacheTimeoutInSeconds, timeoutInSeconds)
        if (myCacheTimeoutInSeconds.get() == timeout) {
            LOG.debug("New timeout $timeoutInSeconds was ignored for $taskId task (current value: $timeout)")
            return
        }

        LOG.debug("New timeout $timeoutInSeconds was accepted for $taskId task")

        myCacheTimeoutInSeconds.set(timeout)
        if (task is AzureThrottlerCacheableTask<A, P, T>) {
            task.setCacheTimeout(myCacheTimeoutInSeconds.get())
        }
    }

    override fun getStatistics(startDateTime: LocalDateTime): AzureThrottlerTaskQueueCallHistoryStatistics {
        return myCallHistory.getStatistics(startDateTime)
    }

    override fun enableRetryOnThrottle() {
        myEnableRetryOnThrottle.set(true)
    }

    private fun execute(item: QueueItem<T, P>) {
        val itemsToCombine = myTaskQueue
                .filter { it == item || it.canBeCombined && it.parameter == item.parameter }

        myTaskQueue.removeAll(itemsToCombine)

        val subjectSubscriptions = SubscriptionList()
        val resultSubject = PublishSubject.create<AzureThrottlerAdapterResult<T>>()
        itemsToCombine.map { resultSubject.subscribe(it.result) }.forEach { subjectSubscriptions.add(it) }

        val maxAttemptNo = itemsToCombine.map { it.attemptNo }.maxBy { it } ?: 0

        subjectSubscriptions.add(
            resultSubject
                    .doAfterTerminate { subjectSubscriptions.clear() }
                    .subscribe()
        )

        val resultSubscription = SubscriptionList()
        resultSubscription.add(adapter
                .execute { task.create(adapter.api, item.parameter) }
                .subscribeOn(requestScheduler)
                .doOnSuccess {
                    myLastUpdatedDateTime.set(LocalDateTime.now(Clock.systemUTC()))
                    myCallHistory.addExecutionCall(it.requestsCount)
                    taskCompletionResultNotifier.notifyCompleted((it.requestsCount ?: 0) > 0)

                    if (task is AzureThrottlerCacheableTask<A, P, T>) {
                        task.setToCache(item.parameter, it.value)
                    }
                }
                .onErrorResumeNext { error ->
                    if (error is AzureRateLimitReachedException) {
                        taskCompletionResultNotifier.notifyRateLimitReached(error.retryAfterTimeoutInSeconds)

                        val taskLiveTime = LocalDateTime.now(Clock.systemUTC()).toEpochSecond(ZoneOffset.UTC) - item.createdDate.toEpochSecond(ZoneOffset.UTC)
                        if (taskLiveTime >= getMaxTaskLiveTimeInSeconds()) {
                            val throttlerError = AzureMaxTaskLiveException("Task $taskId has not been executed for $taskLiveTime sec", error)
                            LOG.debug(throttlerError)
                            return@onErrorResumeNext Single.error<AzureThrottlerAdapterResult<T>>(throttlerError)
                        } else if (maxAttemptNo >= getMaxRetryCount()) {
                            val throttlerError = AzureMaxRetryCountException("Attempt count for task $taskId exceeded", error)
                            LOG.debug(throttlerError)
                            return@onErrorResumeNext Single.error<AzureThrottlerAdapterResult<T>>(throttlerError)
                        } else {
                            if (task is AzureThrottlerCacheableTask<A, P, T>) {
                                LOG.debug("Trying to get data from cache for task $taskId, mode ${AzureThrottlerFlow.Suspended}.")
                                val cacheValue = task.getFromCache(AzureThrottlerFlow.Suspended, item.parameter)
                                if (cacheValue != null) {
                                    LOG.debug("Returning value from cache for task $taskId, mode ${AzureThrottlerFlow.Suspended}.")
                                    return@onErrorResumeNext Single.just<AzureThrottlerAdapterResult<T>>(AzureThrottlerAdapterResult(cacheValue, null, true))
                                }
                            }

                            val timeToStart = LocalDateTime.now(Clock.systemUTC()).plusSeconds(error.retryAfterTimeoutInSeconds)
                            LOG.debug("Retry task $taskId due to Rate limit exception. Attempt no: ${maxAttemptNo + 1}. Time to start: $timeToStart")
                            myTaskQueue.add(QueueItem(
                                    timeToStart,
                                    item.parameter,
                                    item.force,
                                    item.canBeCombined,
                                    resultSubject,
                                    maxAttemptNo + 1,
                                    item.createdDate))

                            return@onErrorResumeNext Observable.never<AzureThrottlerAdapterResult<T>>().toSingle()
                        }
                    } else {
                        return@onErrorResumeNext Single.error<AzureThrottlerAdapterResult<T>>(error)
                    }
                }
                .doAfterTerminate { resultSubscription.clear() }
                .subscribe(resultSubject))
    }

    private fun getMaxTaskLiveTimeInSeconds(): Long {
        if (myEnableRetryOnThrottle.get())
            return TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_THROTTLER_QUEUE_MAX_TASK_LIVE_IN_SEC, 60 * 15)
        return Long.MAX_VALUE
    }

    private fun getMaxRetryCount(): Int {
        if (myEnableRetryOnThrottle.get())
            return TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_THROTTLER_QUEUE_MAX_RETRY_COUNT, 10)
        return 0
    }

    private fun isTimeToUpdate() = myLastUpdatedDateTime.get().plusSeconds(myCacheTimeoutInSeconds.get()) <= LocalDateTime.now(Clock.systemUTC())

    data class QueueItem<T, P>(
            val timeToExecute: LocalDateTime,
            val parameter: P,
            val force: Boolean,
            val canBeCombined: Boolean,
            val result: Subject<AzureThrottlerAdapterResult<T>, AzureThrottlerAdapterResult<T>>,
            val attemptNo: Int,
            val createdDate: LocalDateTime)

    companion object {
        private val LOG = Logger.getInstance(AzureThrottlerTaskQueueImpl::class.java.name)
    }
}
