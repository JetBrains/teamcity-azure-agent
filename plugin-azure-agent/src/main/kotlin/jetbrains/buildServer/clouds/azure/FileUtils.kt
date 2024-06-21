package jetbrains.buildServer.clouds.azure

import java.io.File

/**
 * File utilities.
 */
interface FileUtils {
    /**
     * Gets a file contents.
     *
     * @param file is a file.
     * @return contents.
     */
    fun readFile(file: File): String

    /**
     * Gets a file creation date.
     *
     * @param file is a file.
     * @return creation date as timestamp.
     */
    fun getCreationDate(file: File): Long

    /**
     * Lists files in the directory.
     *
     * @param directory is a directory.
     * @return list of files.
     */
    fun listFiles(directory: File): Array<File>
}
