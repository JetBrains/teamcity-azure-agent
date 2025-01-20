package jetbrains.buildServer.clouds.base.errors;

public interface ErrorMessageUpdater {

  String getFriendlyErrorMessage(String message);

  String getFriendlyErrorMessage(String message, String defaultMessage);

  String getFriendlyErrorMessage(Throwable th);

  String getFriendlyErrorMessage(Throwable th, String defaultMessage);
}
