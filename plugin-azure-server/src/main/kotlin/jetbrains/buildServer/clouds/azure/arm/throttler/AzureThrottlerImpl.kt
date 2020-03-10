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
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class AzureThrottlerImpl<A, I>(
        private val adapter: AzureThrottlerAdapter<A>,
        private val throttlerStrategy: AzureThrottlerStrategy<I>,
        private val requestScheduler: Scheduler,
        private val timeoutScheduler: Scheduler,
        private val scheduledExecutorFactory: AzureThrottlerScheduledExecutorFactorty
) : AzureThrottler<A, I>, AzureThrottlerStrategyTaskContainer<I>, AzureThrottlerTaskCompletionResultNotifier {
    private val myTaskQueues = ConcurrentHashMap<I, AzureThrottlerTaskQueue<I, *, *>>()
    private val myNonBlockingTaskExecutionId = AtomicLong(0)
    private val mySubscriptions = SubscriptionList()
    private val myStartStopLock = ReentrantReadWriteLock()
    private var myScheduledExecutor: AzureThrottlerScheduledExecutor? = null

    init {
        throttlerStrategy.setContainer(this)
    }

    override fun start(): Boolean {
        if (myStartStopLock.read { return@read myScheduledExecutor != null }) return false

        return myStartStopLock.write {
            if (myScheduledExecutor != null) return false

            val executor = scheduledExecutorFactory.create {
                executeNextTask()
            }
            executor.start()

            myScheduledExecutor = executor

            return true
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
        myTaskQueues[taskId] = AzureThrottlerTaskQueueImpl(taskId, task, adapter, taskTimeExecutionType, defaultTimeoutInSeconds, this, requestScheduler)
        return this
    }
    override fun <P, T> registerTask(taskDescriptor: AzureTaskDescriptor<A, I, P, T>, taskTimeExecutionType: AzureThrottlerTaskTimeExecutionType, defaultTimeoutInSeconds: Long): AzureThrottler<A, I> {
        return registerTask(taskDescriptor.taskId, taskDescriptor.create(), taskTimeExecutionType, defaultTimeoutInSeconds)
    }

    override fun <P, T> executeTask(taskId: I, parameters: P): Single<T> {
        @Suppress("UNCHECKED_CAST")
        val taskQueue = myTaskQueues[taskId] as AzureThrottlerTaskQueue<I, P, T>

        return taskQueue.requestTask(throttlerStrategy.getFlow(), parameters).map { it.value }
    }

    override fun <P, T> executeTask(taskDescriptor: AzureTaskDescriptor<A, I, P, T>, parameters: P): Single<T> {
        return executeTask<P, T>(taskDescriptor.taskId, parameters)
    }

    override fun <P, T> executeTaskWithTimeout(taskDescriptor: AzureTaskDescriptor<A, I, P, T>, parameters: P): Single<T> {
        val executionId = myNonBlockingTaskExecutionId.incrementAndGet()
        LOG.debug("[${taskDescriptor.taskId}-$executionId] Starting non blocking task")

        val subscription = SubscriptionList()
        mySubscriptions.add(subscription)

        var source = executeTask<P, T>(taskDescriptor.taskId, parameters)
                .toObservable()
                .delaySubscription(10, TimeUnit.MILLISECONDS)
                .publish()

        var noExternalSubscriber = false;
        subscription.add(
                source
                        .doAfterTerminate { if (noExternalSubscriber) mySubscriptions.remove(subscription) }
                        .doOnNext { LOG.debug("[${taskDescriptor.taskId}-$executionId] Data fetched") }
                        .doOnCompleted { LOG.debug("[${taskDescriptor.taskId}-$executionId] Anchor completed") }
                        .subscribe({}, {})
        )

        return source
                .timeout(getTaskExecutionTimeout(), TimeUnit.SECONDS, timeoutScheduler)
                .onErrorResumeNext { error ->
                    if (error is TimeoutException) {
                        noExternalSubscriber = true;
                        LOG.debug("[${taskDescriptor.taskId}-$executionId] Task could not be executed for requested time")
                        return@onErrorResumeNext Observable.error<T>(ThrottlerTimeoutException("Task ${taskDescriptor.taskId} could not be executed for requested time", error))
                    }
                    return@onErrorResumeNext Observable.error<T>(error)
                }
                .doOnSubscribe {
                    LOG.debug("[${taskDescriptor.taskId}-$executionId] Subscribing")
                    subscription.add(source.connect())
                }
                .doOnUnsubscribe { LOG.debug("[${taskDescriptor.taskId}-$executionId] Unsubscribing") }
                .doOnCompleted { LOG.debug("[${taskDescriptor.taskId}-$executionId] Completed")}
                .doOnNext { LOG.debug("[${taskDescriptor.taskId}-$executionId] Data delivered")}
                .doAfterTerminate { if (!noExternalSubscriber) mySubscriptions.remove(subscription) }
                .toSingle()
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

        if (!taskWasExecuted) return

        throttlerStrategy.applyTaskChanges()
    }

    private fun getTaskExecutionTimeout(): Long {
        return TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_THROTTLER_TASK_TIMEOUT_SEC, 15L)
    }

    companion object {
        private val LOG = Logger.getInstance(AzureThrottlerImpl::class.java.name)
    }
}
