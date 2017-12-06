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

package jetbrains.buildServer.clouds.azure;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * File utilities.
 */
public class FileUtilsImpl implements FileUtils {

    private static final Logger LOG = Logger.getInstance(FileUtilsImpl.class.getName());
    private static final String UNIX_SHELL_PATH = "/bin/sh";

    @Override
    public String readFile(@NotNull final File file) throws Exception {
        final File parentDir = file.getParentFile();
        if (SystemInfo.isUnix && parentDir.exists() && parentDir.isDirectory() && !parentDir.canExecute()) {
            LOG.info("Reading file content " + file + " with sudo");
            return readFileWithSudo(file);
        }

        return FileUtil.readText(file);
    }

    private String readFileWithSudo(@NotNull final File file) throws IOException {
        final GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.setExePath(UNIX_SHELL_PATH);
        commandLine.addParameter("-c");
        commandLine.addParameter(String.format("sudo cat %s", file));

        final ExecResult execResult = SimpleCommandLineProcessRunner.runCommand(commandLine, new byte[0]);
        if (execResult.getExitCode() != 0) {
            throw new IOException("Failed to read file: " + execResult.getStderr());
        }

        return StringUtil.trim(execResult.getStdout());
    }

    @Override
    public Long getCreationDate(@NotNull final File file) {
        return file.lastModified();
    }

    @Override
    public File[] listFiles(@NotNull final File directory) {
        return directory.listFiles();
    }
}
