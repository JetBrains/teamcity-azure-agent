

package jetbrains.buildServer.clouds.azure.arm.throttler

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.serverSide.TeamCityProperties
import rx.Single
import rx.internal.util.SubscriptionList
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class AzureThrottlerImpl<A, I>(
        private val adapter: AzureThrottlerAdapter<A>,
        private val throttlerStrategy: AzureThrottlerStrategy<I>,
        private val schedulers: AzureThrottlerSchedulers,
        private val scheduledExecutorFactory: AzureThrottlerScheduledExecutorFactorty,
        private val taskNotifications: AzureTaskNotifications
) : AzureThrottler<A, I>, AzureThrottlerStrategyTaskContainer<I>, AzureThrottlerTaskCompletionResultNotifier {
    private val myTaskQueues = ConcurrentHashMap<I, AzureThrottlerTaskQueue<I, *, *>>()
    private val myNonBlockingTaskExecutionId = AtomicLong(0)
    private val mySubscriptions = SubscriptionList()
    private val myStartStopLock = ReentrantReadWriteLock()
    private var myScheduledExecutor: AzureThrottlerScheduledExecutor? = null
    private val myLastLogDiagnosticTime = AtomicReference(LocalDateTime.MIN)

    init {
        throttlerStrategy.setContainer(this)
    }

    override fun start(): Boolean {
        if (myStartStopLock.read { myScheduledExecutor != null }) return false

        return myStartStopLock.write {
            if (myScheduledExecutor != null) return@write false

            val executor = scheduledExecutorFactory.create {
                executeNextTask()
            }
            executor.start()

            myScheduledExecutor = executor

            return@write true
        }
    }

    override fun stop() {
        myStartStopLock.write {
            if (myScheduledExecutor != null) {
                val executor = myScheduledExecutor!!
                myScheduledExecutor = null

                executor.stop()
            }
            mySubscriptions.clear()
        }
    }

    override fun <P, T> registerTask(taskId: I, task: AzureThrottlerTask<A, P, T>, taskTimeExecutionType: AzureThrottlerTaskTimeExecutionType, defaultTimeoutInSeconds: Long): AzureThrottler<A, I> {
        if (myTaskQueues.contains(taskId)) throw Exception("Task with Id $taskId has already been registered")
        myTaskQueues[taskId] = AzureThrottlerTaskQueueImpl(
            taskId,
            AzureThrottlerRequestQueueImpl(task),
            task,
            adapter,
            taskTimeExecutionType,
            defaultTimeoutInSeconds,
            this,
            schedulers.requestScheduler
        )
        return this
    }

    override fun <P, T> registerTask(taskDescriptor: AzureTaskDescriptor<A, I, P, T>, taskTimeExecutionType: AzureThrottlerTaskTimeExecutionType, defaultTimeoutInSeconds: Long): AzureThrottler<A, I> {
        return registerTask(taskDescriptor.taskId, taskDescriptor.create(taskNotifications), taskTimeExecutionType, defaultTimeoutInSeconds)
    }

    override fun <P, T> executeTask(taskId: I, parameters: P): Single<T> {
        @Suppress("UNCHECKED_CAST")
        val taskQueue = myTaskQueues[taskId] as AzureThrottlerTaskQueue<I, P, T>

        return taskQueue.requestTask(throttlerStrategy.getFlow(), parameters).map { it.value }
    }

    override fun <P, T> executeTask(taskDescriptor: AzureTaskDescriptor<A, I, P, T>, parameters: P): Single<T> {
        return executeTask(taskDescriptor.taskId, parameters)
    }

    override fun <P, T> executeTaskWithTimeout(taskDescriptor: AzureTaskDescriptor<A, I, P, T>, parameters: P, timeout: Long, timeUnit: TimeUnit): Single<T> {
        val executionId = myNonBlockingTaskExecutionId.incrementAndGet()
        return executeTask<P, T>(taskDescriptor.taskId, parameters)
                .timeout(timeout, timeUnit, schedulers.timeoutScheduler)
                .onErrorResumeNext { error ->
                    LOG.debug("[${taskDescriptor.taskId}-$executionId] Error occurred: $error")
                    if (error is TimeoutException) {
                        LOG.debug("[${taskDescriptor.taskId}-$executionId] Task could not be executed for requested time")
                        return@onErrorResumeNext Single.error<T>(ThrottlerTimeoutException("Task ${taskDescriptor.taskId} could not be executed for requested time", error))
                    }
                    return@onErrorResumeNext Single.error<T>(error)
                }
    }

    override fun <P, T> executeTaskWithTimeout(taskDescriptor: AzureTaskDescriptor<A, I, P, T>, parameters: P): Single<T> {
        return executeTaskWithTimeout(taskDescriptor, parameters, getTaskExecutionTimeout(), TimeUnit.SECONDS)
    }

    override fun notifyCompleted(performedRequests: Boolean) {
        throttlerStrategy.notifyCompleted(performedRequests)
    }

    override fun notifyRateLimitReached(retryAfterTimeoutInSeconds: Long) {
        throttlerStrategy.notifyRateLimitReached(retryAfterTimeoutInSeconds)
    }

    override fun getTaskList(): List<AzureThrottlerStrategyTask<I>> {
        return myTaskQueues.asSequence().map{ it.value }.toList()
    }

    override fun isSuspended(): Boolean {
        return throttlerStrategy.getFlow() == AzureThrottlerFlow.Suspended
    }

    private fun executeNextTask() {
        var taskWasExecuted = false
        for (taskQueue in myTaskQueues.elements().asSequence().sortedBy { it.lastUpdatedDateTime }) {
            taskWasExecuted = taskQueue.executeNext()
            if (taskWasExecuted)
                break
        }

        if (taskWasExecuted) {
            throttlerStrategy.applyTaskChanges()
        }

        logDiagnosticInfo()
    }

    private fun logDiagnosticInfo() {
        if (!LOG.isDebugEnabled){
            return
        }
        val nextCheckTime = myLastLogDiagnosticTime.get().plusSeconds(getPrintDiagnosticInterval())
        val now = LocalDateTime.now(Clock.systemUTC())

        if (nextCheckTime >= now) return
        myLastLogDiagnosticTime.set(now)

        val result = StringBuilder()
        result.append("Tasks statistics: Now: ${now}; Flow: ${throttlerStrategy.getFlow()}; ")
        for(taskEntry in myTaskQueues) {
            val statistics = taskEntry.value.getStatistics(now.minusHours(1))
            result.append("Task: ${taskEntry.key}, " +
                    "Last updated: ${taskEntry.value.lastUpdatedDateTime}, " +
                    "Cache timeout: ${taskEntry.value.getCacheTimeout()}, " +
                    "ExecutionCallCount: ${statistics.executionCallCount}, " +
                    "RequestCallCount: ${statistics.requestCallCount}, " +
                    "ResourceRequestsCount: ${statistics.resourceRequestsCount}" +
                    ";")
        }
        LOG.debug(result.toString())

        adapter.logDiagnosticInfo()
    }

    private fun getTaskExecutionTimeout(): Long {
        return TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_THROTTLER_TASK_TIMEOUT_SEC, 15L)
    }

    private fun getPrintDiagnosticInterval(): Long {
        return TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_THROTTLER_PRINT_DIAGNOSTIC_INTERVAL_SEC, 30L)
    }

    companion object {
        private val LOG = Logger.getInstance(AzureThrottlerImpl::class.java.name)
    }
}
