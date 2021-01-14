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

package jetbrains.buildServer.clouds.azure

import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.util.EventDispatcher
import jetbrains.buildServer.util.executors.ExecutorsFactory
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class AzureScheduledExecutorServiceImpl(private val dispatcher: EventDispatcher<AgentLifeCycleListener>) : AzureScheduledExecutorService {
    private var myService: ScheduledExecutorService

    init {
        myService = ExecutorsFactory.newFixedScheduledDaemonExecutor("Azure Agent Periodical Checker", 1)
        dispatcher.addListener(object : AgentLifeCycleAdapter() {
            override fun agentShutdown() {
                myService.shutdown()
            }
        })
    }

    override fun scheduleWithFixedDelay(r: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit): AzureCancellable {
        return AzureCancellableImpl(myService.scheduleWithFixedDelay(r, initialDelay, delay, unit))
    }

    private class AzureCancellableImpl(private val myFuture: Future<*>) : AzureCancellable {
        override fun cancel() {
            myFuture.cancel(false)
        }
    }
}
