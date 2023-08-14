package jetbrains.buildServer.clouds.azure.throttler

import io.mockk.CapturingSlot
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerAdapter
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerAdapterResult
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTask
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerFlow
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerRequestBatch
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerRequestQueue
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTask
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTaskCompletionResultNotifier
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTaskQueueImpl
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTaskTimeExecutionType
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_THROTTLER_QUEUE_MAX_RETRY_COUNT
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_THROTTLER_QUEUE_MAX_TASK_LIVE_IN_SEC
import jetbrains.buildServer.clouds.azure.arm.throttler.ThrottlerMaxRetryCountException
import jetbrains.buildServer.clouds.azure.arm.throttler.ThrottlerMaxTaskLiveException
import jetbrains.buildServer.clouds.azure.arm.throttler.ThrottlerRateLimitReachedException
import org.jmock.MockObjectTestCase
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import rx.Observable
import rx.Observer
import rx.Scheduler
import rx.Single
import rx.schedulers.Schedulers
import rx.schedulers.TestScheduler
import rx.subjects.Subject
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class AzureThrottlerTaskQueueImplTest : MockObjectTestCase() {

    private lateinit var task: AzureThrottlerTask<Unit, String, String>
    private lateinit var cacheableTask: AzureThrottlerCacheableTask<Unit, String, String>
    private lateinit var adapter: AzureThrottlerAdapter<Unit>
    private var defaultCacheTimeoutInSeconds: Long = 123
    private lateinit var taskCompletionResultNotifier: AzureThrottlerTaskCompletionResultNotifier
    private lateinit var testScheduler : TestScheduler
    private lateinit var requestScheduler: Scheduler
    private lateinit var requestQueue: AzureThrottlerRequestQueue<Unit, String, String>
    private lateinit var emptyBatch: AzureThrottlerRequestBatch<String, String>

    @BeforeMethod
    fun beforeMethod() {
        MockKAnnotations.init(this)

        task = mockk()

        cacheableTask = mockk()

        emptyBatch = mockk()
        every { emptyBatch.count() } returns 0

        requestQueue = mockk()

        adapter = mockk()
        every { adapter.api } returns Unit
        every { adapter.name } returns "Adapter"
        every {
            adapter.execute<String>(captureLambda())
        } answers {
            lambda<(Unit, AzureTaskContext) -> Single<String>>().captured.invoke(Unit, mockk()).map { AzureThrottlerAdapterResult(it, null, false) }
        }

        taskCompletionResultNotifier = mockk()

        testScheduler = Schedulers.test()
        requestScheduler = testScheduler
    }

    @Test
    fun shouldExecuteNextReturnFalseWhenQueueIsEmpty() {
        // Given
        val instance = createInstance()
        every { requestQueue.extractNextBatch() } returns emptyBatch

        // When
        val result = instance.executeNext()

        // Then
        Assert.assertEquals(false, result)
    }

    @Test
    fun shouldInitCacheTimeoutForCacheableTask() {
        // Given
        task = cacheableTask
        every { cacheableTask.setCacheTimeout(any()) } returns Unit

        // When
        @Suppress("UNUSED_VARIABLE")
        val instance = createInstance()

        // Then
        verify { cacheableTask.setCacheTimeout(defaultCacheTimeoutInSeconds) }
    }

    @Test
    fun shouldRequestTaskForNormalFlow() {
        // Given
        val instance = createInstance()
        every { requestQueue.addRequest(any(), any(), any(), any(), any(), any(), any()) } returns Unit

        // When
        instance.requestTask(AzureThrottlerFlow.Normal, "Test parameter")

        // Then
        verify {
            requestQueue.addRequest(
                    match { t -> t <= LocalDateTime.now(Clock.systemUTC()) },
                    "Test parameter",
                    any(),
                    false,
                    true,
                    0,
                    match { t -> t <= LocalDateTime.now(Clock.systemUTC()) })
        }
    }

    @Test
    fun shouldRequestTaskForSuspendFlow() {
        // Given
        val instance = createInstance()
        every { requestQueue.addRequest(any(), any(), any(), any(), any(), any(), any()) } returns Unit

        // When
        instance.requestTask(AzureThrottlerFlow.Suspended, "Test parameter")

        // Then
        verify {
            requestQueue.addRequest(
                    match { t -> t <= LocalDateTime.now(Clock.systemUTC()) },
                    "Test parameter",
                    any(),
                    false,
                    true,
                    0,
                    match { t -> t <= LocalDateTime.now(Clock.systemUTC()) })
        }
    }

    @Test
    fun shouldRequestTaskForSuspendFlowWithEnabledRetrying() {
        // Given
        val instance = createInstance()
        instance.enableRetryOnThrottle()
        every { requestQueue.addRequest(any(), any(), any(), any(), any(), any(), any()) } returns Unit
        val localDate = LocalDateTime.now(Clock.systemUTC())

        // When
        instance.requestTask(AzureThrottlerFlow.Suspended, "Test parameter")

        // Then
        verify {
            requestQueue.addRequest(
                    match { t -> t >= localDate },
                    "Test parameter",
                    any(),
                    false,
                    true,
                    0,
                    match { t -> t <= LocalDateTime.now(Clock.systemUTC()) })
        }
    }

    @Test
    fun shouldRequestCacheableTaskForNormalFlow() {
        // Given
        task = cacheableTask
        every { cacheableTask.setCacheTimeout(any()) } returns Unit
        every { cacheableTask.getFromCache(any()) } returns null

        val instance = createInstance()
        every { requestQueue.addRequest(any(), any(), any(), any(), any(), any(), any()) } returns Unit

        // When
        instance.requestTask(AzureThrottlerFlow.Normal, "Test parameter")

        // Then
        verify {
            requestQueue.addRequest(
                    match { t -> t <= LocalDateTime.now(Clock.systemUTC()) },
                    "Test parameter",
                    any(),
                    false,
                    true,
                    0,
                    match { t -> t <= LocalDateTime.now(Clock.systemUTC()) })
        }
    }

    @Test
    fun shouldRequestCacheableTaskForSuspendFlow() {
        // Given
        task = cacheableTask
        every { cacheableTask.setCacheTimeout(any()) } returns Unit
        every { cacheableTask.getFromCache(any()) } returns null

        val instance = createInstance()
        every { requestQueue.addRequest(any(), any(), any(), any(), any(), any(), any()) } returns Unit

        // When
        instance.requestTask(AzureThrottlerFlow.Suspended, "Test parameter")

        // Then
        verify {
            requestQueue.addRequest(
                    match { t -> t <= LocalDateTime.now(Clock.systemUTC()) },
                    "Test parameter",
                    any(),
                    false,
                    true,
                    0,
                    match { t -> t <= LocalDateTime.now(Clock.systemUTC()) })
        }
    }

    @Test
    fun shouldReturnFromCacheAndForceRequestTaskForNormalFlow() {
        // Given
        task = cacheableTask
        every { cacheableTask.setCacheTimeout(any()) } returns Unit
        every { cacheableTask.getFromCache(any()) } returns "Cached value"

        val instance = createInstance()
        every { requestQueue.addRequest(any(), any(), any(), any(), any(), any(), any()) } returns Unit

        val observer = mockk<Observer<AzureThrottlerAdapterResult<String>>>()
        every { observer.onNext(any()) } returns Unit
        every { observer.onCompleted() } returns Unit

        // When
        val subscription = instance
                .requestTask(AzureThrottlerFlow.Normal, "Test parameter")
                .subscribe(observer)

        // Then
        verify {
            observer.onNext(match {
                r -> r.value == "Cached value" && r.fromCache == true && r.requestsCount == null
            } )
        }
        verify {
            requestQueue.addRequest(
                    match { t -> t <= LocalDateTime.now(Clock.systemUTC()) },
                    "Test parameter",
                    any(),
                    true,
                    true,
                    0,
                    match { t -> t <= LocalDateTime.now(Clock.systemUTC()) })
        }
        subscription.unsubscribe()
    }

    @Test
    fun shouldReturnFromCacheTaskForNormalFlow() {
        // Given
        task = cacheableTask
        every { cacheableTask.setCacheTimeout(any()) } returns Unit
        every { cacheableTask.getFromCache(any()) } returns null
        every { cacheableTask.create(Unit, any(),"Test parameter") } returns Single.just("Test response")

        val instance = createInstance()
        every { requestQueue.extractNextBatch() } returns mockk {
            every { parameter } returns "Test parameter"
            every { canBeCombined() } returns true
            every { getMaxAttempNo() } returns 0
            every { getMinCreatedDate() } returns LocalDateTime.of(2020, 2, 11, 0,0,0)
            every { count() } returns 1
            every { hasForceRequest() } returns false
            every { subscribeTo(any(), any()) } returns Unit
        }

        every { taskCompletionResultNotifier.notifyCompleted(any()) } returns Unit

        instance.executeNext()

        testScheduler.triggerActions()

        every { cacheableTask.getFromCache(any()) } returns "Cached value"

        val observer = mockk<Observer<AzureThrottlerAdapterResult<String>>>()
        every { observer.onNext(any()) } returns Unit
        every { observer.onCompleted() } returns Unit

        // When
        val subscription = instance
                .requestTask(AzureThrottlerFlow.Normal, "Test parameter2")
                .subscribe(observer)

        // Then
        verify {
            observer.onNext(match {
                r -> r.value == "Cached value" && r.fromCache == true && r.requestsCount == null
            } )
        }
        subscription.unsubscribe()
    }

    @Test
    fun shouldReturnFromCacheWhenExecuting() {
        // Given
        task = cacheableTask
        every { cacheableTask.setCacheTimeout(any()) } returns Unit
        every { cacheableTask.getFromCache(any()) } returns "Cached value"
        every { cacheableTask.needCacheUpdate("Test parameter") } returns false

        val instance = createInstance()

        val observer = mockk<Observer<AzureThrottlerAdapterResult<String>>>()
        every { observer.onNext(any()) } returns Unit
        every { observer.onCompleted() } returns Unit

        val observableSlot = CapturingSlot<Observable<AzureThrottlerAdapterResult<String>>>()
        every { requestQueue.extractNextBatch() } returns mockk {
            every { parameter } returns "Test parameter"
            every { canBeCombined() } returns true
            every { getMaxAttempNo() } returns 0
            every { getMinCreatedDate() } returns LocalDateTime.of(2020, 2, 11, 0,0,0)
            every { count() } returns 1
            every { hasForceRequest() } returns false
            every {subscribeTo(capture(observableSlot), any()) } answers { observableSlot.captured.subscribe(observer) }
        }

        // When
        instance.executeNext()
        testScheduler.triggerActions()

        // Then
        verify(timeout = 100) {
            observer.onNext(match {
                r -> r.value == "Cached value" && r.fromCache == true && r.requestsCount == null
            } )
        }
    }

    @Test
    fun shouldNotReturnFromCacheWhenExecuting() {
        // Given
        task = cacheableTask
        every { cacheableTask.setCacheTimeout(any()) } returns Unit
        every { cacheableTask.getFromCache(any()) } returns "Cached value"
        every { cacheableTask.needCacheUpdate("Test parameter") } returns true
        every { cacheableTask.create(Unit, any(),"Test parameter") } returns Single.just("Test response")

        val instance = createInstance()

        every { requestQueue.extractNextBatch() } returns mockk {
            every { parameter } returns "Test parameter"
            every { canBeCombined() } returns true
            every { getMaxAttempNo() } returns 0
            every { getMinCreatedDate() } returns LocalDateTime.of(2020, 2, 11, 0,0,0)
            every { count() } returns 1
            every { hasForceRequest() } returns false
            every {subscribeTo(any(), any()) } returns Unit
        }

        // When
        instance.executeNext()

        // Then
        verify {
            task.create(Unit, any(), "Test parameter")
        }
    }

    @Test
    fun shouldPostProcessQueueWhenExecuting() {
        // Given
        task = cacheableTask
        every { cacheableTask.setCacheTimeout(any()) } returns Unit
        every { cacheableTask.getFromCache(any()) } returns null
        every { cacheableTask.needCacheUpdate("Test parameter") } returns false
        every { cacheableTask.create(Unit, any(),"Test parameter") } returns Single.just("Test response")

        val instance = createInstance()

        val tmpObservableSlot = CapturingSlot<Observable<AzureThrottlerAdapterResult<String>>>()
        every { requestQueue.extractNextBatch() } returns mockk {
            every { parameter } returns "Test parameter"
            every { canBeCombined() } returns true
            every { getMaxAttempNo() } returns 0
            every { getMinCreatedDate() } returns LocalDateTime.of(2020, 2, 11, 0,0,0)
            every { count() } returns 1
            every { hasForceRequest() } returns false
            every {subscribeTo(capture(tmpObservableSlot), any()) } answers { tmpObservableSlot.captured.subscribe({}) }
        }

        every { taskCompletionResultNotifier.notifyCompleted(false) } returns Unit

        val observer = mockk<Observer<AzureThrottlerAdapterResult<String>>>()
        every { observer.onNext(any()) } returns Unit
        every { observer.onCompleted() } returns Unit

        val observableSlot = CapturingSlot<Observable<AzureThrottlerAdapterResult<String>>>()
        every { requestQueue.extractBatchFor("Test parameter") } returns mockk {
            every { parameter } returns "Test parameter"
            every { canBeCombined() } returns true
            every { getMaxAttempNo() } returns 0
            every { getMinCreatedDate() } returns LocalDateTime.of(2020, 2, 11, 0,0,0)
            every { count() } returns 10
            every { hasForceRequest() } returns false
            every {subscribeTo(capture(observableSlot), any()) } answers { observableSlot.captured.subscribe(observer) }
        }

        // When
        instance.executeNext()
        testScheduler.triggerActions()

        // Then
        verify(timeout = 100) {
            observer.onNext(match {
                r -> r.value == "Test response" && r.fromCache == false && r.requestsCount == null
            } )
        }
    }

    @Test(invocationCount = 10)
    fun shouldPostProcessQueueWhenExecutingWithDelayedSubscription() {
        // Given
        task = cacheableTask
        every { cacheableTask.setCacheTimeout(any()) } returns Unit
        every { cacheableTask.getFromCache(any()) } returns null
        every { cacheableTask.needCacheUpdate("Test parameter") } returns false
        every { cacheableTask.create(Unit, any(),"Test parameter") } returns Observable.just("Test response").delaySubscription(10, TimeUnit.SECONDS, Schedulers.immediate()).toSingle()

        requestScheduler = Schedulers.immediate()
        val instance = createInstance()

        val tmpObservableSlot = CapturingSlot<Observable<AzureThrottlerAdapterResult<String>>>()
        every { requestQueue.extractNextBatch() } returns mockk {
            every { parameter } returns "Test parameter"
            every { canBeCombined() } returns true
            every { getMaxAttempNo() } returns 0
            every { getMinCreatedDate() } returns LocalDateTime.of(2020, 2, 11, 0,0,0)
            every { count() } returns 1
            every { hasForceRequest() } returns false
            every {subscribeTo(capture(tmpObservableSlot), any()) } answers { tmpObservableSlot.captured.subscribe({}) }
        }

        every { taskCompletionResultNotifier.notifyCompleted(false) } returns Unit

        val observer = mockk<Observer<AzureThrottlerAdapterResult<String>>>()
        every { observer.onNext(any()) } returns Unit
        every { observer.onCompleted() } returns Unit

        val observableSlot = CapturingSlot<Observable<AzureThrottlerAdapterResult<String>>>()
        every { requestQueue.extractBatchFor("Test parameter") } returns mockk {
            every { parameter } returns "Test parameter"
            every { canBeCombined() } returns true
            every { getMaxAttempNo() } returns 0
            every { getMinCreatedDate() } returns LocalDateTime.of(2020, 2, 11, 0,0,0)
            every { count() } returns 10
            every { hasForceRequest() } returns false
            every {subscribeTo(capture(observableSlot), any()) } answers { observableSlot.captured.subscribe(observer) }
        }

        // When
        instance.executeNext()

        // Then
        verify(timeout = 100) {
            observer.onNext(match {
                r -> r.value == "Test response" && r.fromCache == false && r.requestsCount == null
            } )
        }
    }

    @Test
    fun shouldDeliverErrorWhenRetrying() {
        // Given
        val error = Exception("Test error")

        task = cacheableTask
        every { cacheableTask.setCacheTimeout(any()) } returns Unit
        every { cacheableTask.getFromCache(any()) } returns "Cached value"
        every { cacheableTask.needCacheUpdate("Test parameter") } returns true
        every { cacheableTask.create(Unit, any(),"Test parameter") } returns Single.error(error)

        val instance = createInstance()

        val observer = mockk<Observer<AzureThrottlerAdapterResult<String>>>()
        every { observer.onError(any()) } returns Unit
        every { observer.onCompleted() } returns Unit

        val observableSlot = CapturingSlot<Observable<AzureThrottlerAdapterResult<String>>>()
        every { requestQueue.extractNextBatch() } returns mockk {
            every { parameter } returns "Test parameter"
            every { canBeCombined() } returns true
            every { getMaxAttempNo() } returns 0
            every { getMinCreatedDate() } returns LocalDateTime.of(2020, 2, 11, 0,0,0)
            every { count() } returns 10
            every { hasForceRequest() } returns false
            every {subscribeTo(capture(observableSlot), any()) } answers { observableSlot.captured.subscribe(observer) }
        }

        // When
        instance.executeNext()
        testScheduler.triggerActions()

        // Then
        verify {
            observer.onError(error)
        }
    }

    @Test
    fun shouldRespectMaxTaskLiveTimeWhenERetrying() {
        // Given
        val error = ThrottlerRateLimitReachedException(10, 1, "Test error")

        task = cacheableTask
        every { cacheableTask.setCacheTimeout(any()) } returns Unit
        every { cacheableTask.getFromCache(any()) } returns "Cached value"
        every { cacheableTask.needCacheUpdate("Test parameter") } returns true
        every { cacheableTask.create(Unit, any(),"Test parameter") } returns Single.error(error)

        every { taskCompletionResultNotifier.notifyRateLimitReached(10) } returns Unit

        val instance = createInstance()
        instance.enableRetryOnThrottle()
        System.setProperty(TEAMCITY_CLOUDS_AZURE_THROTTLER_QUEUE_MAX_TASK_LIVE_IN_SEC,  "1")

        val observer = mockk<Observer<AzureThrottlerAdapterResult<String>>>()
        every { observer.onError(any()) } returns Unit
        every { observer.onCompleted() } returns Unit

        val observableSlot = CapturingSlot<Observable<AzureThrottlerAdapterResult<String>>>()
        every { requestQueue.extractNextBatch() } returns mockk {
            every { parameter } returns "Test parameter"
            every { canBeCombined() } returns true
            every { getMaxAttempNo() } returns 0
            every { getMinCreatedDate() } returns LocalDateTime.of(2020, 2, 11, 0,0,0)
            every { count() } returns 10
            every { hasForceRequest() } returns false
            every {subscribeTo(capture(observableSlot), any()) } answers { observableSlot.captured.subscribe(observer) }
        }

        // When
        instance.executeNext()
        testScheduler.triggerActions()

        // Then
        verify {
            observer.onError(match { it is ThrottlerMaxTaskLiveException })
        }
    }

    @Test
    fun shouldRespectMaxRetryCountWhenRetrying() {
        // Given
        val error = ThrottlerRateLimitReachedException(10, 1, "Test error")

        task = cacheableTask
        every { cacheableTask.setCacheTimeout(any()) } returns Unit
        every { cacheableTask.getFromCache(any()) } returns "Cached value"
        every { cacheableTask.needCacheUpdate("Test parameter") } returns true
        every { cacheableTask.create(Unit, any(),"Test parameter") } returns Single.error(error)

        every { taskCompletionResultNotifier.notifyRateLimitReached(10) } returns Unit

        val instance = createInstance()
        instance.enableRetryOnThrottle()
        System.setProperty(TEAMCITY_CLOUDS_AZURE_THROTTLER_QUEUE_MAX_RETRY_COUNT,  "12")

        val observer = mockk<Observer<AzureThrottlerAdapterResult<String>>>()
        every { observer.onError(any()) } returns Unit
        every { observer.onCompleted() } returns Unit

        val observableSlot = CapturingSlot<Observable<AzureThrottlerAdapterResult<String>>>()
        every { requestQueue.extractNextBatch() } returns mockk {
            every { parameter } returns "Test parameter"
            every { canBeCombined() } returns true
            every { getMaxAttempNo() } returns 12
            every { getMinCreatedDate() } returns LocalDateTime.now(Clock.systemUTC())
            every { count() } returns 10
            every { hasForceRequest() } returns false
            every {subscribeTo(capture(observableSlot), any()) } answers { observableSlot.captured.subscribe(observer) }
        }

        // When
        instance.executeNext()
        testScheduler.triggerActions()

        // Then
        verify {
            observer.onError(match { it is ThrottlerMaxRetryCountException })
        }
    }

    @Test
    fun shouldGetFromCacheWhenRetrying() {
        // Given
        val error = ThrottlerRateLimitReachedException(10, 1, "Test error")

        task = cacheableTask
        every { cacheableTask.setCacheTimeout(any()) } returns Unit
        every { cacheableTask.getFromCache(any()) } returns "Cached value"
        every { cacheableTask.needCacheUpdate("Test parameter") } returns true
        every { cacheableTask.create(Unit, any(),"Test parameter") } returns Single.error(error)

        every { taskCompletionResultNotifier.notifyRateLimitReached(10) } returns Unit

        val instance = createInstance()
        instance.enableRetryOnThrottle()

        val observer = mockk<Observer<AzureThrottlerAdapterResult<String>>>()
        every { observer.onError(any()) } returns Unit
        every { observer.onCompleted() } returns Unit

        val observableSlot = CapturingSlot<Observable<AzureThrottlerAdapterResult<String>>>()
        every { requestQueue.extractNextBatch() } returns mockk {
            every { parameter } returns "Test parameter"
            every { canBeCombined() } returns true
            every { getMaxAttempNo() } returns 1
            every { getMinCreatedDate() } returns LocalDateTime.now(Clock.systemUTC())
            every { count() } returns 10
            every { hasForceRequest() } returns false
            every {subscribeTo(capture(observableSlot), any()) } answers { observableSlot.captured.subscribe(observer) }
        }

        // When
        instance.executeNext()
        testScheduler.triggerActions()

        // Then
        verify {
            observer.onNext(match {
                r -> r.value == "Cached value" && r.fromCache == true && r.requestsCount == null
            })
        }
    }

    @Test
    fun shouldRetryWhenRetrying() {
        // Given
        val error = ThrottlerRateLimitReachedException(10, 1, "Test error")

        task = cacheableTask
        every { cacheableTask.setCacheTimeout(any()) } returns Unit
        every { cacheableTask.getFromCache(any()) } returns null
        every { cacheableTask.needCacheUpdate("Test parameter") } returns true
        every { cacheableTask.create(Unit, any(),"Test parameter") } returns Single.error(error)

        every { taskCompletionResultNotifier.notifyRateLimitReached(10) } returns Unit

        val instance = createInstance()
        instance.enableRetryOnThrottle()

        val observer = mockk<Observer<AzureThrottlerAdapterResult<String>>>()

        val minCreatedDate = LocalDateTime.now(Clock.systemUTC())
        val observableSlot = CapturingSlot<Observable<AzureThrottlerAdapterResult<String>>>()
        every { requestQueue.extractNextBatch() } returns mockk {
            every { parameter } returns "Test parameter"
            every { canBeCombined() } returns true
            every { getMaxAttempNo() } returns 1
            every { getMinCreatedDate() } returns minCreatedDate
            every { count() } returns 10
            every { hasForceRequest() } returns false
            every {subscribeTo(capture(observableSlot), any()) } answers { observableSlot.captured.subscribe(observer) }
        }
        every { requestQueue.addRequest(any(), any(), any(), any(), any(), any(), any()) } returns Unit

        // When
        instance.executeNext()
        testScheduler.triggerActions()

        // Then
        verify {
            requestQueue.addRequest(
                    match { t -> t <= LocalDateTime.now(Clock.systemUTC()).plusSeconds(10) },
                    "Test parameter",
                    any(),
                    false,
                    true,
                    2,
                    minCreatedDate
            )
        }
    }

    @Test
    fun shouldDeliverMessageWhenRetrying() {
        // Given
        val error = ThrottlerRateLimitReachedException(10, 1, "Test error")

        task = cacheableTask
        every { cacheableTask.setCacheTimeout(any()) } returns Unit
        every { cacheableTask.getFromCache(any()) } returns null
        every { cacheableTask.needCacheUpdate("Test parameter") } returns true
        every { cacheableTask.create(Unit, any(),"Test parameter") } returns Single.error(error)

        every { taskCompletionResultNotifier.notifyRateLimitReached(10) } returns Unit

        val instance = createInstance()
        instance.enableRetryOnThrottle()

        val observer = mockk<Observer<AzureThrottlerAdapterResult<String>>>()
        every { observer.onError(any()) } returns Unit
        every { observer.onCompleted() } returns Unit

        val minCreatedDate = LocalDateTime.now(Clock.systemUTC())
        val observableSlot = CapturingSlot<Observable<AzureThrottlerAdapterResult<String>>>()
        val retrySubject = CapturingSlot<Subject<AzureThrottlerAdapterResult<String>, AzureThrottlerAdapterResult<String>>>()
        every { requestQueue.extractNextBatch() } returns mockk {
            every { parameter } returns "Test parameter"
            every { canBeCombined() } returns true
            every { getMaxAttempNo() } returns 1
            every { getMinCreatedDate() } returns minCreatedDate
            every { count() } returns 10
            every { hasForceRequest() } returns false
            every {subscribeTo(capture(observableSlot), any()) } answers { observableSlot.captured.subscribe(observer) }
        }
        every {
            requestQueue.addRequest(any(), any(), capture(retrySubject), any(), any(), any(), any())
        } returns Unit

        // When
        instance.executeNext()
        testScheduler.triggerActions()
        retrySubject.captured.onNext(AzureThrottlerAdapterResult("Value from retry", null, false))

        // Then
        verify {
            observer.onNext(match {
                r -> r.value == "Value from retry" && r.fromCache == false && r.requestsCount == null
            })
        }
    }

    private fun createInstance() : AzureThrottlerTaskQueueImpl<Unit, Unit, String, String> {
        return AzureThrottlerTaskQueueImpl(
                Unit,
                requestQueue,
                task,
                adapter,
                AzureThrottlerTaskTimeExecutionType.Random,
                defaultCacheTimeoutInSeconds,
                taskCompletionResultNotifier,
                requestScheduler
        )
    }
}
