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
