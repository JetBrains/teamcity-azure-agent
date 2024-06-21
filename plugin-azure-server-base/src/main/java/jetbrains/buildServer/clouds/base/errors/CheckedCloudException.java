package jetbrains.buildServer.clouds.base.errors;

public class CheckedCloudException extends Exception {

  public CheckedCloudException(final Throwable cause) {
    super(cause);
  }

  public CheckedCloudException(final String message) {
    super(message);
  }

  public CheckedCloudException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
