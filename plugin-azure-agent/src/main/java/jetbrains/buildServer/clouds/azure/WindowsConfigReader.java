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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import jetbrains.buildServer.configuration.FileWatcherListener;
import jetbrains.buildServer.configuration.FilesWatcher;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Reads configuration settings on Windows.
 */
public class WindowsConfigReader extends AgentConfigReader {

    private static final Logger LOG = Logger.getInstance(WindowsConfigReader.class.getName());
    private static final String SYSTEM_DRIVE = System.getenv("SystemDrive");
    private static final String WINDOWS_PROP_FILE_DIR = SYSTEM_DRIVE + "\\WindowsAzure\\Config";
    private static final String WINDOWS_CUSTOM_DATA_FILE = SYSTEM_DRIVE + "\\AzureData\\CustomData.bin";
    private final FileUtils myFileUtils;

    public WindowsConfigReader(@NotNull final BuildAgentConfigurationEx agentConfiguration,
                               @NotNull final IdleShutdown idleShutdown,
                               @NotNull final FileUtils fileUtils) {
        super(agentConfiguration, idleShutdown);
        myFileUtils = fileUtils;
    }

    @Override
    public void process() {
        // Check custom data file existence
        final File customDataFile = new File(WINDOWS_CUSTOM_DATA_FILE);
        processCustomData(customDataFile);

        // Check properties file existence
        final File configDir = new File(WINDOWS_PROP_FILE_DIR);
        processConfigs(configDir);

        // Watch for changes in the files
        final FilesWatcher watcher = new FilesWatcher(new FilesWatcher.WatchedFilesProvider() {
            @NotNull
            @Override
            public File[] getWatchedFiles() throws IOException {
                return new File[]{configDir, customDataFile};
            }
        });

        watcher.registerListener(new FileWatcherListener() {
            @Override
            public void changesDetected(@NotNull List<File> newFiles, @NotNull List<File> modifiedFiles, @NotNull List<File> removedFiles) {
                for (File file : modifiedFiles) {
                    if (file.equals(configDir)) {
                        LOG.info(String.format("Found changes in configs directory %s", file.getAbsolutePath()));
                        processConfigs(configDir);
                    } else if (file.equals(customDataFile)) {
                        LOG.info(String.format("Found changes in custom data file %s", file.getAbsolutePath()));
                        processCustomData(customDataFile);
                    }
                }
            }
        });

        watcher.start();
    }

    private void processCustomData(File customDataFile) {
        final String customData = myFileUtils.readFile(customDataFile);
        if (StringUtil.isEmpty(customData)) {
            LOG.info(String.format(CUSTOM_DATA_FILE_IS_EMPTY, customDataFile));
        } else {
            // Process custom data
            try {
                processCustomData(customData);
            } catch (Exception e) {
                LOG.warnAndDebugDetails(String.format(UNABLE_TO_READ_CUSTOM_DATA_FILE, customDataFile), e);
            }
        }
    }

    private void processConfigs(File configDir) {
        final File[] files = myFileUtils.listFiles(configDir);
        if (files == null || files.length == 0) {
            LOG.info(String.format("Unable to find azure properties file in directory %s", WINDOWS_PROP_FILE_DIR));
        } else {
            Arrays.sort(files, new Comparator<File>() {
                public int compare(final File o1, final File o2) {
                    return myFileUtils.getCreationDate(o2).compareTo(myFileUtils.getCreationDate(o1));
                }
            });

            final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            for (File file : files) {
                final Long createDate = myFileUtils.getCreationDate(file);
                LOG.info(String.format("Found azure properties file %s, last modified %s", file.getAbsolutePath(), sdf.format(createDate)));
            }

            final File latest = files[0];

            // Process properties
            try {
                LOG.info("Using azure properties file " + latest.getAbsolutePath());
                FileUtil.readXmlFile(latest, new FileUtil.Processor() {
                    public void process(final Element element) {
                        setInstanceParameters(element);
                    }
                });
            } catch (Throwable e) {
                LOG.warnAndDebugDetails(String.format(FAILED_TO_READ_AZURE_PROPERTIES_FILE, latest), e);
            }
        }
    }
}
