

package jetbrains.buildServer.clouds.base.connector;

/**
 * @author Sergey.Pak
 *         Date: 7/29/2014
 *         Time: 6:37 PM
 */
public class TaskCallbackHandler {

  public static final TaskCallbackHandler DUMMY_HANDLER = new TaskCallbackHandler();

  public void onSuccess() {

  }

  public void onError(Throwable error) {

  }

  public void onComplete() {

  }

}
