

package jetbrains.buildServer.clouds.base.errors;

/**
 * @author Sergey.Pak
 *         Date: 11/13/2014
 *         Time: 2:13 PM
 */
public interface ErrorMessageUpdater {

  String getFriendlyErrorMessage(String message);

  String getFriendlyErrorMessage(String message, String defaultMessage);

  String getFriendlyErrorMessage(Throwable th);

  String getFriendlyErrorMessage(Throwable th, String defaultMessage);
}
