package jetbrains.buildServer.clouds.azure;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * File utilities.
 */
public interface FileUtils {
    /**
     * Gets a file contents.
     *
     * @param file is a file.
     * @return contents.
     */
    String readFile(@NotNull File file);

    /**
     * Gets a file creation date.
     *
     * @param file is a file.
     * @return creation date as timestamp.
     */
    Long getCreationDate(@NotNull File file);

    /**
     * Lists files in the directory.
     *
     * @param directory is a directory.
     * @return list of files.
     */
    File[] listFiles(@NotNull File directory);
}
