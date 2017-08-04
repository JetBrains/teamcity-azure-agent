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
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.File;

/**
 * Reads configuration settings on Unix.
 */
public class UnixConfigReader extends AgentConfigReader {

    private static final Logger LOG = Logger.getInstance(UnixConfigReader.class.getName());
    private static final String UNIX_CONFIG_DIR = "/var/lib/waagent/";
    private static final String UNIX_PROP_FILE = UNIX_CONFIG_DIR + "SharedConfig.xml";
    private final FileUtils myFileUtils;

    public UnixConfigReader(@NotNull final BuildAgentConfigurationEx agentConfiguration,
                            @NotNull final FileUtils fileUtils) {
        super(agentConfiguration);
        myFileUtils = fileUtils;
    }

    @Override
    public void process() {
        // Check properties file existence
        final File propertiesFile = new File(UNIX_PROP_FILE);
        final String xmlData = myFileUtils.readFile(propertiesFile);
        if (StringUtil.isEmpty(xmlData)) {
            LOG.info(String.format("Azure properties file %s is empty", propertiesFile));
            return;
        }

        // Process properties
        try {
            final Element documentElement = FileUtil.parseDocument(new ByteArrayInputStream(xmlData.getBytes()), false);
            if (documentElement == null) {
                LOG.warn(String.format("Unable to read azure properties file %s", propertiesFile));
                return;
            }

            setInstanceParameters(documentElement);
        } catch (Exception e) {
            LOG.warnAndDebugDetails(String.format(FAILED_TO_READ_AZURE_PROPERTIES_FILE, propertiesFile), e);
            LOG.info("File contents: " + xmlData);
        }
    }
}
