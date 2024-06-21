package jetbrains.buildServer.clouds.base.errors;

/**
 * @author Sergey.Pak
 *         Date: 11/12/2014
 *         Time: 5:54 PM
 */
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
