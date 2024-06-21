package jetbrains.buildServer.clouds.base.connector;

public class TaskCallbackHandler {

  public static final TaskCallbackHandler DUMMY_HANDLER = new TaskCallbackHandler();

  public void onSuccess() {

  }

  public void onError(Throwable error) {

  }

  public void onComplete() {

  }

}
