

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
