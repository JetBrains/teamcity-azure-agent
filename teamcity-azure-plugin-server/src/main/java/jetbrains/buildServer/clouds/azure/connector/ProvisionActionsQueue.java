package jetbrains.buildServer.clouds.azure.connector;

import com.intellij.openapi.diagnostic.Logger;
import com.microsoft.windowsazure.exception.ServiceException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 9/25/2014
 *         Time: 7:29 PM
 */
public class ProvisionActionsQueue{
  private static final Logger LOG = Logger.getInstance(ProvisionActionsQueue.class.getName());
  private static final Map<String, AtomicReference<String>> requestsQueue = new HashMap<String, AtomicReference<String>>();
  public static final Pattern CONFLICT_ERROR_PATTERN = Pattern.compile("Windows Azure is currently performing an operation with x-ms-requestid ([0-9a-f]{32}) on this deployment that requires exclusive access.");

  public static boolean isLocked(@NotNull final String serviceName){
    final String key = serviceName;
    return requestsQueue.get(key) == null || requestsQueue.get(key).get() == null;
  }

  public static synchronized void queueAction(@NotNull final String serviceName, @NotNull final InstanceAction action){
    if (!requestsQueue.containsKey(serviceName)){
      requestsQueue.put(serviceName, new AtomicReference<String>(null));
    }
    ConditionalRunner.addConditional(new ConditionalRunner.Conditional() {
      @NotNull
      public String getName() {
        return "Start handler of '" + action.getName() + "'";
      }

      public boolean canExecute(){
        return requestsQueue.get(serviceName).get() == null;
      }

      public boolean execute() throws Exception {
        try {

          final String actionId = action.action();
          ConditionalRunner.addConditional(createFromActionId(action, actionId, serviceName));
          requestsQueue.get(serviceName).set(actionId);
          return true;
        } catch (ServiceException ex){
          LOG.warn(ex.toString(), ex);
          if (ex.getErrorMessage() == null)
            throw ex;
          final Matcher matcher = CONFLICT_ERROR_PATTERN.matcher(ex.getErrorMessage());
          if (matcher.matches()){
            requestsQueue.get(serviceName).set(matcher.group(1));
            return false;
          } else {
            throw ex;
          }
        }
      }
    });
  }

  public static Runnable getRequestCheckerCleanable(@NotNull final ActionIdChecker actionIdChecker){
    return new Runnable() {
      public void run() {
        try {
          for (AtomicReference<String> reference : requestsQueue.values()) {
            if (reference.get() == null)
              continue;
            if (actionIdChecker.isActionFinished(reference.get())){
              reference.set(null);
            }
          }
        } catch (Exception ex){}
      }
    };
  }

  private static ConditionalRunner.Conditional createFromActionId(@NotNull final InstanceAction action, @NotNull final String actionId, @NotNull final String key){
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

  public static interface InstanceAction{
    @NotNull String getName();
    @NotNull String action() throws ServiceException, IOException;
    @NotNull ActionIdChecker getActionIdChecker();
    void onFinish();
  }

}
