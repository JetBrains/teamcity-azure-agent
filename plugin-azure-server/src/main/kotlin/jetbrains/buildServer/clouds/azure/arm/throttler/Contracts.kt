package jetbrains.buildServer.clouds.azure.arm.throttler

import rx.Single
import java.time.LocalDateTime

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
    fun getFromCache(flow: AzureThrottlerFlow, parameter: P): T?
    fun setToCache(parameter: P, value: T?)
    fun invalidateCache()
    fun setCacheTimeout(timeoutInSeconds: Long)
}

interface AzureThrottlerTask<A, P, T> {
    fun create(api: A, parameter: P): Single<T>
}

interface AzureTaskDescriptor<A, I, P, T> {
    val taskId: I
    fun create(): AzureThrottlerTask<A, P, T>
}

interface AzureThrottler<A, I> : AzureThrottlerTaskCompletionResultNotifier, AzureThrottlerStrategyTaskContainer<I> {
    fun <P, T> registerTask(taskId: I, task : AzureThrottlerTask<A, P, T>, taskTimeExecutionType: AzureThrottlerTaskTimeExecutionType, defaultTimeoutInSeconds: Long) : AzureThrottler<A, I>
    fun <P, T> registerTask(taskDescriptor: AzureTaskDescriptor<A, I, P, T>, taskTimeExecutionType: AzureThrottlerTaskTimeExecutionType, defaultTimeoutInSeconds: Long) : AzureThrottler<A, I>

    fun <P, T> executeTask(taskId: I, parameters: P) : Single<T>
    fun <P, T> executeTask(taskDescriptor: AzureTaskDescriptor<A, I, P, T>, parameters: P) : Single<T>
}

interface AzureThrottlerStrategyTaskContainer<I> {
    fun getTaskList(): List<AzureThrottlerStrategyTask<I>>
}

interface AzureThrottlerStrategyTask<I> {
    val taskId: I
    val lastUpdatedDateTime : LocalDateTime
    val timeExecutionType : AzureThrottlerTaskTimeExecutionType
    fun getStatistics(startDateTime: LocalDateTime): AzureThrottlerTaskQueueCallHistoryStatistics
    fun setCacheTimeout(timeoutInSeconds: Long, source: AzureThrottlingSource)
    fun resetCache(source: AzureThrottlingSource)
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

interface AzureThrottlerAdapter<A> : AzureThrottlerAdapterRemainingReadsNotifier {
    val api: A
    fun setThrottlerTime(milliseconds: Long)
    fun getThrottlerTime(): Long
    fun getWindowWidthInMilliseconds(): Long
    fun getWindowStartDateTime(): LocalDateTime
    fun getRemainingReads(): Long
    fun getDefaultReads(): Long
    fun <T> execute(queryFactory: (A) -> Single<T>): Single<AzureThrottlerAdapterResult<T>>
}

interface AzureThrottlerAdapterRemainingReadsNotifier {
    fun notifyRemainingReads(value: Long?)
}

interface AzureThrottlerTaskCompletionResultNotifier {
    fun notifyRateLimitReached(retryAfterTimeoutInSeconds: Long)
    fun notifyCompleted()
}

class AzureRateLimitReachedException(val retryAfterTimeoutInSeconds: Long, msg: String? = null, cause: Throwable? = null): Exception(msg, cause)
