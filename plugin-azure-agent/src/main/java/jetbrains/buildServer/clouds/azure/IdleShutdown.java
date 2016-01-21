/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package jetbrains.buildServer.clouds.azure;

import com.intellij.openapi.diagnostic.Logger;
import java.text.MessageFormat;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import jetbrains.buildServer.agent.BuildAgentSystemInfo;
import jetbrains.buildServer.agent.impl.AgentIdleTimeTracker;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.RunCommand;
import jetbrains.buildServer.util.executors.ExecutorsFactory;

/**
 * @author Sergey.Pak
 *         Date: 10/28/2014
 *         Time: 12:13 PM
 */
public class IdleShutdown {
  private static final Logger LOG = Logger.getInstance(IdleShutdown.class.getName());
  private static final String SHUTDOWN_COMMAND_KEY = "teamcity.agent.shutdown.command";

  private final ScheduledExecutorService myService = ExecutorsFactory.newFixedScheduledDaemonExecutor("Azure instance shutdown on idle time", 1);
  private final AgentIdleTimeTracker myTracker;
  private final BuildAgentConfigurationEx myConfig;
  private final RunCommand myRunCommand;

  public IdleShutdown(final AgentIdleTimeTracker tracker,
                      final EventDispatcher<AgentLifeCycleListener> dispatcher,
                      final BuildAgentConfigurationEx config,
                      final RunCommand runCommand) {
    myTracker = tracker;
    myConfig = config;
    myRunCommand = runCommand;
    dispatcher.addListener(new AgentLifeCycleAdapter(){
      @Override
      public void agentShutdown() {
        myService.shutdownNow();
      }
    });
  }

  public void setIdleTime(final long idleTime) {

    LOG.info(MessageFormat.format("Agent will be automatically shutdown after {0} minutes of inactivity.", idleTime / 1000 / 60));

    final Runnable r = new Runnable() {
      public void run() {
        final long actualIdle = myTracker.getIdleTime();
        if (actualIdle > idleTime) {
          LOG.warn("Agent was idle for " + actualIdle / 1000 / 60 + " minutes. Cloud profile timeout was " + idleTime / 1000 / 60  + " minutes. Instance will be shut down");
          shutdownInstance();
          return;
        }

        //Check again
        myService.schedule(this, 10 + idleTime - actualIdle, TimeUnit.MILLISECONDS);
      }
    };
    r.run();
  }

  private void shutdownInstance() {
    LOG.info("To change this command define '" + SHUTDOWN_COMMAND_KEY +
             "' property with proper shutdown command in the buildAgent.properties file");

    for (String cmd : getShutdownCommands()) {
      LOG.info("Shutting down agent with command: " + cmd);

      myRunCommand.runCommand(cmd, new RunCommand.LoggerOutputProcessor(LOG, "Shutdown"));
    }
  }

  private String[] getShutdownCommands() {
    String cmd = myConfig.getCustomProperties().get(SHUTDOWN_COMMAND_KEY);
    if (cmd != null) {
      return new String[]{cmd};
    }

    final String os = System.getProperty("os.name").toLowerCase();
    LOG.info("Shutdown instance commands for " + os);

    final BuildAgentSystemInfo info = myConfig.getSystemInfo();

    if (info.isUnix() || info.isMac()) {
      return new String[]{
        "shutdown -Ph now", "halt -p", "poweroff",
        "sudo shutdown -Ph now", "sudo halt -p", "sudo poweroff",
      };
    }

    if (info.isWindows()) {
      return new String[]{
        "shutdown -s -t 1 -c \"TeamCity Agent Azure Instance shutdown on idle time\" -f",
        "shutdown /s /t 1 /c \"TeamCity Agent Azure Instance shutdown on idle time\" /f"
      };
    }
    LOG.warn("No command fow shutdown. Add '" + SHUTDOWN_COMMAND_KEY + "' property to the buildAgent.properties file with shutdown command");
    return new String[0];
  }
}
