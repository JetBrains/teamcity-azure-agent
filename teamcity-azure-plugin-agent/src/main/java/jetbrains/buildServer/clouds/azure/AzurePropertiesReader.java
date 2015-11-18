/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package jetbrains.buildServer.clouds.azure;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 4/23/2014
 *         Time: 6:40 PM
 */
public class AzurePropertiesReader {

  private static final Logger LOG = Logger.getInstance(AzurePropertiesReader.class.getName());
  private static final String UNIX_SHELL_PATH = "/bin/sh";
  private static final String UNIX_CONFIG_DIR = "/var/lib/waagent/";
  private static final String UNIX_PROP_FILE = UNIX_CONFIG_DIR + "SharedConfig.xml";
  private static final String UNIX_CUSTOM_DATA_FILE = UNIX_CONFIG_DIR + "ovf-env.xml";
  private static final String WINDOWS_PROP_FILE_DIR="C:\\WindowsAzure\\Config";
  private static final String WINDOWS_CUSTOM_DATA_FILE="C:\\AzureData\\CustomData.bin";

  private final BuildAgentConfigurationEx myAgentConfiguration;
  @NotNull private final IdleShutdown myIdleShutdown;


  public AzurePropertiesReader(@NotNull final BuildAgentConfigurationEx agentConfiguration,
                               @NotNull final EventDispatcher<AgentLifeCycleListener> events,
                               @NotNull final IdleShutdown idleShutdown) {
    myIdleShutdown = idleShutdown;
    LOG.info("Azure plugin initializing...");
    myAgentConfiguration = agentConfiguration;
    events.addListener(new AgentLifeCycleAdapter(){
      @Override
      public void afterAgentConfigurationLoaded(@NotNull final BuildAgent agent) {
        if (SystemInfo.isLinux || SystemInfo.isFreeBSD){
          processUnixConfig();
        } else if (SystemInfo.isWindows){
          processWindowsConfig();
        } else {
          LOG.warn(String.format("Azure integration is disabled: unsupported OS family %s(%s)", SystemInfo.OS_ARCH, SystemInfo.OS_VERSION));
        }
      }
    });
  }

  private void processWindowsConfig() {
    File configDir = new File(WINDOWS_PROP_FILE_DIR);
    final File[] files = configDir.listFiles();
    if (files == null || files.length == 0) {
      LOG.info("Unable to find azure properties file. Azure integration is disabled");
      return;
    }
    Arrays.sort(files, new Comparator<File>() {
      public int compare(final File o1, final File o2) {
        return new Long(o2.lastModified()).compareTo(o1.lastModified());
      }
    });
    File latest = files[0];
    FileUtil.readXmlFile(latest, new FileUtil.Processor() {
      public void process(final Element element) {
        addInstanceName(element);
      }
    });

    File customDataFile = new File(WINDOWS_CUSTOM_DATA_FILE);
    if (customDataFile.exists()){
      try {
        final String customData = FileUtil.readText(customDataFile);
        if (customData != null) {
          processCustomData(customData);
        }
      } catch (IOException e) {
        LOG.info("Unable to read customData file: " + e.toString());
      }
    }
  }

  private void processUnixConfig() {
    final String xmlData = readFile(UNIX_PROP_FILE);
    if (StringUtil.isEmpty(xmlData)){
      LOG.info("Unable to find azure properties file. Azure integration is disabled");
      return;
    }
    try {
      final Element documentElement = FileUtil.parseDocument(new ByteArrayInputStream(xmlData.getBytes()), false);
      if (documentElement == null) {
        LOG.info("Unable to read azure properties file. Azure integration is disabled");
        return;
      }
      addInstanceName(documentElement);
    } catch (Exception e) {
      LOG.info("Unable to read azure properties file. Azure integration is disabled:" + xmlData);
      LOG.info(e.toString());
      LOG.debug(e.toString(), e);
    }

    final String customData = readFile(UNIX_CUSTOM_DATA_FILE);
    if (StringUtil.isEmpty(customData)){
      LOG.info("Empty custom data. Will use existing parameters");
      return;
    }
    try {
      final Element documentElement = FileUtil.parseDocument(new ByteArrayInputStream(customData.getBytes()), false);
      if (documentElement == null) {
        LOG.info("Unable to read azure custom data. Will use existing parameters");
        return;
      }
      readCustomData(documentElement);
    } catch (Exception e) {
      LOG.info("Unable to read azure custom data. Will use existing parameters: " + customData);
      LOG.info(e.toString());
      LOG.debug(e.toString(), e);
    }
  }

  private void addInstanceName(final Element documentElement){
    final Element roleElem = documentElement.getChild("Role");
    if (roleElem == null)
      return;
    final Attribute nameAttr = roleElem.getAttribute("name");
    if (nameAttr != null){
      final String instanceName = nameAttr.getValue();
      addLocalPort(documentElement, instanceName);
      setOwnAddress(documentElement, instanceName);
      myAgentConfiguration.addConfigurationParameter(AzurePropertiesNames.INSTANCE_NAME, instanceName);
      LOG.info("Reported azure instance name " + instanceName);
    } else {
      LOG.info("Unable to find azure properties file. Azure integration is disabled");
    }
  }

  private void addLocalPort(@NotNull final Element documentElement, @NotNull final String selfInstanceName){
    try {
      final XPath xPath = XPath.newInstance(String.format(
        "string(//Instances/Instance[@id='%s']/InputEndpoints/Endpoint[@name='%s']/LocalPorts/LocalPortRange/@from)",
        selfInstanceName, AzurePropertiesNames.ENDPOINT_NAME));
      final Object value = xPath.selectSingleNode(documentElement);
      try {
        final int portValue = Integer.parseInt(String.valueOf(value));
        myAgentConfiguration.setOwnPort(portValue);
        LOG.info("Own port is set to " + portValue);
      } catch (Exception ex){
        LOG.warnAndDebugDetails("Unable to set self port. Azure integration will experience problems", ex);
      }
    } catch (JDOMException e) {
      LOG.warn("", e);
    }
  }

  private void setOwnAddress(@NotNull final Element documentElement, @NotNull final String selfInstanceName){
    try {
      final XPath xPath = XPath.newInstance(String.format(
        "string(//Instances/Instance[@id='%s']/InputEndpoints/Endpoint[@name='%s']/@loadBalancedPublicAddress)",
        selfInstanceName, AzurePropertiesNames.ENDPOINT_NAME));
      final Object value = xPath.selectSingleNode(documentElement);
      final String loadBalancedAddress = String.valueOf(value);
      final String externalIp = loadBalancedAddress.contains(":") ? loadBalancedAddress.substring(0, loadBalancedAddress.indexOf(":")) : loadBalancedAddress;
      myAgentConfiguration.addAlternativeAgentAddress(externalIp);
      LOG.info("Added alternative address is set to " + externalIp);
    } catch (JDOMException e) {
      LOG.warn("", e);
    }
  }

  private void readCustomData(@NotNull final Element documentElement){
    final XPath xPath;
    try {
      xPath = XPath.newInstance("string(//wa:LinuxProvisioningConfigurationSet/wa:CustomData)");
      final Object value = xPath.selectSingleNode(documentElement);
      if (value != null){
        final String serializedCustomData = String.valueOf(value);
        if (StringUtil.isNotEmpty(serializedCustomData)) {
          processCustomData(serializedCustomData);
        }
      }
    } catch (JDOMException e) {
      LOG.warn("Unable to read custom data", e);
    }
  }

  private void processCustomData(@NotNull final String serializedCustomData) {
    final CloudInstanceUserData data = CloudInstanceUserData.deserialize(serializedCustomData);
    if (data != null) {
      myAgentConfiguration.setServerUrl(data.getServerAddress());
      if (data.getIdleTimeout() != null) {
        LOG.info("Set idle timeout to " + data.getIdleTimeout());
        myIdleShutdown.setIdleTime(data.getIdleTimeout());
      }
      LOG.info("Set server URL to " + data.getServerAddress());
      final Map<String, String> customParams = data.getCustomAgentConfigurationParameters();
      for (String key : customParams.keySet()) {
        myAgentConfiguration.addConfigurationParameter(key, customParams.get(key));
        LOG.info(String.format("added config param: {%s, %s}", key, customParams.get(key)));
      }
    } else {
      LOG.info(String.format("Unable to deserialize customData: '%s'", serializedCustomData));
    }
  }

  private String readFile(@NotNull final String filePath){
    LOG.info("Attempting to read Azure properties from " + filePath);
    final File file = new File(filePath);
    final File parentDir = file.getParentFile();
    if (!parentDir.exists() || !parentDir.isDirectory()){
      LOG.info("Azure config dir not found at " + parentDir);
      return null; // no waagent dir
    }
    if (parentDir.canExecute()){
      try {
        return FileUtil.readText(file);
      } catch (IOException e) {
        LOG.info(e.toString());
        return "";
      }
    } else {
      LOG.info("Reading properties from " + filePath + " with sudo");
      return readFileWithSudo(filePath);
    }
  }
  private String readFileWithSudo(@NotNull final String filePath){
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(UNIX_SHELL_PATH);
    commandLine.addParameter("-c");
    commandLine.addParameter(String.format("sudo cat %s", filePath));
    final ExecResult execResult = SimpleCommandLineProcessRunner.runCommand(commandLine, new byte[0]);
    if (execResult.getExitCode() != 0 ){
      final String stderr = execResult.getStderr();
      LOG.info(stderr);
    }
    return StringUtil.trim(execResult.getStdout());
  }


}
