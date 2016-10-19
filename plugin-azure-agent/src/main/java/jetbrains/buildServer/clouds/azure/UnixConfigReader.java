package jetbrains.buildServer.clouds.azure;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;
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
    private static final String UNIX_CUSTOM_DATA_FILE = UNIX_CONFIG_DIR + "ovf-env.xml";
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
        final XPath xPath = XPath.newInstance("string(//wa:LinuxProvisioningConfigurationSet/wa:CustomData)");
        final Object value = xPath.selectSingleNode(documentElement);
        if (value == null) {
            LOG.warn(String.format("Unable to read CustomData element in file %s", UNIX_CUSTOM_DATA_FILE));
            return;
        }

        final String serializedCustomData = String.valueOf(value);
        if (StringUtil.isEmpty(serializedCustomData)) {
            LOG.warn(String.format("CustomData element in file %s is empty", UNIX_CUSTOM_DATA_FILE));
            return;
        }

        processCustomData(serializedCustomData);
    }
}
