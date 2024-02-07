

package jetbrains.buildServer.clouds.base.errors;

import jetbrains.buildServer.util.StringUtil;

/**
 * Default error message updater.
 */
public class DefaultErrorMessageUpdater implements ErrorMessageUpdater {
    @Override
    public String getFriendlyErrorMessage(String message) {
        return message;
    }

    @Override
    public String getFriendlyErrorMessage(String message, String defaultMessage) {
        if (StringUtil.isEmpty(message)) {
            return "No details available";
        }

        return defaultMessage;
    }

    @Override
    public String getFriendlyErrorMessage(Throwable th) {
        final String message = th.getMessage();
        return getFriendlyErrorMessage(message, message);
    }

    @Override
    public String getFriendlyErrorMessage(Throwable th, String defaultMessage) {
        return getFriendlyErrorMessage(th.getMessage(), defaultMessage);
    }
}
