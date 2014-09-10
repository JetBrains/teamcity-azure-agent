package jetbrains.buildServer.clouds.azure.connector;

import com.microsoft.windowsazure.core.OperationStatus;
import com.microsoft.windowsazure.core.OperationStatusResponse;
import java.util.concurrent.*;
import jetbrains.buildServer.clouds.base.connector.AsyncCloudTask;
import jetbrains.buildServer.clouds.base.connector.CloudTaskResult;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 8/5/2014
 *         Time: 6:59 PM
 */
public class AzureTaskWrapper implements AsyncCloudTask {


  private final Callable<Future<OperationStatusResponse>> myResponseFuture;

  public AzureTaskWrapper(Callable<Future<OperationStatusResponse>> operation) {
    myResponseFuture = operation;
  }

  public Future<CloudTaskResult> executeAsync() {
    try {
      final Future<OperationStatusResponse> call = myResponseFuture.call();
      return new Future<CloudTaskResult>() {
        public boolean cancel(final boolean mayInterruptIfRunning) {
          return call.cancel(mayInterruptIfRunning);
        }

        public boolean isCancelled() {
          return call.isCancelled();
        }

        public boolean isDone() {
          return call.isDone();
        }

        public CloudTaskResult get() throws InterruptedException, ExecutionException {
          final OperationStatusResponse response = call.get();
          if (response.getStatus() == OperationStatus.Succeeded) {
            return new CloudTaskResult(response.getId());
          } else if (response.getStatus() == OperationStatus.Failed) {
            return new CloudTaskResult(true, response.getError().getMessage(), null);
          } else {
            return new CloudTaskResult(true, String.format("Illegal status response: %s requestId: %s", response.getStatus(), response.getRequestId()), null);
          }
        }

        public CloudTaskResult get(final long timeout, @NotNull final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
          final OperationStatusResponse response = call.get(timeout, unit);
          if (response.getStatus() == OperationStatus.Succeeded) {
            return new CloudTaskResult(response.getId());
          } else if (response.getStatus() == OperationStatus.Failed) {
            return new CloudTaskResult(true, response.getError().getMessage(), null);
          } else {
            throw new TimeoutException(String.format("Request %s is still in progress ", response.getId()));
          }
        }
      };
    } catch (Exception e) {
      return createExceptionFuture(e);
    }
  }
  Future<CloudTaskResult> createExceptionFuture(final Exception e) {
    return new Future<CloudTaskResult>() {
      public boolean cancel(final boolean mayInterruptIfRunning) {
        return false;
      }

      public boolean isCancelled() {
        return false;
      }

      public boolean isDone() {
        return true;
      }

      public CloudTaskResult get() throws InterruptedException, ExecutionException {
        throw new ExecutionException (e);
      }

      public CloudTaskResult get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new ExecutionException (e);
      }
    };
  }

}