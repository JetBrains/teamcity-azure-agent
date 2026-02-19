package jetbrains.buildServer.clouds.azure

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.AgentRunningBuildEx
import jetbrains.buildServer.agent.BuildAgent
import jetbrains.buildServer.agent.CurrentBuildTracker
import jetbrains.buildServer.messages.DefaultMessagesInfo
import jetbrains.buildServer.serverSide.TeamCityProperties
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SpotInstanceTerminationCheckerImpl(
        private val myBuildTracker: CurrentBuildTracker,
        private val myBuildAgent: BuildAgent,
        private val myScheduler: AzureScheduledExecutorService
) : SpotInstanceTerminationChecker {
    private lateinit var myCancellable: AzureCancellable
    private val myIsCancelling = AtomicBoolean(false)

    override fun start(resourceName: String) {
        LOG.info("Subscribe on Azure scheduledEvents metadata")
        val task = object : Runnable {
            override fun run() {
                if (myIsCancelling.get()) return

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
                }?.let(::handleEvictionEvent)
            }

            private fun handleEvictionEvent(event: AzureMetadata.Event) {
                try {
                    val message = "The spot instance is scheduled for termination at ${event.notBefore}"
                    LOG.info(message)

                    myIsCancelling.set(true)

                    val runningBuild = myBuildTracker.currentBuild as? AgentRunningBuildEx

                    val cancelBuild = getCancelBuildConfig(runningBuild)
                    if (cancelBuild) {
                        runningBuild?.interruptBuild(message, true)
                        myBuildAgent.shutdown()
                    } else {
                        runningBuild?.buildLogger?.logMessage(DefaultMessagesInfo.createTextMessage(message))
                    }
                } finally {
                    myCancellable.cancel()
                }
            }

            private fun getCancelBuildConfig(runningBuild: AgentRunningBuildEx?): Boolean =
                (TeamCityProperties.getBooleanOrTrue(CANCEL_BUILD_ON_INSTANCE_TERMINATION)
                        && (runningBuild?.sharedConfigParameters?.getOrDefault(
                    CANCEL_BUILD_ON_INSTANCE_TERMINATION,
                    "true"
                ) == "true"))
        }

        myCancellable = myScheduler.scheduleWithFixedDelay(task, 0, 1, TimeUnit.SECONDS)
    }

    companion object {
        private val LOG = Logger.getInstance(SpotInstanceTerminationChecker::class.java.name)
        private val PREEMPT_EVENT_TYPE = "Preempt"
        private val RESOURCE_TYPE = "VirtualMachine"

        private const val CANCEL_BUILD_ON_INSTANCE_TERMINATION = "teamcity.internal.azure.agents.spot.cancelBuildOnInstanceTermination"
    }
}
