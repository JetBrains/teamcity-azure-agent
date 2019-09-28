package jetbrains.buildServer.clouds.azure.arm.throttler

import com.microsoft.azure.credentials.AzureTokenCredentials
import com.microsoft.azure.management.Azure
import rx.Single
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

class AzureThrottlerAdapterImpl (
        azureConfigurable: Azure.Configurable,
        credentials: AzureTokenCredentials,
        subscriptionId: String?
) : AzureThrottlerAdapter<Azure> {
    private var myInterceptor: AzureThrottlerInterceptor
    private val myAzure: Azure

    private val myRemainingReads = AtomicLong(0)

    private val myWindowStartTime = AtomicReference<LocalDateTime>(LocalDateTime.now())
    private val myDefaultReads = AtomicLong(12000)

    init {
        myInterceptor = AzureThrottlerInterceptor(this)

        myAzure = azureConfigurable
                .withInterceptor(myInterceptor)
                .authenticate(credentials)
                .withSubscription(subscriptionId)

        myAzure
                .deployments()
                .manager()
                .inner()
                .azureClient
                .setLongRunningOperationRetryTimeout(60)
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

    override fun getWindowWidthInSeconds(): Long {
        return max(0,
                myWindowStartTime.get().plusHours(1).toInstant(ZoneOffset.UTC).epochSecond -
                        LocalDateTime.now(Clock.systemUTC()).toInstant(ZoneOffset.UTC).epochSecond)
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

