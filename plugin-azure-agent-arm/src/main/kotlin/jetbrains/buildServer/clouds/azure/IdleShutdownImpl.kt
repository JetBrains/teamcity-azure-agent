package jetbrains.buildServer.clouds.azure

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.agent.BuildAgentConfigurationEx
import jetbrains.buildServer.agent.impl.AgentIdleTimeTracker
import jetbrains.buildServer.util.EventDispatcher
import jetbrains.buildServer.util.RunCommand
import jetbrains.buildServer.util.executors.ExecutorsFactory
import java.util.concurrent.TimeUnit

/**
 * @author Sergey.Pak
 * *         Date: 10/28/2014
 * *         Time: 12:13 PM
 */
open class IdleShutdownImpl(private val myTracker: AgentIdleTimeTracker,
                            dispatcher: EventDispatcher<AgentLifeCycleListener>,
                            private val myConfig: BuildAgentConfigurationEx,
                            private val myRunCommand: RunCommand) : IdleShutdown {

    private val myService = ExecutorsFactory.newFixedScheduledDaemonExecutor("Azure instance shutdown on idle time", 1)

    init {
        dispatcher.addListener(object : AgentLifeCycleAdapter() {
            override fun agentShutdown() {
                myService.shutdownNow()
            }
        })
    }

    override fun setIdleTime(idleTime: Long) {
        LOG.info("Agent will be automatically shutdown after ${idleTime / 1000 / 60} minutes of inactivity.")

        val r = object : Runnable {
            override fun run() {
                val actualIdle = myTracker.idleTime
                if (actualIdle > idleTime) {
                    LOG.warn("Agent was idle for ${actualIdle / 1000 / 60} minutes. Cloud profile timeout was ${idleTime / 1000 / 60} minutes. Instance will be shut down")
                    shutdownInstance()
                    return
                }

                //Check again
                myService.schedule(this, 10 + idleTime - actualIdle, TimeUnit.MILLISECONDS)
            }
        }
        r.run()
    }

    private fun shutdownInstance() {
        LOG.info("To change this command define '$SHUTDOWN_COMMAND_KEY' property with proper shutdown command in the buildAgent.properties file")

        for (cmd in shutdownCommands) {
            LOG.info("Shutting down agent with command: $cmd")
            myRunCommand.runCommand(cmd, RunCommand.LoggerOutputProcessor(LOG, "Shutdown"))
        }
    }

    private val shutdownCommands: Array<String>
        get() {
            val cmd = myConfig.configurationParameters[SHUTDOWN_COMMAND_KEY]
            if (cmd != null) {
                return arrayOf(cmd)
            }

            val os = System.getProperty("os.name")
            LOG.info("Shutdown instance commands for $os")

            val info = myConfig.systemInfo

            if (info.isUnix || info.isMac) {
                return arrayOf("shutdown -Ph now", "halt -p", "poweroff", "sudo shutdown -Ph now", "sudo halt -p", "sudo poweroff")
            }

            if (info.isWindows) {
                return arrayOf("shutdown -s -t 1 -c \"TeamCity Azure Instance shutdown on idle time\" -f", "shutdown /s /t 1 /c \"TeamCity Azure Instance shutdown on idle time\" /f")
            }
            LOG.warn("No command for shutdown. Add '$SHUTDOWN_COMMAND_KEY' property to the buildAgent.properties file with shutdown command")
            return emptyArray()
        }

    companion object {
        private val LOG = Logger.getInstance(IdleShutdown::class.java.name)
        private const val SHUTDOWN_COMMAND_KEY = "teamcity.agent.shutdown.command"
    }
}
