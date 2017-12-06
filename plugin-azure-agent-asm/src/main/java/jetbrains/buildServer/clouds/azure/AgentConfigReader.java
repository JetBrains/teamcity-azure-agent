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
import jetbrains.buildServer.util.StringUtil;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;

/**
 * Configuration reader.
 */
public abstract class AgentConfigReader {

    private static final Logger LOG = Logger.getInstance(AzurePropertiesReader.class.getName());

    protected static final String FAILED_TO_READ_AZURE_PROPERTIES_FILE = "Failed to read azure properties file %s";
    protected static final String FAILED_TO_PROCESS_AZURE_PROPERTIES_FILE = "Failed to process azure properties file %s";
    private final BuildAgentConfigurationEx myAgentConfiguration;

    public AgentConfigReader(@NotNull final BuildAgentConfigurationEx agentConfiguration) {
        myAgentConfiguration = agentConfiguration;
    }

    abstract void process();

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

        if (!myAgentConfiguration.getConfigurationParameters().containsKey(AzurePropertiesNames.INSTANCE_NAME)) {
            myAgentConfiguration.addConfigurationParameter(AzurePropertiesNames.INSTANCE_NAME, agentName);
        }
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

            if (myAgentConfiguration.getAlternativeAddresses().isEmpty()) {
                myAgentConfiguration.addAlternativeAgentAddress(externalIp);
                LOG.info("Alternative agent ip address is set to " + externalIp);
            }
        } catch (JDOMException e) {
            LOG.warnAndDebugDetails("Unable to set ip address", e);
        }
    }
}
