

package jetbrains.buildServer.clouds.azure

import java.io.FileNotFoundException

object AzureUtils {
    private val FILE_NOT_FOUND_CAUSE = Regex("\\(([^)]+)\\)")

    fun getFileNotFoundMessage(e: FileNotFoundException): String {
        val errorMessage = e.message!!
        FILE_NOT_FOUND_CAUSE.matchEntire(errorMessage)?.let {
            val (message) = it.destructured
            return message
        }

        return errorMessage
    }
}
