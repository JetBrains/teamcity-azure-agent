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

import rx.Observable
import rx.Single
import rx.internal.util.SubscriptionList
import rx.subjects.Subject
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

enum class AzureThrottlerTaskTimeExecutionType {
    Periodical,
    Random
}

enum class AzureThrottlingSource {
    Adapter,
    Throttler
}

enum class AzureThrottlerFlow {
    Normal,
    Suspended
}

interface AzureThrottlerCacheableTask<A, P, T> : AzureThrottlerTask<A, P, T> {
    fun getFromCache(parameter: P): T?
    fun invalidateCache()
    fun setCacheTimeout(timeoutInSeconds: Long)
    fun needCacheUpdate(parameter: P): Boolean
    fun checkThrottleTime(parameter: P): Boolean
}

interface AzureTaskContext {
    val corellationId: String

    fun apply()
    fun getRequestSequenceLength(): Long
    fun increaseRequestsSequenceLength()
}

interface AzureTaskContextProvider {
    fun getContext(): AzureTaskContext?
}

interface AzureThrottlerTask<A, P, T> :  AzureThrottlerTaskParameterEqualityComparer<P> {
    fun create(api: A, taskContext: AzureTaskContext, parameter: P): Single<T>
}

interface AzureThrottlerTaskParameterEqualityComparer<P> {
    fun areParametersEqual(parameter: P, other: P): Boolean
}

interface AzureTaskDescriptor<A, I, P, T> {
    val taskId: I
    fun create(taskNotifications: AzureTaskNotifications): AzureThrottlerTask<A, P, T>
}

interface AzureThrottler<A, I> {
    fun <P, T> registerTask(taskId: I, task : AzureThrottlerTask<A, P, T>, taskTimeExecutionType: AzureThrottlerTaskTimeExecutionType, defaultTimeoutInSeconds: Long) : AzureThrottler<A, I>
    fun <P, T> registerTask(taskDescriptor: AzureTaskDescriptor<A, I, P, T>, taskTimeExecutionType: AzureThrottlerTaskTimeExecutionType, defaultTimeoutInSeconds: Long) : AzureThrottler<A, I>

    fun <P, T> executeTask(taskId: I, parameters: P) : Single<T>
    fun <P, T> executeTask(taskDescriptor: AzureTaskDescriptor<A, I, P, T>, parameters: P) : Single<T>

    fun <P, T> executeTaskWithTimeout(taskDescriptor: AzureTaskDescriptor<A, I, P, T>, parameters: P) : Single<T>
    fun <P, T> executeTaskWithTimeout(taskDescriptor: AzureTaskDescriptor<A, I, P, T>, parameters: P, timeout: Long, timeUnit: TimeUnit) : Single<T>

    fun isSuspended() : Boolean

    fun start() : Boolean
    fun stop()
}

interface AzureThrottlerScheduledExecutor {
    fun start(): Boolean
    fun stop()
}

interface AzureThrottlerScheduledExecutorFactorty {
    fun create(scheduledAction: () -> Unit) : AzureThrottlerScheduledExecutor
}

interface AzureThrottlerStrategyTaskContainer<I> {
    fun getTaskList(): List<AzureThrottlerStrategyTask<I>>
}

interface AzureThrottlerStrategyTask<I> : AzureThrottlerTaskCompletionResultNotifier {
    val taskId: I
    val lastUpdatedDateTime : LocalDateTime
    val timeExecutionType : AzureThrottlerTaskTimeExecutionType
    fun getStatistics(startDateTime: LocalDateTime): AzureThrottlerTaskQueueCallHistoryStatistics
    fun setCacheTimeout(timeoutInSeconds: Long, source: AzureThrottlingSource)
    fun getCacheTimeout() : Long
    fun enableRetryOnThrottle()
}

interface AzureThrottlerStrategy<I> : AzureThrottlerTaskCompletionResultNotifier {
    fun getFlow(): AzureThrottlerFlow
    fun applyTaskChanges()
    fun setContainer(container: AzureThrottlerStrategyTaskContainer<I>)
}

interface AzureThrottlerTaskQueueExecutor {
    val lastUpdatedDateTime : LocalDateTime
    fun executeNext(): Boolean
}

interface AzureThrottlerTaskQueueRequestor<P, T> {
    fun requestTask(flow: AzureThrottlerFlow, parameters: P): Single<AzureThrottlerAdapterResult<T>>
}

interface AzureThrottlerTaskQueue<I, P, T> : AzureThrottlerTaskQueueExecutor, AzureThrottlerTaskQueueRequestor<P, T>, AzureThrottlerStrategyTask<I> {
}

interface AzureThrottlerRequestQueue<I, P, T> {
    fun addRequest(timeToExecute: LocalDateTime,
                   parameter: P,
                   result: Subject<AzureThrottlerAdapterResult<T>, AzureThrottlerAdapterResult<T>>,
                   force: Boolean = false,
                   canBeCombined: Boolean = true,
                   attemptNo: Int = 0,
                   createdDate: LocalDateTime = LocalDateTime.now(Clock.systemUTC()))
    fun extractNextBatch(): AzureThrottlerRequestBatch<P, T>
    fun extractBatchFor(parameter: P): AzureThrottlerRequestBatch<P, T>
}

interface AzureThrottlerRequestBatch<P, T> {
    val parameter: P
    fun count(): Int
    fun hasForceRequest(): Boolean
    fun canBeCombined(): Boolean
    fun getMaxAttempNo(): Int
    fun getMinCreatedDate(): LocalDateTime
    fun subscribeTo(source: Observable<AzureThrottlerAdapterResult<T>>, anchor: SubscriptionList)
}

interface AzureThrottlerTaskQueueCallHistory {
    fun addRequestCall()
    fun addExecutionCall(readsCount: Long?)
    fun getStatistics(startDateTime: LocalDateTime): AzureThrottlerTaskQueueCallHistoryStatistics
}

data class AzureThrottlerTaskQueueCallHistoryStatistics(
        val requestCallCount: Long?,
        val executionCallCount: Long?,
        val resourceRequestsCount: Long?)

data class AzureThrottlerAdapterResult<T>(val value: T?, val requestsCount: Long?, val fromCache: Boolean)

interface AzureThrottlerAdapter<A> : AzureThrottlerAdapterRemainingReadsNotifier, AzureTaskContextProvider {
    val api: A
    val name: String
    fun setThrottlerTime(milliseconds: Long)
    fun getThrottlerTime(): Long
    fun getWindowWidthInMilliseconds(): Long
    fun getWindowStartDateTime(): LocalDateTime
    fun getRemainingReads(): Long
    fun getDefaultReads(): Long
    fun <T> execute(queryFactory: (A, AzureTaskContext) -> Single<T>): Single<AzureThrottlerAdapterResult<T>>
    fun logDiagnosticInfo()
}

interface AzureThrottlerAdapterRemainingReadsNotifier {
    fun notifyRemainingReads(value: Long?, requestCount: Long)
}

interface AzureThrottlerTaskCompletionResultNotifier {
    fun notifyRateLimitReached(retryAfterTimeoutInSeconds: Long)
    fun notifyCompleted(performedRequests: Boolean)
}

interface AzureThrottlerRequestSync {
    fun waitForNextTimeSlot()
}

interface AzureThrottlerSleeper {
    fun sleep(millis: Long)
}

class ThrottlerRateLimitReachedException(val retryAfterTimeoutInSeconds: Long, val requestSequenceLength: Long?, msg: String? = null, cause: Throwable? = null): Exception(msg, cause)

open class ThrottlerExecutionTaskException(msg: String? = null, cause: Throwable? = null): Exception(msg, cause)
class ThrottlerMaxTaskLiveException(msg: String? = null, cause: Throwable? = null): ThrottlerExecutionTaskException(msg, cause)
class ThrottlerMaxRetryCountException(msg: String? = null, cause: Throwable? = null): ThrottlerExecutionTaskException(msg, cause)
class ThrottlerTimeoutException(msg: String? = null, cause: Throwable? = null): ThrottlerExecutionTaskException(msg, cause)
