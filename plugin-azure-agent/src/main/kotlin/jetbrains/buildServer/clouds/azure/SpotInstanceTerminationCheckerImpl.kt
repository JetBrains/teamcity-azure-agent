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

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.AgentRunningBuildEx
import jetbrains.buildServer.agent.BuildAgent
import jetbrains.buildServer.agent.CurrentBuildTracker
import jetbrains.buildServer.messages.DefaultMessagesInfo
import java.util.concurrent.TimeUnit

class SpotInstanceTerminationCheckerImpl(
        private val myBuildTracker: CurrentBuildTracker,
        private val myBuildAgent: BuildAgent,
        private val myScheduler: AzureScheduledExecutorService
) : SpotInstanceTerminationChecker {
    private lateinit var myCancellable: AzureCancellable

    override fun start(resourceName: String) {
        LOG.info("Subscribe on Azure scheduledEvents metadata")
        val task = Runnable {
            val eventsMetadata = try {
                AzureMetadata.readScheduledEventsMetadata()
            } catch (e: Throwable) {
                LOG.info("Azure scheduledEvents metadata is not available: ${e.message}")
                LOG.debug(e)
                null
            }
            eventsMetadata?.events?.firstOrNull {
                PREEMPT_EVENT_TYPE == it.eventType &&
                        (it.resources ?: emptyList()).contains(resourceName) &&
                        RESOURCE_TYPE == it.resourceType
            }?.let {
                LOG.info("The spot instance is scheduled for eviction at ${it.notBefore}")

                val currentBuild = myBuildTracker.currentBuild as AgentRunningBuildEx?
                currentBuild?.buildLogger?.logMessage(DefaultMessagesInfo.createTextMessage(String.format("The spot instance is scheduled for eviction at ${it.notBefore}.")))

                try {
                    AzureMetadata.approveEvent(eventsMetadata.documentIncarnation, it.eventId)
                } catch (e: Throwable) {
                    LOG.info("Could not approve Azure scheduled event: ${it.eventId}, message: ${e.message}")
                    LOG.debug(e)
                }

                myCancellable.cancel()
            }
        }
        myCancellable = myScheduler.scheduleWithFixedDelay(task, 10, 1, TimeUnit.SECONDS)
    }

    companion object {
        private val LOG = Logger.getInstance(SpotInstanceTerminationChecker::class.java.name)
        private val PREEMPT_EVENT_TYPE = "Preempt"
        private val RESOURCE_TYPE = "VirtualMachine"
    }
}
