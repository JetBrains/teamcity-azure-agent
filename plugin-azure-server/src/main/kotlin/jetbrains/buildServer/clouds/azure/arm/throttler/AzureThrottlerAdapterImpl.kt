package jetbrains.buildServer.clouds.azure.arm.throttler

import com.microsoft.azure.credentials.AzureTokenCredentials
import com.microsoft.azure.management.Azure
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.version.ServerVersionHolder
import rx.Single
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

class AzureThrottlerAdapterImpl (
        azureConfigurable: AzureConfigurableWithNetworkInterceptors,
        credentials: AzureTokenCredentials,
        subscriptionId: String?,
        name: String
) : AzureThrottlerAdapter<Azure> {
    @Suppress("JoinDeclarationAndAssignment")
    private var myInterceptor: AzureThrottlerInterceptor

    private val myAzure: Azure

    private val myRemainingReads = AtomicLong(DEFAULT_REMAINING_READS_PER_HOUR)
    private val myWindowStartTime = AtomicReference<LocalDateTime>(LocalDateTime.now())
    private val myDefaultReads = AtomicLong(DEFAULT_REMAINING_READS_PER_HOUR)

    init {
        myInterceptor = AzureThrottlerInterceptor(this, name)

        myAzure = azureConfigurable
                .configureProxy()
                .withNetworkInterceptor(myInterceptor)
                .withUserAgent("TeamCity Server ${ServerVersionHolder.getVersion().displayVersion}")
                .authenticate(credentials)
                .withSubscription(subscriptionId)

        myAzure
                .deployments()
                .manager()
                .inner()
                .azureClient
                .setLongRunningOperationRetryTimeout(TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_DEPLOYMENT_LONG_RUNNING_QUERY_RETRY_TIMEOUT, 60))
    }
    override val api: Azure
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

    override fun <T> execute(queryFactory: (Azure) -> Single<T>): Single<AzureThrottlerAdapterResult<T>> {
        val startRemainingReads = myRemainingReads.get()
        return queryFactory(myAzure).map {
            val remainingReads = myRemainingReads.get()
            val readsCount = if (remainingReads > startRemainingReads) myDefaultReads.get() - remainingReads else startRemainingReads - remainingReads
            AzureThrottlerAdapterResult(
                    it,
                    if (readsCount > 0) readsCount else null,
                    false)
        }
    }

    override fun notifyRemainingReads(value: Long?) {
        if (value == null) return

        if (myRemainingReads.get() < value) {
            myWindowStartTime.set(LocalDateTime.now(Clock.systemUTC()))
        }
        myRemainingReads.set(value)
        myDefaultReads.getAndUpdate { max(it, myRemainingReads.get()) }
    }
}
