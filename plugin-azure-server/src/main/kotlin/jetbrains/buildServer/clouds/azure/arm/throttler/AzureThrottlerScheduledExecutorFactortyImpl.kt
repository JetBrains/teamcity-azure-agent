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
