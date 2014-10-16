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

package jetbrains.buildServer.clouds.base.connector;

import com.intellij.openapi.diagnostic.Logger;
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

  private ScheduledExecutorService myExecutor;
  private final ConcurrentMap<Future<CloudTaskResult>, TaskCallbackHandler> myExecutingTasks;

  public CloudAsyncTaskExecutor(String prefix) {
    myExecutingTasks = new ConcurrentHashMap<Future<CloudTaskResult>, TaskCallbackHandler>();
    myExecutor = ExecutorsFactory.newFixedScheduledDaemonExecutor(prefix, 2);
    myExecutor.scheduleWithFixedDelay(new Runnable() {
      public void run() {
        checkTasks();
      }
    }, 0, 300, TimeUnit.MILLISECONDS);
  }

  public void executeAsync(final AsyncCloudTask operation) {
    executeAsync(operation, TaskCallbackHandler.DUMMY_HANDLER);
  }

  public void executeAsync(final AsyncCloudTask operation, final TaskCallbackHandler callbackHandler) {
    final Future<CloudTaskResult> future = operation.executeAsync();
    myExecutingTasks.put(future, callbackHandler);
  }

  public ScheduledFuture<?> scheduleWithFixedDelay(@NotNull final Runnable task, final long initialDelay, final long delay, final TimeUnit unit){
    return myExecutor.scheduleWithFixedDelay(task, initialDelay, delay, unit);
  }

  public Future<?> submit(Runnable r){
    return myExecutor.submit(r);
  }

  private void checkTasks() {
    for (Future<CloudTaskResult> executingTask : myExecutingTasks.keySet()) {
      if (executingTask.isDone()) {
        final TaskCallbackHandler handler = myExecutingTasks.get(executingTask);
        try {
          final CloudTaskResult result = executingTask.get();
          handler.onComplete();
          if (result.isHasErrors()) {
            handler.onError(result.getThrowable());
          } else {
            handler.onSuccess();
          }
        } catch (Exception e) {
          LOG.info("Error: " + e.toString());
          handler.onError(e);
        }
        myExecutingTasks.remove(executingTask);
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
