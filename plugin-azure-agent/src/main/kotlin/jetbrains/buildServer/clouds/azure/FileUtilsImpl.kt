package jetbrains.buildServer.clouds.azure

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import jetbrains.buildServer.SimpleCommandLineProcessRunner
import jetbrains.buildServer.util.FileUtil
import java.io.File
import java.io.IOException

/**
 * File utilities.
 */
class FileUtilsImpl : FileUtils {

    override fun readFile(file: File): String {
        return try {
            FileUtil.readText(file).let { contents ->
                if (contents.isEmpty() && file.supportsSudo) {
                    return readFileWithSudo(file)
                } else contents
            }
        } catch (e: Exception) {
            if (file.supportsSudo) {
                return readFileWithSudo(file)
            }

            throw e
        }
    }

    private fun readFileWithSudo(file: File): String {
        LOG.info("Reading file $file contents with sudo")

        val commandLine = GeneralCommandLine()
        commandLine.exePath = UNIX_SHELL_PATH
        commandLine.addParameter("-c")
        commandLine.addParameter("sudo cat $file")

        val execResult = SimpleCommandLineProcessRunner.runCommand(commandLine, ByteArray(0))
        if (execResult.exitCode != 0) {
            throw IOException("Failed to read file: ${execResult.stderr}")
        }

        return execResult.stdout.trim()
    }

    override fun getCreationDate(file: File) = file.lastModified()

    override fun listFiles(directory: File): Array<File> = directory.listFiles()

    companion object {
        private val LOG = Logger.getInstance(FileUtilsImpl::class.java.name)
        private const val UNIX_SHELL_PATH = "/bin/sh"

        val File.supportsSudo
            get() = SystemInfo.isUnix && parentFile.isDirectory
    }
}
