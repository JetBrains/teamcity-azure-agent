/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.base.connector;

import com.intellij.openapi.diagnostic.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.executors.ExecutorsFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 7/29/2014
 *         Time: 3:51 PM
 */
public class CloudAsyncTaskExecutor {

  private static final Logger LOG = Logger.getInstance(CloudAsyncTaskExecutor.class.getName());
  private static final long LONG_TASK_TIME = 60*1000l;

  private final ScheduledExecutorService myExecutor;
  private final ConcurrentMap<AsyncCloudTask, TaskCallbackHandler> myExecutingTasks;
  private final Map<AsyncCloudTask, Long> myLongTasks = new HashMap<AsyncCloudTask, Long>();

  public CloudAsyncTaskExecutor(String prefix) {
    myExecutingTasks = new ConcurrentHashMap<AsyncCloudTask, TaskCallbackHandler>();
    myExecutor = ExecutorsFactory.newFixedScheduledDaemonExecutor(prefix, 2);
    scheduleWithFixedDelay("Check for tasks", new Runnable() {
      public void run() {
        checkTasks();
      }
    }, 0, 300, TimeUnit.MILLISECONDS);
  }

  public void executeAsync(final AsyncCloudTask operation) {
    executeAsync(operation, TaskCallbackHandler.DUMMY_HANDLER);
  }

  public void executeAsync(final AsyncCloudTask operation, final TaskCallbackHandler callbackHandler) {
    operation.executeOrGetResultAsync();
    myExecutingTasks.put(operation, callbackHandler);
  }

  public ScheduledFuture<?> scheduleWithFixedDelay(@NotNull final String taskName, @NotNull final Runnable task, final long initialDelay, final long delay, final TimeUnit unit){
    return myExecutor.scheduleWithFixedDelay(new Runnable() {
      public void run() {
        NamedThreadFactory.executeWithNewThreadName(taskName, task);
      }
    }, initialDelay, delay, unit);
  }

  public Future<?> submit(final String taskName, final Runnable r){
    return myExecutor.submit(new Runnable() {
      public void run() {
        try {
          LOG.debug("Starting " + taskName);
          NamedThreadFactory.executeWithNewThreadName(taskName, r);
        } finally {
          LOG.debug("Finished " + taskName);
        }
      }
    });
  }

  private void checkTasks() {
    for (AsyncCloudTask task : myExecutingTasks.keySet()) {
      try {
        processSingleTask(task);
      } catch (Throwable th) {
        LOG.warnAndDebugDetails("An error occurred during checking " + task, th);
      }
    }
  }

  private void processSingleTask(AsyncCloudTask task) {
    final Future<CloudTaskResult> future = task.executeOrGetResultAsync();
    if (future.isDone()) {
      final TaskCallbackHandler handler = myExecutingTasks.get(task);
      try {
        final CloudTaskResult result = future.get();
        handler.onComplete();
        if (result.isHasErrors()) {
          handler.onError(result.getThrowable());
        } else {
          handler.onSuccess();
        }
      } catch (Exception e) {
        LOG.warn(String.format("An error occurred while executing : '%s': %s", task.toString(), e.toString()));
        handler.onError(e);
      }
      myExecutingTasks.remove(task);
      if (myLongTasks.remove(task) != null) {
        final long operationTime = System.currentTimeMillis() - task.getStartTime();
        LOG.info(String.format("Long operation finished: '%s' took %d seconds to execute", task.toString(), operationTime / 1000));
      }
    } else {
      final long operationTime = System.currentTimeMillis() - task.getStartTime();
      if (operationTime > LONG_TASK_TIME) {
        final Long lastTimeReported = myLongTasks.get(task);
        if (lastTimeReported == null || (System.currentTimeMillis() - lastTimeReported) > LONG_TASK_TIME) {
          LOG.info(String.format("Detected long running task:('%s', running for %d seconds)", task.toString(), operationTime / 1000));
          myLongTasks.put(task, System.currentTimeMillis());
        }
      }
    }
  }

  public void dispose(){
    myExecutor.shutdown();
    try {
      myExecutor.awaitTermination(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {}
    myExecutingTasks.clear();
  }

}
