package jetbrains.buildServer.clouds.azure.arm.throttler

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.serverSide.TeamCityProperties
import rx.Observable
import rx.schedulers.Schedulers
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class AzureDeferralSequenceTimeManagerImpl : AzureDefettalSequenceTimeManager {
    private val myBucketSize = AtomicLong(getMaxBucketSize())
    private val myBucketRefillSubscription = Observable.timer(getRefillInterval(), TimeUnit.MILLISECONDS, Schedulers.io())
        .repeat()
        .subscribe {
            refill()
        }

    override fun getDeferralSequence(corellationId: String): Observable<Unit> {
        val removed = tryDecreaseBucketSize()
        if (removed) return Observable.just(Unit)

        val startTime = LocalDateTime.now()
        LOG.debug("Start waiting for the next time slot. CorellationId: ${corellationId}")
        return Observable.timer(getRefillInterval(), TimeUnit.MILLISECONDS, Schedulers.io())
            .repeat()
            .takeWhile { !tryDecreaseBucketSize() }
            .map { Unit }
            .ignoreElements()
            .concatWith(Observable.just(Unit))
            .doOnNext {
                LOG.debug("Total wait duration: ${Duration.between(startTime, LocalDateTime.now())}, CorellationId: ${corellationId}")
            }
    }

    fun refill() {
        val previousSize = myBucketSize.get()
        val size = myBucketSize.updateAndGet {
            val newValue = it + getRefillValue()
            min(newValue, getMaxBucketSize())
        }
        if (previousSize != size) {
            LOG.debug("Available requests bucket size: ${previousSize} -> ${size}")
        }
    }

    override fun dispose() {
        myBucketRefillSubscription.unsubscribe()
    }

    private fun tryDecreaseBucketSize(): Boolean {
        val removed = AtomicBoolean(false)
        myBucketSize.updateAndGet {
            if (it > 0) {
                removed.set(true)
                it - 1
            } else it
        }
        return removed.get()
    }

    companion object {
        private val LOG = Logger.getInstance(AzureDeferralSequenceTimeManagerImpl::class.java)
        private fun getMaxBucketSize() = TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_BUCKET_SIZE, 250L)
        private fun getRefillInterval() = TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_BUCKET_REFILL_INTERVAL_MSEC, 1100L)
        private fun getRefillValue() = TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_BUCKET_REFILL_VALUE, 20L)
    }
}
