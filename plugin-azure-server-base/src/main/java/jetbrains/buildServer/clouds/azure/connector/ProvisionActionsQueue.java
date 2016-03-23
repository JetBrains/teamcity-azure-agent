/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.connector;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.base.connector.CloudAsyncTaskExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Sergey.Pak
 *         Date: 9/25/2014
 *         Time: 7:29 PM
 */
public class ProvisionActionsQueue {
  private static final Logger LOG = Logger.getInstance(ProvisionActionsQueue.class.getName());
  private static final Pattern CONFLICT_ERROR_PATTERN = Pattern.compile("Windows Azure is currently performing an operation with x-ms-requestid ([0-9a-f]{32}) on this deployment that requires exclusive access.");
  private static final Pattern PORT_ERROR_PATTERN = Pattern.compile("Port (\\d+) is already in use by one of the endpoints in this deployment. Ensure that the port numbers are unique across endpoints within a deployment.");

  private final Map<String, AtomicReference<String>> requestsQueue = new HashMap<>();
  private final ConditionalRunner myRunner = new ConditionalRunner();

  public ProvisionActionsQueue(final CloudAsyncTaskExecutor asyncTaskExecutor) {
    asyncTaskExecutor.scheduleWithFixedDelay("Update instances", myRunner, 0, 5, TimeUnit.SECONDS);
  }

  public boolean isLocked(@NotNull final String serviceName) {
    return requestsQueue.get(serviceName) == null || requestsQueue.get(serviceName).get() == null;
  }

  public synchronized void queueAction(@NotNull final String serviceName, @NotNull final InstanceAction action) {
    if (!requestsQueue.containsKey(serviceName)) {
      requestsQueue.put(serviceName, new AtomicReference<String>(null));
    }
    myRunner.addConditional(new ConditionalRunner.Conditional() {
      @NotNull
      public String getName() {
        return "Start handler of '" + action.getName() + "'";
      }

      public boolean canExecute() {
        return requestsQueue.get(serviceName).get() == null;
      }

      public boolean execute() throws Exception {
        try {
          final String actionId = action.action();
          myRunner.addConditional(createFromActionId(action, actionId, serviceName));
          requestsQueue.get(serviceName).set(actionId);
          return true;
        } catch (Exception ex) {
          LOG.warn("An error occurred while attempting to execute " + getName() + ": " + ex.toString(), ex);
          if (ex.getMessage() == null) {
            action.onError(ex);
            throw ex;
          }
          final Matcher matcher = CONFLICT_ERROR_PATTERN.matcher(ex.getMessage());
          if (matcher.matches()) {
            requestsQueue.get(serviceName).set(matcher.group(1));
            return false;
          } else {
            Matcher portMatcher = PORT_ERROR_PATTERN.matcher(ex.getMessage());
            if (portMatcher.matches()) {
              return false;
            }
          }
          action.onError(ex);
          throw ex;
        }
      }
    });
  }

  public Runnable getRequestCheckerCleanable(@NotNull final ActionIdChecker actionIdChecker) {
    return new Runnable() {
      public void run() {
        try {
          for (AtomicReference<String> reference : requestsQueue.values()) {
            if (reference.get() == null)
              continue;
            if (actionIdChecker.isActionFinished(reference.get())) {
              reference.set(null);
            }
          }
        } catch (Exception ex) {
        }
      }
    };
  }

  private ConditionalRunner.Conditional createFromActionId(@NotNull final InstanceAction action, @NotNull final String actionId, @NotNull final String key) {
    return new ConditionalRunner.Conditional() {
      @NotNull
      public String getName() {
        return "Finish handler of '" + action.getName() + "'";
      }

      public boolean canExecute() throws Exception {
        return action.getActionIdChecker().isActionFinished(actionId);
      }

      public boolean execute() throws Exception {
        requestsQueue.get(key).set(null);
        action.onFinish();
        return true;
      }
    };

  }

  public interface InstanceAction {
    @NotNull
    String getName();

    @NotNull
    String action() throws Exception;

    @NotNull
    ActionIdChecker getActionIdChecker();

    void onFinish();

    void onError(Throwable th);
  }

}
