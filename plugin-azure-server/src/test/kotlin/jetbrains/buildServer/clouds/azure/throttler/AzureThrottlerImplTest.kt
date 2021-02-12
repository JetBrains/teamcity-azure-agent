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

package jetbrains.buildServer.clouds.azure.throttler

import io.mockk.*
import jetbrains.buildServer.clouds.azure.arm.throttler.*
import org.jmock.MockObjectTestCase
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import rx.Observer
import rx.schedulers.Schedulers
import rx.schedulers.TestScheduler
import rx.Single
import java.util.concurrent.TimeUnit

class AzureThrottlerImplTest : MockObjectTestCase() {
    private lateinit var adapter: AzureThrottlerAdapter<Unit>
    private lateinit var throttlerStrategy: AzureThrottlerStrategy<Unit>
    private lateinit var scheduler: TestScheduler
    private lateinit var timeoutScheduler: TestScheduler
    private lateinit var scheduledExecutorFactory: AzureThrottlerScheduledExecutorFactorty
    private lateinit var scheduledExecutor: AzureThrottlerScheduledExecutor
    private lateinit var executeTaskAction: () -> Unit
    private lateinit var taskNotifications: AzureTaskNotifications

    @BeforeMethod
    fun beforeMethod() {
        MockKAnnotations.init(this)

        adapter = mockk()
        every { adapter.api } returns Unit
        every {
            adapter.execute<String>(captureLambda())
        } answers {
            lambda<(Unit) -> Single<String>>().captured.invoke(Unit).map { AzureThrottlerAdapterResult(it, null, false) }
        }
        every { adapter.logDiagnosticInfo() } returns Unit

        throttlerStrategy = mockk()
        every { throttlerStrategy.setContainer(any()) } returns Unit
        every { throttlerStrategy.getFlow() } returns AzureThrottlerFlow.Normal
        every { throttlerStrategy.applyTaskChanges() } returns Unit

        scheduledExecutor = mockk()
        every { scheduledExecutor.start() } returns true
        every { scheduledExecutor.stop() } returns Unit

        scheduledExecutorFactory = mockk()
        every {
            scheduledExecutorFactory.create(captureLambda())
        } answers {
            executeTaskAction = lambda<() -> Unit>().captured
            scheduledExecutor
        }

        scheduler = Schedulers.test()
        timeoutScheduler = Schedulers.test()

        taskNotifications = mockk()
    }

    @Test
    fun shouldExecuteTask() {
        // Given
        val instance = createInstance()

        every { throttlerStrategy.notifyCompleted(any()) } returns Unit

        val task : AzureThrottlerTask<Unit, String, String> = mockk()
        val parameterSlot = CapturingSlot<String>()
        every { task.create(Unit, capture(parameterSlot)) } answers { Single.just("Executed with ${parameterSlot.captured}") }

        instance.registerTask(Unit, task, AzureThrottlerTaskTimeExecutionType.Random, 10)
        instance.start()

        val observer : Observer<String> = mockk()
        every { observer.onCompleted() } returns Unit
        every { observer.onNext(any()) } returns Unit

        // When
        val subscription = instance
                .executeTask<String, String>(Unit, "value")
                .subscribe(observer)

        processOneTask()

        // Then
        verify { observer.onNext("Executed with value") }

        subscription.unsubscribe()
        instance.stop()
    }

    @Test
    fun shouldProcessTaskError() {
        // Given
        val instance = createInstance()

        val task : AzureThrottlerTask<Unit, String, String> = mockk()
        val error = Exception("Test error")
        every { task.create(Unit, any()) } answers { Single.error(error) }

        instance.registerTask(Unit, task, AzureThrottlerTaskTimeExecutionType.Random, 10)
        instance.start()

        val observer : Observer<String> = mockk()
        every { observer.onCompleted() } returns Unit
        every { observer.onError(any()) } returns Unit

        // When
        val subscription = instance
                .executeTask<String, String>(Unit, "value")
                .subscribe(observer)

        processOneTask()

        // Then
        verify { observer.onError(error) }

        subscription.unsubscribe()
        instance.stop()
    }

    @Test
    fun shouldExecuteTaskWithTimeout() {
        // Given
        val instance = createInstance()

        every { throttlerStrategy.notifyCompleted(any()) } returns Unit

        val task : AzureThrottlerTask<Unit, String, String> = mockk()
        val parameterSlot = CapturingSlot<String>()
        every { task.create(Unit, capture(parameterSlot)) } answers { Single.just("Executed with ${parameterSlot.captured}") }

        val taskDescriptor: AzureTaskDescriptor<Unit, Unit, String, String> = mockk()
        every { taskDescriptor.create(taskNotifications) } returns task
        every { taskDescriptor.taskId } returns Unit

        instance.registerTask(Unit, task, AzureThrottlerTaskTimeExecutionType.Random, 10)
        instance.start()

        val observer : Observer<String> = mockk()
        every { observer.onCompleted() } returns Unit
        every { observer.onNext(any()) } returns Unit

        // When

        val subscription = instance
                .executeTaskWithTimeout<String, String>(taskDescriptor, "value")
                .subscribe(observer)

        executeTaskAction()
        Thread.sleep(20) // Waiting for delayed subscription
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        // Then
        verify { observer.onNext("Executed with value") }

        subscription.unsubscribe()
        instance.stop()
    }

    @Test
    fun shouldProcessErrorWhenExecuteTaskWithTimeout() {
        // Given
        val instance = createInstance()

        val task : AzureThrottlerTask<Unit, String, String> = mockk()
        val error = Exception("Test error")
        every { task.create(Unit, any()) } answers { Single.error(error) }

        val taskDescriptor: AzureTaskDescriptor<Unit, Unit, String, String> = mockk()
        every { taskDescriptor.create(taskNotifications) } returns task
        every { taskDescriptor.taskId } returns Unit

        instance.registerTask(Unit, task, AzureThrottlerTaskTimeExecutionType.Random, 10)
        instance.start()

        val observer : Observer<String> = mockk()
        every { observer.onCompleted() } returns Unit
        every { observer.onError(any()) } returns Unit

        // When

        val subscription = instance
                .executeTaskWithTimeout<String, String>(taskDescriptor, "value")
                .subscribe(observer)

        executeTaskAction()
        Thread.sleep(20) // Waiting for delayed subscription
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        // Then
        verify { observer.onError(error) }

        subscription.unsubscribe()
        instance.stop()
    }

    @Test
    fun shouldProcessTimeoutErrorWhenExecuteTaskWithTimeout() {
        // Given
        val instance = createInstance()

        val task : AzureThrottlerTask<Unit, String, String> = mockk()
        val parameterSlot = CapturingSlot<String>()
        every { task.create(Unit, capture(parameterSlot)) } answers { Single.just("Executed with ${parameterSlot.captured}") }

        val taskDescriptor: AzureTaskDescriptor<Unit, Unit, String, String> = mockk()
        every { taskDescriptor.create(taskNotifications) } returns task
        every { taskDescriptor.taskId } returns Unit

        instance.registerTask(Unit, task, AzureThrottlerTaskTimeExecutionType.Random, 10)
        instance.start()

        val observer : Observer<String> = mockk()
        every { observer.onCompleted() } returns Unit
        every { observer.onError(any()) } returns Unit

        // When

        val subscription = instance
                .executeTaskWithTimeout<String, String>(taskDescriptor, "value")
                .subscribe(observer)

        executeTaskAction()
        Thread.sleep(20) // Waiting for delayed subscription
        timeoutScheduler.advanceTimeBy(20, TimeUnit.SECONDS)

        // Then
        verify { observer.onError(match { e -> e is ThrottlerTimeoutException }) }

        subscription.unsubscribe()
        instance.stop()
    }

    @Test
    fun shouldKeepSubscriptionWhenTimeoutError() {
        // Given
        val instance = createInstance()

        val task : AzureThrottlerTask<Unit, String, String> = mockk()
        val parameterSlot = CapturingSlot<String>()
        val delayScheduler = Schedulers.test()
        var state = "initial"
        every {
            task.create(Unit, capture(parameterSlot))
        } answers {
            Single
                    .just("Executed with ${parameterSlot.captured}")
                    .delay(1, TimeUnit.SECONDS, delayScheduler)
                    .doOnSubscribe { state = "subscribed" }
                    .doOnUnsubscribe { state = "unsubscribed" }
        }

        val taskDescriptor: AzureTaskDescriptor<Unit, Unit, String, String> = mockk()
        every { taskDescriptor.create(taskNotifications) } returns task
        every { taskDescriptor.taskId } returns Unit

        instance.registerTask(Unit, task, AzureThrottlerTaskTimeExecutionType.Random, 10)
        instance.start()

        val observer : Observer<String> = mockk()
        every { observer.onCompleted() } returns Unit
        every { observer.onError(any()) } returns Unit

        // When
        val subscription = instance
                .executeTaskWithTimeout<String, String>(taskDescriptor, "value")
                .subscribe(observer)

        executeTaskAction()
        Thread.sleep(20) // Waiting for delayed subscription
        timeoutScheduler.advanceTimeBy(20, TimeUnit.SECONDS)

        subscription.unsubscribe()

        Assert.assertEquals(state, "initial")

        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
        Assert.assertEquals(state, "subscribed")

        delayScheduler.advanceTimeBy(10, TimeUnit.SECONDS)
        Assert.assertEquals(state, "unsubscribed")

        // Then
        verify { observer.onError(match { e -> e is ThrottlerTimeoutException }) }

        instance.stop()
    }

    private fun processOneTask() {
        executeTaskAction()
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS)
    }

    private fun createInstance() : AzureThrottlerImpl<Unit, Unit> {
        return AzureThrottlerImpl(
                adapter,
                throttlerStrategy,
                scheduler,
                timeoutScheduler,
                scheduledExecutorFactory,
                taskNotifications
        )
    }
}
