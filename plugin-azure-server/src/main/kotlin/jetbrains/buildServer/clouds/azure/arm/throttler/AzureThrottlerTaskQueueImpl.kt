package jetbrains.buildServer.clouds.azure.arm.throttler

import com.intellij.openapi.diagnostic.Logger
import rx.Single
import rx.internal.util.SubscriptionList
import rx.subjects.PublishSubject
import rx.subjects.Subject
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

class AzureThrottlerTaskQueueImpl<A, I, P, T>(
        override val taskId: I,
        private val task : AzureThrottlerTask<A, P, T>,
        private val adapter: AzureThrottlerAdapter<A>,
        taskExecutionTimeType: AzureThrottlerTaskTimeExecutionType,
        private val defaultCacheTimeoutInSeconds: Long,
        private val taskCompletionResultNotifier: AzureThrottlerTaskCompletionResultNotifier
) : AzureThrottlerTaskQueue<I, P, T> {
    private val myTaskQueue = ConcurrentLinkedQueue<QueueItem<T, P>>()
    private val myLastUpdatedDateTime = AtomicReference<LocalDateTime>(LocalDateTime.MIN)
    private val myCacheTimeoutInSeconds = AtomicLong(defaultCacheTimeoutInSeconds)
    private val myCallHistory = AzureThrottlerTaskQueueCallHistoryImpl()
    private var myTaskTimeExecutionType = AtomicReference(taskExecutionTimeType)

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
                LOG.info("Trying to get data from cache for task $taskId, mode $flow.")
                val cacheValue = task.getFromCache(flow, parameters)
                if (cacheValue != null) {
                    LOG.info("Returning from cache  for task $taskId, mode $flow.")
                    return Single.just(AzureThrottlerAdapterResult(cacheValue, null, true))
                }
            }
        }

        if (flow == AzureThrottlerFlow.Suspended) {
            LOG.info("There is no data in cache for task $taskId, mode $flow. Added request to queue")
        }

        val subject = PublishSubject.create<AzureThrottlerAdapterResult<T>>()
        myTaskQueue.add(QueueItem(
                LocalDateTime.now(Clock.systemUTC()),
                parameters,
                false,
                true,
                subject))
        return subject.toSingle()
    }

    override fun invalidateCache() {
        if (task is AzureThrottlerCacheableTask<A, P, T>) {
            task.invalidateCache()
        }
    }

    override fun setCacheTimeout(timeoutInSeconds: Long, source: AzureThrottlingSource) {
        val timeout = max(defaultCacheTimeoutInSeconds, timeoutInSeconds)
        if (myCacheTimeoutInSeconds.get() == timeout) {
            LOG.info("New timeout $timeoutInSeconds was rejected for $taskId task")
            return
        }

        LOG.info("New timeout $timeoutInSeconds was accepted for $taskId task")

        myCacheTimeoutInSeconds.set(timeout)
        if (task is AzureThrottlerCacheableTask<A, P, T>) {
            task.setCacheTimeout(myCacheTimeoutInSeconds.get())
        }
    }

    override fun getStatistics(startDateTime: LocalDateTime): AzureThrottlerTaskQueueCallHistoryStatistics {
        return myCallHistory.getStatistics(startDateTime)
    }

    private fun execute(item: QueueItem<T, P>) {
        val itemsToCombine = myTaskQueue
                .filter { it == item || it.canBeCombined && it.parameter == item.parameter }

        myTaskQueue.removeAll(itemsToCombine)

        val subscriptions = SubscriptionList()
        val resultSubject = PublishSubject.create<AzureThrottlerAdapterResult<T>>()
        itemsToCombine.map { resultSubject.subscribe(it.result) }.forEach { subscriptions.add(it) }

        subscriptions.add(adapter
                .execute { task.create(adapter.api, item.parameter) }
                .doOnSuccess {
                    myLastUpdatedDateTime.set(LocalDateTime.now(Clock.systemUTC()))
                    myCallHistory.addExecutionCall(it.requestsCount)
                    if (task is AzureThrottlerCacheableTask<A, P, T>) {
                        task.setToCache(item.parameter, it.value)
                    }
                    taskCompletionResultNotifier.notifyCompleted()
                }
                .doOnError {
                    error ->
                    if (error is AzureRateLimitReachedException)
                        taskCompletionResultNotifier.notifyRateLimitReached(error.retryAfterTimeoutInSeconds)
                }
                .doAfterTerminate { subscriptions.clear() }
                .subscribe(resultSubject))
    }

    private fun isTimeToUpdate() = myLastUpdatedDateTime.get().plusSeconds(myCacheTimeoutInSeconds.get()) <= LocalDateTime.now(Clock.systemUTC())

    data class QueueItem<T, P>(
            val timeToExecute: LocalDateTime,
            val parameter: P,
            val force: Boolean,
            val canBeCombined: Boolean,
            val result: Subject<AzureThrottlerAdapterResult<T>, AzureThrottlerAdapterResult<T>>)

    companion object {
        private val LOG = Logger.getInstance(AzureThrottlerTaskQueueImpl::class.java.name)
    }
}
