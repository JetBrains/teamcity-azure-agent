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
import org.apache.commons.codec.binary.Base64;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads configuration settings on Unix.
 */
public class UnixConfigReader extends AgentConfigReader {

    private static final Logger LOG = Logger.getInstance(UnixConfigReader.class.getName());
    private static final String UNIX_CONFIG_DIR = "/var/lib/waagent/";
    private static final String UNIX_PROP_FILE = UNIX_CONFIG_DIR + "SharedConfig.xml";
    private static final String UNIX_CUSTOM_DATA_FILE = UNIX_CONFIG_DIR + "ovf-env.xml";
    private static final String WINDOWSAZURE_NAMESPACE = "http://schemas.microsoft.com/windowsazure";
    private final FileUtils myFileUtils;

    public UnixConfigReader(@NotNull final BuildAgentConfigurationEx agentConfiguration,
                            @NotNull final IdleShutdown idleShutdown,
                            @NotNull final FileUtils fileUtils) {
        super(agentConfiguration, idleShutdown);
        myFileUtils = fileUtils;
    }

    @Override
    public void process() {
        // Check custom data existence
        final File customDataFile = new File(UNIX_CUSTOM_DATA_FILE);
        final String customData = myFileUtils.readFile(customDataFile);
        if (StringUtil.isEmpty(customData)) {
            LOG.info(String.format(CUSTOM_DATA_FILE_IS_EMPTY, customDataFile));
        } else {
            // Process custom data
            try {
                final Element documentElement = FileUtil.parseDocument(new ByteArrayInputStream(customData.getBytes()), false);
                if (documentElement == null) {
                    LOG.warn(String.format(UNABLE_TO_READ_CUSTOM_DATA_FILE, customDataFile));
                } else {
                    readCustomData(documentElement);
                }
            } catch (Exception e) {
                LOG.warnAndDebugDetails(String.format(UNABLE_TO_READ_CUSTOM_DATA_FILE, customDataFile), e);
            }
        }

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

    private void readCustomData(@NotNull final Element documentElement) throws JDOMException {
        //noinspection unchecked
        final List<Namespace> namespaces = new ArrayList<Namespace>(documentElement.getAdditionalNamespaces());
        namespaces.add(documentElement.getNamespace());

        String prefix = null;
        for (Namespace namespace : namespaces) {
            if (namespace.getURI().equals(WINDOWSAZURE_NAMESPACE)) {
                prefix = namespace.getPrefix();
                break;
            }
        }

        if (prefix == null) {
            LOG.warn(String.format("Unable to find %s namespace in file %s", WINDOWSAZURE_NAMESPACE, UNIX_CUSTOM_DATA_FILE));
            return;
        }

        final String customDataQuery = String.format("string(//%s:LinuxProvisioningConfigurationSet/%s:CustomData)", prefix, prefix);
        final XPath xPath = XPath.newInstance(customDataQuery);
        final Object value = xPath.selectSingleNode(documentElement);
        if (value == null) {
            LOG.warn(String.format("Unable to read CustomData element in file %s", UNIX_CUSTOM_DATA_FILE));
            return;
        }

        String serializedCustomData = String.valueOf(value);
        if (StringUtil.isEmpty(serializedCustomData)) {
            LOG.warn(String.format("CustomData element in file %s is empty", UNIX_CUSTOM_DATA_FILE));
            return;
        }

        final byte[] bytes = serializedCustomData.getBytes();
        if (!Base64.isBase64(bytes)) {
            LOG.warn(String.format("CustomData value should be Base64 encoded in file %s is empty", UNIX_CUSTOM_DATA_FILE));
            return;
        }

        // New azure linux agent execute additional Base64 encode
        final byte[] decodedBytes = Base64.decodeBase64(bytes);
        if (Base64.isBase64(decodedBytes)) {
            serializedCustomData = new String(decodedBytes);
        }

        processCustomData(serializedCustomData);
    }
}
