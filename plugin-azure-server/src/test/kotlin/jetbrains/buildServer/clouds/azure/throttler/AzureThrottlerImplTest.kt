

package jetbrains.buildServer.clouds.azure.throttler

import io.mockk.CapturingSlot
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskDescriptor
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskNotifications
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerAdapter
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerAdapterResult
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerFlow
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerImpl
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerScheduledExecutor
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerScheduledExecutorFactorty
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerSchedulers
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerStrategy
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTask
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTaskTimeExecutionType
import jetbrains.buildServer.clouds.azure.arm.throttler.ThrottlerTimeoutException
import org.jmock.MockObjectTestCase
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import rx.Observer
import rx.Single
import rx.schedulers.Schedulers
import rx.schedulers.TestScheduler
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
        every { adapter.name } returns "Adapter"
        every {
            adapter.execute<String>(captureLambda())
        } answers {
            lambda<(Unit, AzureTaskContext) -> Single<String>>().captured.invoke(Unit, mockk()).map { AzureThrottlerAdapterResult(it, null, false) }
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
        every { task.create(Unit, any(), capture(parameterSlot)) } answers { Single.just("Executed with ${parameterSlot.captured}") }

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
        every { task.create(Unit, any(), any()) } answers { Single.error(error) }

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
        every { task.create(Unit, any(), capture(parameterSlot)) } answers { Single.just("Executed with ${parameterSlot.captured}") }

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
        every { task.create(Unit, any(), any()) } answers { Single.error(error) }

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
        every { task.create(Unit, any(), capture(parameterSlot)) } answers { Single.just("Executed with ${parameterSlot.captured}") }

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
            task.create(Unit, any(), capture(parameterSlot))
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
                AzureThrottlerSchedulers(scheduler, timeoutScheduler),
                scheduledExecutorFactory,
                taskNotifications
        )
    }
}
