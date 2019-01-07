/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
