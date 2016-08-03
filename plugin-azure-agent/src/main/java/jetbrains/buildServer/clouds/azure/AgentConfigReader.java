package jetbrains.buildServer.clouds.azure;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.util.StringUtil;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Configuration reader.
 */
public abstract class AgentConfigReader {

    private static final Logger LOG = Logger.getInstance(AzurePropertiesReader.class.getName());
    protected static final String CUSTOM_DATA_FILE_IS_EMPTY = "Azure custom data file %s is empty: integration is disabled";
    protected static final String UNABLE_TO_READ_CUSTOM_DATA_FILE = "Unable to read azure custom data file %s: will use existing parameters";
    protected static final String FAILED_TO_READ_AZURE_PROPERTIES_FILE = "Failed to read azure properties file %s: integration is disabled";
    private final BuildAgentConfigurationEx myAgentConfiguration;
    private final IdleShutdown myIdleShutdown;

    public AgentConfigReader(@NotNull final BuildAgentConfigurationEx agentConfiguration,
                             @NotNull final IdleShutdown idleShutdown) {
        myAgentConfiguration = agentConfiguration;
        myIdleShutdown = idleShutdown;
    }

    abstract void process();

    protected void processCustomData(@NotNull final String serializedCustomData) {
        final CloudInstanceUserData data = CloudInstanceUserData.deserialize(serializedCustomData);
        if (data == null) {
            LOG.info(String.format("Unable to deserialize customData: '%s'", serializedCustomData));
            return;
        }

        myAgentConfiguration.setServerUrl(data.getServerAddress());
        if (data.getIdleTimeout() == null) {
            LOG.debug("Idle timeout in custom data is null");
        } else {
            LOG.info("Set idle timeout to " + data.getIdleTimeout());
            myIdleShutdown.setIdleTime(data.getIdleTimeout());
        }

        LOG.info("Set server URL to " + data.getServerAddress());
        final Map<String, String> customParams = data.getCustomAgentConfigurationParameters();
        for (String key : customParams.keySet()) {
            final String value = customParams.get(key);
            myAgentConfiguration.addConfigurationParameter(key, value);
            LOG.info(String.format("Added config parameter: {%s, %s}", key, value));
        }
    }

    protected void setInstanceParameters(@NotNull final Element documentElement) {
        final Element roleElement = documentElement.getChild("Role");
        if (roleElement == null) {
            LOG.warn("Unable to find Role element in azure properties file. Azure integration is disabled");
            return;
        }

        final Attribute nameAttribute = roleElement.getAttribute("name");
        if (nameAttribute == null) {
            LOG.warn("Unable to find Role's name attribute in azure properties file. Azure integration is disabled");
            return;
        }

        final String instanceName = nameAttribute.getValue();
        setLocalPort(documentElement, instanceName);
        setOwnAddress(documentElement, instanceName);

        final String agentName = StringUtil.trimStart(instanceName, "_");
        LOG.info("Reported azure instance name " + agentName);

        myAgentConfiguration.addConfigurationParameter(AzurePropertiesNames.INSTANCE_NAME, agentName);
    }

    private void setLocalPort(final Element documentElement, final String instanceName) {
        try {
            final XPath xPath = XPath.newInstance(String.format(
                    "string(//Instances/Instance[@id='%s']/InputEndpoints/Endpoint[@name='%s']/LocalPorts/LocalPortRange/@from)",
                    instanceName, AzurePropertiesNames.ENDPOINT_NAME));
            final String portNumber = (String) xPath.selectSingleNode(documentElement);
            if (StringUtil.isEmpty(portNumber)) {
                LOG.info("No input endpoints found in azure properties file, unable to set local port.");
                return;
            }

            try {
                final int portValue = Integer.parseInt(portNumber);
                myAgentConfiguration.setOwnPort(portValue);
                LOG.info("Own port is set to " + portValue);
            } catch (Exception e) {
                LOG.infoAndDebugDetails("Unable to set self port. Is this instance started by TeamCity?", e);
            }
        } catch (JDOMException e) {
            LOG.warnAndDebugDetails("Unable to set local port", e);
        }
    }

    private void setOwnAddress(@NotNull final Element documentElement, @NotNull final String selfInstanceName) {
        try {
            final XPath xPath = XPath.newInstance(String.format(
                    "string(//Instances/Instance[@id='%s']/InputEndpoints/Endpoint[@name='%s']/@loadBalancedPublicAddress)",
                    selfInstanceName, AzurePropertiesNames.ENDPOINT_NAME));
            final String loadBalancedAddress = (String) xPath.selectSingleNode(documentElement);
            if (StringUtil.isEmpty(loadBalancedAddress)) {
                LOG.info("No input endpoints found in azure properties file, unable to set own ip address.");
                return;
            }

            final String externalIp = loadBalancedAddress.contains(":")
                    ? loadBalancedAddress.substring(0, loadBalancedAddress.indexOf(":"))
                    : loadBalancedAddress;

            myAgentConfiguration.addAlternativeAgentAddress(externalIp);
            LOG.info("Alternative agent ip address is set to " + externalIp);
        } catch (JDOMException e) {
            LOG.warnAndDebugDetails("Unable to set ip address", e);
        }
    }
}
