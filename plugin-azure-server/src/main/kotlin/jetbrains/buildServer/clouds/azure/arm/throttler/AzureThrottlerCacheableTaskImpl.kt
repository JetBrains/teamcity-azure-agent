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

import com.google.common.cache.CacheBuilder
import com.microsoft.azure.management.Azure
import jetbrains.buildServer.serverSide.TeamCityProperties
import rx.Single
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

abstract class AzureThrottlerCacheableTaskBaseImpl<P, T> : AzureThrottlerTaskBaseImpl<Azure, P, T>(), AzureThrottlerCacheableTask<Azure, P, T> {
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

    override fun create(api: Azure, parameter: P): Single<T> {
        return createQuery(api, parameter)
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
        return lastUpdatedDateTime == null || lastUpdatedDateTime.plusSeconds(getTaskThrottleTime()) < LocalDateTime.now(Clock.systemUTC())
    }

    override fun areParametersEqual(parameter: P, other: P): Boolean {
        return parameter == other
    }

    private fun getTaskThrottleTime(): Long {
        return TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_THROTTLER_TASK_THROTTLE_TIMEOUT_SEC, 10)
    }

    protected abstract fun createQuery(api: Azure, parameter: P): Single<T>

    data class CacheValue<T>(val value: T, val lastUpdatedDateTime : LocalDateTime)

    private fun createCache() = CacheBuilder
            .newBuilder()
            .expireAfterWrite(TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_THROTTLER_TASK_CACHE_TIMEOUT_SEC, 32 * 60), TimeUnit.SECONDS)
            .build<P, CacheValue<T>>()
}
