package jetbrains.buildServer.clouds.azure.arm.throttler

import com.google.common.cache.CacheBuilder
import com.microsoft.azure.management.Azure
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

abstract class AzureThrottlerCacheableTaskBaseImpl<P, T> : AzureThrottlerCacheableTask<Azure, P, T> {
    private val myTimeoutInSeconds = AtomicLong(60)
    private val myCache = AtomicReference(createCache())

    override fun getFromCache(flow: AzureThrottlerFlow, parameter: P): T? {
        return myCache.get().getIfPresent(parameter)
    }

    override fun setToCache(parameter: P, value: T?) {
        if (value != null) {
            myCache.get().put(parameter, value)
        } else {
            myCache.get().invalidate(parameter)
        }
    }

    override fun invalidateCache() {
        myCache.get().invalidateAll()
    }

    override fun setCacheTimeout(timeoutInSeconds: Long) {
        if (myTimeoutInSeconds.get() == timeoutInSeconds) return

        myTimeoutInSeconds.set(timeoutInSeconds)
        val newCache = createCache()
        newCache.putAll(myCache.get().asMap())

        myCache.set(newCache)
    }

    private fun createCache() = CacheBuilder.newBuilder().expireAfterWrite(myTimeoutInSeconds.get(), TimeUnit.SECONDS).build<P, T>()
}
