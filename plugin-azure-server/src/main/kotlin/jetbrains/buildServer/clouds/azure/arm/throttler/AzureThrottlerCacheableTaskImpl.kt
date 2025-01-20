package jetbrains.buildServer.clouds.azure.arm.throttler

import com.google.common.cache.CacheBuilder
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureApi
import jetbrains.buildServer.serverSide.TeamCityProperties
import rx.Single
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

abstract class AzureThrottlerCacheableTaskBaseImpl<P, T> : AzureThrottlerTaskBaseImpl<AzureApi, P, T>(), AzureThrottlerCacheableTask<AzureApi, P, T> {
    private val myTimeoutInSeconds = AtomicLong(60)
    private val myCache = createCache()

    override fun getFromCache(parameter: P): T? {
        return myCache.getIfPresent(parameter)?.value
    }

    override fun invalidateCache() {
        myCache.invalidateAll()
    }

    override fun setCacheTimeout(timeoutInSeconds: Long) {
        myTimeoutInSeconds.set(timeoutInSeconds)
    }

    override fun create(api: AzureApi, taskContext: AzureTaskContext, parameter: P): Single<T> {
        return createQuery(api, taskContext, parameter)
                .doOnSuccess {
                    myCache.put(parameter, CacheValue(it, LocalDateTime.now(Clock.systemUTC())))
                }
    }

    override fun needCacheUpdate(parameter: P): Boolean {
        val lastUpdatedDateTime = myCache.getIfPresent(parameter)?.lastUpdatedDateTime
        return lastUpdatedDateTime == null || lastUpdatedDateTime.plusSeconds(myTimeoutInSeconds.get()) < LocalDateTime.now(Clock.systemUTC())
    }

    override fun checkThrottleTime(parameter: P): Boolean {
        val lastUpdatedDateTime = myCache.getIfPresent(parameter)?.lastUpdatedDateTime
        return lastUpdatedDateTime != null && lastUpdatedDateTime.plusSeconds(getTaskThrottleTime()) < LocalDateTime.now(Clock.systemUTC())
    }

    override fun areParametersEqual(parameter: P, other: P): Boolean {
        return parameter == other
    }

    private fun getTaskThrottleTime(): Long {
        return TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_THROTTLER_TASK_THROTTLE_TIMEOUT_SEC, 5)
    }

    protected abstract fun createQuery(api: AzureApi, taskContext: AzureTaskContext, parameter: P): Single<T>

    data class CacheValue<T>(val value: T, val lastUpdatedDateTime : LocalDateTime)

    private fun createCache() = CacheBuilder
            .newBuilder()
            .expireAfterWrite(TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_THROTTLER_TASK_CACHE_TIMEOUT_SEC, 32 * 60), TimeUnit.SECONDS)
            .build<P, CacheValue<T>>()
}
