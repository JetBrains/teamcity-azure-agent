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
        final File propertiesFile = new File(UNIX_PROP_FILE);
        final String xmlData = myFileUtils.readFile(propertiesFile);
        if (StringUtil.isEmpty(xmlData)) {
            LOG.info(String.format("Azure properties file %s is empty. Azure integration is disabled", propertiesFile));
            return;
        }

        try {
            final Element documentElement = FileUtil.parseDocument(new ByteArrayInputStream(xmlData.getBytes()), false);
            if (documentElement == null) {
                LOG.info(String.format("Unable to read azure properties file %s. Azure integration is disabled", propertiesFile));
                return;
            }

            setInstanceParameters(documentElement);
        } catch (Exception e) {
            LOG.infoAndDebugDetails(String.format("Failed to read azure properties file %s. Azure integration is disabled", propertiesFile), e);
            LOG.info("File contents: " + xmlData);
        }

        final File customDataFile = new File(UNIX_CUSTOM_DATA_FILE);
        final String customData = myFileUtils.readFile(customDataFile);
        if (StringUtil.isEmpty(customData)) {
            LOG.info(String.format("Custom data file %s is empty. Will use existing parameters", customDataFile));
            return;
        }

        try {
            final Element documentElement = FileUtil.parseDocument(new ByteArrayInputStream(customData.getBytes()), false);
            if (documentElement == null) {
                LOG.info(String.format("Unable to read azure custom data file %s. Will use existing parameters", customDataFile));
                return;
            }

            readCustomData(documentElement);
        } catch (Exception e) {
            LOG.infoAndDebugDetails(String.format("Unable to read azure custom data file %s. Will use existing parameters", customDataFile), e);
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
