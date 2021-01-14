/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.util.executors.ExecutorsFactory
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class AzureThrottlerScheduledExecutorFactortyImpl : AzureThrottlerScheduledExecutorFactorty {
    override fun create(scheduledAction: () -> Unit): AzureThrottlerScheduledExecutor {
        return AzureThrottlerScheduledExecutorImpl(scheduledAction)
    }

    class AzureThrottlerScheduledExecutorImpl(private val scheduledAction: () -> Unit) : AzureThrottlerScheduledExecutor {
        private val myScheduledExecutor : ScheduledExecutorService = ExecutorsFactory.newFixedScheduledDaemonExecutor("Azure throttler task queue executor", 1)
        private var myExecutorFuture: ScheduledFuture<*>? = null

        override fun start(): Boolean {
            if (myExecutorFuture != null) {
                return false
            }

            val period = TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_THROTTLER_QUEUE_PERIOD, 300)
            myExecutorFuture = myScheduledExecutor.scheduleAtFixedRate(
                    {
                        try
                        {
                            scheduledAction()
                        }
                        catch(e: Throwable) {
                            LOG.warnAndDebugDetails("An error occurred during processing task in Azure Throttler", e)
                        }
                    },
                    1000L,
                    period,
                    TimeUnit.MILLISECONDS)
            return true
        }

        override fun stop() {
            myExecutorFuture?.cancel(true)
            myExecutorFuture = null
        }
    }

    companion object {
        private val LOG = Logger.getInstance(AzureThrottlerScheduledExecutorFactortyImpl::class.java.name)
    }
}
