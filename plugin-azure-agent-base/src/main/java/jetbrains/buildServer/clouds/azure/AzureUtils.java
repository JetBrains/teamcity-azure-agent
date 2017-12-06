package jetbrains.buildServer.clouds.azure;

import java.io.FileNotFoundException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AzureUtils {

    private static Pattern FILE_NOT_FOUND_CAUSE = Pattern.compile("\\(([^)]+)\\)");

    public static String getFileNotFoundMessage(FileNotFoundException e) {
        final String message = e.getMessage();
        final Matcher matcher = FILE_NOT_FOUND_CAUSE.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return message;
    }
}
