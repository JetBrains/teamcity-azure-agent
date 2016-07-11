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
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * File utilities.
 */
public class FileUtilsImpl implements FileUtils {

    private static final Logger LOG = Logger.getInstance(FileUtilsImpl.class.getName());
    private static final String UNIX_SHELL_PATH = "/bin/sh";

    @Override
    public String readFile(@NotNull final File file) {
        final File parentDir = file.getParentFile();
        if (!parentDir.exists() || !parentDir.isDirectory()) {
            LOG.info("Parent directory not found at " + parentDir);
            return null; // no waagent dir
        }

        if (!parentDir.canExecute() && SystemInfo.isUnix) {
            LOG.info("Reading file content " + file + " with sudo");
            return readFileWithSudo(file);
        }

        if (!file.exists()) {
            LOG.info("File " + file + " not found");
            return StringUtil.EMPTY;
        }

        try {
            return FileUtil.readText(file);
        } catch (IOException e) {
            LOG.infoAndDebugDetails("Failed to read file " + file, e);
            return StringUtil.EMPTY;
        }
    }

    private String readFileWithSudo(@NotNull final File file) {
        final GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.setExePath(UNIX_SHELL_PATH);
        commandLine.addParameter("-c");
        commandLine.addParameter(String.format("sudo cat %s", file));

        final ExecResult execResult = SimpleCommandLineProcessRunner.runCommand(commandLine, new byte[0]);
        if (execResult.getExitCode() != 0) {
            final String stderr = execResult.getStderr();
            LOG.info(stderr);
        }

        return StringUtil.trim(execResult.getStdout());
    }

    @Override
    public Long getCreationDate(@NotNull final File file) {
        if (FILEATTRIBUTES_CLASS != null) {
            try {
                Object path = FILE_TO_PATH_METHOD.invoke(file);
                Object options = Array.newInstance(LINKOPTION_CLASS, 0);
                Object attributes = FILES_READ_ATTRIBUTES_METHOD.invoke(null, path, FILEATTRIBUTES_CLASS, options);
                return (Long) FILETIME_TO_MILLIS_METHOD.invoke(FILEATTRIBUTES_CREATION_TIME_METHOD.invoke(attributes));
            } catch (Exception e) {
                LOG.infoAndDebugDetails("Unable to get file create date", e);
            }
        }

        return file.lastModified();
    }

    @Override
    public File[] listFiles(@NotNull final File directory) {
        return directory.listFiles();
    }

    // java.nio.file.Files is available since Java 1.7
    private static final Class FILEATTRIBUTES_CLASS;
    private static final Class LINKOPTION_CLASS;
    private static final Method FILE_TO_PATH_METHOD;
    private static final Method FILES_READ_ATTRIBUTES_METHOD;
    private static final Method FILEATTRIBUTES_CREATION_TIME_METHOD;
    private static final Method FILETIME_TO_MILLIS_METHOD;

    static {
        Class<?> fileAttributes = null;
        Class<?> linkOption = null;
        Method readAttributes = null;
        Method toPath = null;
        Method creationTime = null;
        Method toMillis = null;

        try {
            fileAttributes = Class.forName("java.nio.file.attribute.BasicFileAttributes");
            linkOption = Class.forName("java.nio.file.LinkOption");
            Class<?> linkOptions = Class.forName("[Ljava.nio.file.LinkOption;");
            Class<?> filesClass = Class.forName("java.nio.file.Files");
            Class<?> pathClass = Class.forName("java.nio.file.Path");
            Class<?> fileTime = Class.forName("java.nio.file.attribute.FileTime");

            readAttributes = filesClass.getDeclaredMethod("readAttributes", pathClass, Class.class, linkOptions);
            creationTime = fileAttributes.getDeclaredMethod("creationTime");
            toPath = File.class.getDeclaredMethod("toPath");
            toMillis = fileTime.getDeclaredMethod("toMillis");
        } catch (Exception e) {
            LOG.debug("Unable to get java.nio.file.Files", e);
        }

        FILE_TO_PATH_METHOD = toPath;
        FILES_READ_ATTRIBUTES_METHOD = readAttributes;
        FILEATTRIBUTES_CLASS = fileAttributes;
        FILEATTRIBUTES_CREATION_TIME_METHOD = creationTime;
        FILETIME_TO_MILLIS_METHOD = toMillis;
        LINKOPTION_CLASS = linkOption;
    }
}
