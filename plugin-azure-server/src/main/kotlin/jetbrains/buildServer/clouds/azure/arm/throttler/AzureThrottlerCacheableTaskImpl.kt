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

    private fun createCache() = CacheBuilder.newBuilder().expireAfterWrite(myTimeoutInSeconds.get(), TimeUnit.SECONDS).recordStats().build<P, T>()
}
