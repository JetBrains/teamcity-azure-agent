

package jetbrains.buildServer.clouds.azure.arm.throttler

import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.credentials.AzureTokenCredentials
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureApi
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureApiImpl
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.version.ServerVersionHolder
import rx.Observable
import rx.Scheduler
import rx.Single
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

class AzureThrottlerAdapterImpl (
        azureConfigurable: AzureConfigurableWithNetworkInterceptors,
        resourceGraphConfigurable: ResourceGraphConfigurableWithNetworkInterceptors,
        credentials: AzureTokenCredentials,
        subscriptionId: String?,
        private val timeManager: AzureTimeManager,
        private val delayScheduler: Scheduler,
        override val name: String
) : AzureThrottlerAdapter<AzureApi> {
    @Suppress("JoinDeclarationAndAssignment")
    private var myInterceptor: AzureThrottlerInterceptor

    private val myAzure: AzureApi

    private val myRemainingReads = AtomicLong(DEFAULT_REMAINING_READS_PER_HOUR)
    private val myWindowStartTime = AtomicReference<LocalDateTime>(LocalDateTime.now(Clock.systemUTC()))
    private val myDefaultReads = AtomicLong(DEFAULT_REMAINING_READS_PER_HOUR)
    private var myTaskContext = ThreadLocal<TaskContext?>()

    init {
        myInterceptor = AzureThrottlerInterceptor(this, this, name)

        myAzure = AzureApiImpl(
            azureConfigurable
                .configureProxy()
                .withNetworkInterceptor(myInterceptor)
                .withUserAgent("TeamCity Server ${ServerVersionHolder.getVersion().displayVersion}")
                .authenticate(credentials)
                .withSubscription(subscriptionId),
            resourceGraphConfigurable
                .configureProxy()
                .withNetworkInterceptor(myInterceptor)
                .withUserAgent("TeamCity Server ${ServerVersionHolder.getVersion().displayVersion}")
                .authenticate(credentials)
                .withSubscription(subscriptionId))

        myAzure
                .deployments()
                .manager()
                .inner()
                .azureClient
                .setLongRunningOperationRetryTimeout(TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_DEPLOYMENT_LONG_RUNNING_QUERY_RETRY_TIMEOUT, 30))
    }
    override val api: AzureApi
        get() = myAzure


    override fun getDefaultReads(): Long {
        return myDefaultReads.get()
    }

    override fun setThrottlerTime(milliseconds: Long) {
        myInterceptor.setThrottlerTime(milliseconds)
    }

    override fun getThrottlerTime(): Long {
        return myInterceptor.getThrottlerTime()
    }

    override fun getWindowWidthInMilliseconds(): Long {
        return max(0,
                myWindowStartTime.get().plusHours(1).toInstant(ZoneOffset.UTC).toEpochMilli() -
                        LocalDateTime.now(Clock.systemUTC()).toInstant(ZoneOffset.UTC).toEpochMilli())
    }

    override fun getWindowStartDateTime(): LocalDateTime {
        return myWindowStartTime.get()
    }

    override fun getRemainingReads(): Long {
        return myRemainingReads.get()
    }

    override fun <T> execute(queryFactory: (AzureApi, AzureTaskContext) -> Single<T>): Single<AzureThrottlerAdapterResult<T>> {
        val taskContext = TaskContext(myTaskContext, timeManager, delayScheduler)
        return taskContext
            .getDeferralSequence()
            .flatMap {
                queryFactory(myAzure, taskContext)
                    .doOnSubscribe {
                        myTaskContext.set(taskContext)
                    }
                    .doOnUnsubscribe {
                        myTaskContext.remove()
                    }
                    .map {
                        AzureThrottlerAdapterResult(
                            it,
                            taskContext.getRequestSequenceLength(),
                            false)
                    }
                    .toObservable()
            }.toSingle()
    }

    override fun notifyRemainingReads(value: Long?, requestCount: Long) {
        if (value == null) {
            myRemainingReads.getAndUpdate { max(MIN_REMAINING_READS, it - requestCount) }
        } else {
            if (myRemainingReads.get() < value) {
                myWindowStartTime.set(LocalDateTime.now(Clock.systemUTC()))
            }
            myRemainingReads.set(max(MIN_REMAINING_READS, value))
            myDefaultReads.getAndUpdate { max(it, myRemainingReads.get()) }
        }
    }

    override fun logDiagnosticInfo() {
        LOG.debug("[${name}] info: " +
                "Default reads: ${getDefaultReads()}, " +
                "Remaining reads: ${getRemainingReads()}, " +
                "Window start time: ${getWindowStartDateTime()}, " +
                "Window width: ${Duration.ofMillis(getWindowWidthInMilliseconds())}, " +
                "Throttler time: ${Duration.ofMillis(getThrottlerTime())}")
    }

    override fun getContext(): AzureTaskContext? = myTaskContext.get()

    class TaskContext(
        private val storage: ThreadLocal<TaskContext?>,
        private val timeManager: AzureTimeManager,
        private val delayScheduler: Scheduler
    ) : AzureTaskContext {
        private val myCorellationId: String
        private val requestSequenceLength = AtomicLong(0)
        override val corellationId: String
            get() = myCorellationId

        init {
            myCorellationId = UUID.randomUUID().toString()
        }

        override fun apply() = storage.set(this)

        override fun getRequestSequenceLength(): Long = requestSequenceLength.get()

        override fun increaseRequestsSequenceLength() { requestSequenceLength.incrementAndGet() }

        override fun getDeferralSequence(): Observable<Unit> {
            return Observable.defer {
                if (TeamCityProperties.getBoolean(TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_NEW_THROTTLING_MODEL_DISABLE)) {
                    val ticket = timeManager.getTicket(myCorellationId)
                    LOG.debug("Operation delay details: CorellationId: ${myCorellationId}, offset: ${ticket.getOffset()}")
                    Observable.just(Unit)
                        .delaySubscription(ticket.getOffset().toMillis(), TimeUnit.MILLISECONDS, delayScheduler)
                } else {
                    timeManager
                        .getDeferralSequence(corellationId)
                }
            }
                .map { apply() }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(AzureThrottlerAdapterImpl::class.java.name)
        private val MIN_REMAINING_READS = 1L
    }
}
