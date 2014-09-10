package jetbrains.buildServer.clouds.azure;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.agent.*;
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
  private static final String LINUX_PROP_FILE= "/var/lib/waagent/SharedConfig.xml";
  private static final String WINDOWS_PROP_FILE_DIR="C:\\WindowsAzure\\Config";

  private final BuildAgentConfigurationEx myAgentConfiguration;


  public AzurePropertiesReader(final BuildAgentConfigurationEx agentConfiguration,
                               @NotNull EventDispatcher<AgentLifeCycleListener> events) {
    LOG.info("Azure plugin initializing...");
    myAgentConfiguration = agentConfiguration;
    events.addListener(new AgentLifeCycleAdapter(){
      @Override
      public void afterAgentConfigurationLoaded(@NotNull final BuildAgent agent) {
        if (SystemInfo.isLinux){
          final String xmlData = readFileWithSudo();
          if (StringUtil.isEmpty(xmlData)){
            LOG.info("Unable to find azure properties file. Azure integration is disabled");
            return;
          }
          try {
            final Element documentElement = FileUtil.parseDocument(new StringReader(xmlData), false);
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
        } else if (SystemInfo.isWindows){
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
        } else {
          LOG.warn(String.format("Azure integration is disablled: unsupported OS family %s(%s)", SystemInfo.OS_ARCH, SystemInfo.OS_VERSION));
        }
      }
    });
  }

  private void addInstanceName(final Element documentElement){
    final Element roleElem = documentElement.getChild("Role");
    if (roleElem == null)
      return;
    final Attribute nameAttr = roleElem.getAttribute("name");
    if (nameAttr != null){
      final String instanceName = nameAttr.getValue();
      addLocalPort(documentElement, instanceName);
      setOwnAddress(documentElement);
      myAgentConfiguration.addConfigurationParameter(AzurePropertiesNames.INSTANCE_NAME, instanceName);
      myAgentConfiguration.setName(instanceName);
      LOG.info("Instance name and agent name are set to " + instanceName);
    } else {
      LOG.info("Unable to find azure properties file. Azure integration is disabled");
    }
  }

  private void addLocalPort(@NotNull final Element documentElement, @NotNull final String selfInstanceName){
    try {
      final XPath xPath = XPath.newInstance(String.format(
        "string(//Instances/Instance[@id='%s']/InputEndpoints/Endpoint[@name='TC Agent']/LocalPorts/LocalPortRange/@from)",
        selfInstanceName));
      final Object value = xPath.selectSingleNode(documentElement);
      try {
        final int portValue = Integer.parseInt(String.valueOf(value));
        myAgentConfiguration.setOwnPort(portValue);
        LOG.info("Own port is set to " + portValue);
      } catch (Exception ex){
        LOG.warn("Unable to set self port. Azure integration will experience problems");
      }
    } catch (JDOMException e) {
      LOG.warn("", e);
    }
  }

  private void setOwnAddress(@NotNull final Element documentElement){
    try {
      final XPath xPath = XPath.newInstance("string(//Deployment/Service/@name)");
      final Object value = xPath.selectSingleNode(documentElement);
      final String serviceAddress = value + ".cloudapp.net";
      myAgentConfiguration.addAlternativeAgentAddress(serviceAddress);
      LOG.info("Own address is set to " + serviceAddress);
    } catch (JDOMException e) {
      LOG.warn("", e);
    }
  }

  private String readFileWithSudo(){
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath("/bin/bash");
    commandLine.addParameter("-c");
    commandLine.addParameter(String.format("sudo cat %s", LINUX_PROP_FILE));
    final ExecResult execResult = SimpleCommandLineProcessRunner.runCommand(commandLine, new byte[0]);
    if (execResult.getExitCode() != 0){
      LOG.info("");
    }
    return StringUtil.trim(execResult.getStdout());
  }

}
