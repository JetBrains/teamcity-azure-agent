/*
 * Copyright 2000-2021 JetBrains s.r.o.
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
import com.microsoft.azure.CloudException
import jetbrains.buildServer.serverSide.TeamCityProperties
import rx.Observable
import rx.Scheduler
import rx.Single
import rx.internal.util.SubscriptionList
import rx.subjects.PublishSubject
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

class AzureThrottlerTaskQueueImpl<A, I, P, T>(
        override val taskId: I,
        private val requestQueue: AzureThrottlerRequestQueue<I, P, T>,
        private val task: AzureThrottlerTask<A, P, T>,
        private val adapter: AzureThrottlerAdapter<A>,
        taskExecutionTimeType: AzureThrottlerTaskTimeExecutionType,
        private val defaultCacheTimeoutInSeconds: Long,
        private val taskCompletionResultNotifier: AzureThrottlerTaskCompletionResultNotifier,
        private val requestScheduler: Scheduler
) : AzureThrottlerTaskQueue<I, P, T> {
    private val myLastUpdatedDateTime = AtomicReference<LocalDateTime>(LocalDateTime.MIN)
    private val myCacheTimeoutInSeconds = AtomicLong(defaultCacheTimeoutInSeconds)
    private val myCallHistory = AzureThrottlerTaskQueueCallHistoryImpl()
    private var myTaskTimeExecutionType = AtomicReference(taskExecutionTimeType)
    private val myEnableRetryOnThrottle = AtomicBoolean(false)
    private val mySubscriptions = SubscriptionList()
    private val myRetryAfterTime = AtomicReference<LocalDateTime?>(null)

    private val name: String
        get() = adapter.name

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
        val retryAfterTime = myRetryAfterTime.get()
        if (retryAfterTime != null && retryAfterTime.isAfter(LocalDateTime.now(Clock.systemUTC())))
            return false

        var batch = requestQueue.extractNextBatch()
        if (batch.count() > 0) {
            execute(batch)
            return true
        }
        return false
    }

    override fun requestTask(flow: AzureThrottlerFlow, parameters: P): Single<AzureThrottlerAdapterResult<T>> {
        myCallHistory.addRequestCall()

        var timeToStart = LocalDateTime.now(Clock.systemUTC())
        if (task is AzureThrottlerCacheableTask<A, P, T>) {
            LOG.debug("[$name] Trying to get data from cache for task $taskId, mode $flow.")
            val cacheValue = task.getFromCache(parameters)
            if (cacheValue != null) {
                if (isTimeToUpdate()) {
                    LOG.debug("[$name] Prefetching data for task $taskId.")
                    val subject = PublishSubject.create<AzureThrottlerAdapterResult<T>>()
                    val subscriptionList = SubscriptionList()
                    subscriptionList.add(
                            subject
                                    .doAfterTerminate {
                                        LOG.debug("[$name] Prefetching data for task $taskId done.")
                                        subscriptionList.clear()
                                    }
                                    .subscribe({}, {})
                    )
                    requestQueue.addRequest(
                            timeToStart,
                            parameters,
                            subject,
                            true,
                            true
                    )
                }
                LOG.debug("[$name] Returning value from cache for task $taskId, mode $flow.")
                return Single.just<AzureThrottlerAdapterResult<T>>(AzureThrottlerAdapterResult(cacheValue, null, true))
            }
        }

        LOG.debug("[$name] There is no data in cache for task $taskId, mode $flow. Adding request to queue. Time to start: $timeToStart")

        val subject = PublishSubject.create<AzureThrottlerAdapterResult<T>>()
        requestQueue.addRequest(
                timeToStart,
                parameters,
                subject,
                false,
                true)
        return subject.toSingle()
    }

    override fun setCacheTimeout(timeoutInSeconds: Long, source: AzureThrottlingSource) {
        val timeout = max(defaultCacheTimeoutInSeconds, timeoutInSeconds)
        if (myCacheTimeoutInSeconds.get() == timeout) {
            LOG.debug("[$name] New timeout $timeoutInSeconds was ignored for $taskId task (current value: $timeout)")
            return
        }

        LOG.debug("[$name] New timeout $timeoutInSeconds was accepted for $taskId task")

        myCacheTimeoutInSeconds.set(timeout)
        if (task is AzureThrottlerCacheableTask<A, P, T>) {
            task.setCacheTimeout(myCacheTimeoutInSeconds.get())
        }
    }

    override fun getCacheTimeout(): Long {
        return myCacheTimeoutInSeconds.get()
    }

    override fun getStatistics(startDateTime: LocalDateTime): AzureThrottlerTaskQueueCallHistoryStatistics {
        return myCallHistory.getStatistics(startDateTime)
    }

    override fun enableRetryOnThrottle() {
        myEnableRetryOnThrottle.set(true)
    }

    override fun notifyRateLimitReached(retryAfterTimeoutInSeconds: Long) {
        myRetryAfterTime.set(LocalDateTime.now(Clock.systemUTC()).plusSeconds(retryAfterTimeoutInSeconds));
    }

    override fun notifyCompleted(performedRequests: Boolean) {
        myRetryAfterTime.set(null)
    }

    private fun execute(requestBatch: AzureThrottlerRequestBatch<P, T>) {
        LOG.debug("[$name] Start executing batch of tasks. TaskId: ${taskId}, Task count: ${requestBatch.count()}")

        val resultSubject = PublishSubject.create<AzureThrottlerAdapterResult<T>>()
        requestBatch.subscribeTo(resultSubject, mySubscriptions)

        val hasForceItem = requestBatch.hasForceRequest()
        if (task is AzureThrottlerCacheableTask<A, P, T>) {
            if (hasForceItem && !task.checkThrottleTime(requestBatch.parameter)) {
                LOG.debug("[$name] Force update for task cached task $taskId.")
            } else {
                val cacheValue = task.getFromCache(requestBatch.parameter)
                if (cacheValue != null && !task.needCacheUpdate(requestBatch.parameter)) {
                    LOG.debug("[$name] Updating queue items from cache for task $taskId. Count: ${requestBatch.count()}")

                    val cacheSubscription = SubscriptionList()
                    mySubscriptions.add(cacheSubscription)
                    cacheSubscription.add(Single
                            .just<AzureThrottlerAdapterResult<T>>(AzureThrottlerAdapterResult(cacheValue, null, true))
                            .subscribeOn(requestScheduler)
                            .doOnUnsubscribe { mySubscriptions.remove(cacheSubscription) }
                            .subscribe(resultSubject))
                    return
                }
            }
        }

        val resultSubscription = SubscriptionList()
        mySubscriptions.add(resultSubscription)
        resultSubscription.add(adapter
                .execute { task.create(adapter.api, requestBatch.parameter) }
                .doOnEach { LOG.debug("[$name] [$taskId] Received task notification. Kind: ${it.kind}") }
                .subscribeOn(requestScheduler)
                .doOnSuccess {
                    myLastUpdatedDateTime.set(LocalDateTime.now(Clock.systemUTC()))
                    myCallHistory.addExecutionCall(it.requestsCount)
                    taskCompletionResultNotifier.notifyCompleted((it.requestsCount ?: 0) > 0)

                    postProcessQueue(requestBatch.parameter, it)
                }
                .onErrorResumeNext { throwable ->
                    LOG.warnAndDebugDetails("[$name] [$taskId] Received error:", throwable)
                    var error = throwable
                    if (error !is CloudException && error is RuntimeException) {
                        if (error.cause != null) {
                            error = error.cause
                        }
                    }
                    if (error is ThrottlerRateLimitReachedException) {
                        myCallHistory.addExecutionCall(error.requestSequenceLength)
                        taskCompletionResultNotifier.notifyRateLimitReached(error.retryAfterTimeoutInSeconds)

                        val taskLiveTime = LocalDateTime.now(Clock.systemUTC()).toEpochSecond(ZoneOffset.UTC) - requestBatch.getMinCreatedDate().toEpochSecond(ZoneOffset.UTC)
                        if (taskLiveTime >= getMaxTaskLiveTimeInSeconds()) {
                            val throttlerError = ThrottlerMaxTaskLiveException("Task $taskId has not been executed for $taskLiveTime sec", error)
                            LOG.warnAndDebugDetails("[$name] Task $taskId has not been executed for $taskLiveTime sec", throttlerError)
                            return@onErrorResumeNext Single.error<AzureThrottlerAdapterResult<T>>(throttlerError)
                        } else if (requestBatch.getMaxAttempNo() >= getMaxRetryCount()) {
                            val throttlerError = ThrottlerMaxRetryCountException("Attempt count for task $taskId exceeded", error)
                            LOG.warnAndDebugDetails("[$name] Attempt count for task $taskId exceeded", throttlerError)
                            return@onErrorResumeNext Single.error<AzureThrottlerAdapterResult<T>>(throttlerError)
                        } else {
                            if (task is AzureThrottlerCacheableTask<A, P, T>) {
                                LOG.debug("[$name] Trying to get data from cache for task $taskId, mode ${AzureThrottlerFlow.Suspended}.")
                                val cacheValue = task.getFromCache(requestBatch.parameter)
                                if (cacheValue != null) {
                                    LOG.debug("[$name] Returning value from cache for task $taskId, mode ${AzureThrottlerFlow.Suspended}.")
                                    return@onErrorResumeNext Single.just<AzureThrottlerAdapterResult<T>>(AzureThrottlerAdapterResult(cacheValue, null, true))
                                }
                            }

                            val timeToStart = LocalDateTime.now(Clock.systemUTC()).plusSeconds(error.retryAfterTimeoutInSeconds)
                            LOG.warn("[$name] Retry task $taskId due to Rate limit exception. Attempt no: ${requestBatch.getMaxAttempNo() + 1}. Time to start: $timeToStart")
                            requestQueue.addRequest(
                                    timeToStart,
                                    requestBatch.parameter,
                                    resultSubject,
                                    requestBatch.hasForceRequest(),
                                    requestBatch.canBeCombined(),
                                    requestBatch.getMaxAttempNo() + 1,
                                    requestBatch.getMinCreatedDate()
                            )

                            mySubscriptions.remove(resultSubscription);

                            return@onErrorResumeNext Observable.never<AzureThrottlerAdapterResult<T>>().toSingle()
                        }
                    } else {
                        return@onErrorResumeNext Single.error<AzureThrottlerAdapterResult<T>>(error)
                    }
                }
                .doAfterTerminate { mySubscriptions.remove(resultSubscription) }
                .subscribe(resultSubject))
    }

    private fun postProcessQueue(parameter: P, result: AzureThrottlerAdapterResult<T>) {
        val requestBatch = requestQueue.extractBatchFor(parameter)
        if (requestBatch.count() > 0) {
            LOG.debug("[$name] Post-processing queue items for task $taskId. Count: ${requestBatch.count()}")

            var source = Observable
                    .just<AzureThrottlerAdapterResult<T>>(result)
                    .subscribeOn(requestScheduler)
            requestBatch.subscribeTo(source, mySubscriptions)
        }
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

    companion object {
        private val LOG = Logger.getInstance(AzureThrottlerTaskQueueImpl::class.java.name)
    }
}
