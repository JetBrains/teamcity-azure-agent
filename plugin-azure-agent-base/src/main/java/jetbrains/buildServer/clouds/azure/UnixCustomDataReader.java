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
import java.util.ArrayList;
import java.util.List;

public class UnixCustomDataReader extends AzureCustomDataReader {

    private static final Logger LOG = Logger.getInstance(UnixCustomDataReader.class.getName());
    private static final String UNIX_CONFIG_DIR = "/var/lib/waagent/";
    private static final String UNIX_CUSTOM_DATA_FILE = UNIX_CONFIG_DIR + "ovf-env.xml";
    private static final String WINDOWSAZURE_NAMESPACE = "http://schemas.microsoft.com/windowsazure";

    public UnixCustomDataReader(@NotNull final BuildAgentConfigurationEx agentConfiguration,
                                @NotNull final IdleShutdown idleShutdown,
                                @NotNull final FileUtils fileUtils) {
        super(agentConfiguration, idleShutdown, fileUtils);
    }

    @Override
    protected String getCustomDataFileName() {
        return UNIX_CUSTOM_DATA_FILE;
    }

    @Override
    protected void parseCustomData(String customData) {
        // Process custom data
        try {
            final Element documentElement = FileUtil.parseDocument(new ByteArrayInputStream(customData.getBytes()), false);
            if (documentElement == null) {
                LOG.warn(String.format(UNABLE_TO_READ_CUSTOM_DATA_FILE, getCustomDataFileName()));
            } else {
                readCustomData(documentElement);
            }
        } catch (Exception e) {
            LOG.warnAndDebugDetails(String.format(UNABLE_TO_READ_CUSTOM_DATA_FILE, getCustomDataFileName()), e);
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
        if (!Base64.isArrayByteBase64(bytes)) {
            LOG.warn(String.format("CustomData value should be Base64 encoded in file %s is empty", UNIX_CUSTOM_DATA_FILE));
            return;
        }

        // New azure linux agent execute additional Base64 encode
        final byte[] decodedBytes = Base64.decodeBase64(bytes);
        if (Base64.isArrayByteBase64(decodedBytes)) {
            serializedCustomData = new String(decodedBytes);
        }

        processCustomData(serializedCustomData);
    }
}
