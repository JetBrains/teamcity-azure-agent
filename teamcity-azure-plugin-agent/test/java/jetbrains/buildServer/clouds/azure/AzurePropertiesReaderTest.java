package jetbrains.buildServer.clouds.azure;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import jetbrains.buildServer.util.FileUtil;
import org.apache.xerces.impl.dv.util.Base64;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;

/**
 * @author Sergey.Pak
 *         Date: 8/28/2014
 *         Time: 9:22 PM
 */

@Test
public class AzurePropertiesReaderTest {

  public static void main(String[] args) throws IOException, JDOMException {
    final String xmlData = FileUtil.readText(new File("teamcity-azure-plugin-agent/test/resources/SharedConfig.xml"));
    final Element element = FileUtil.parseDocument(new StringReader(xmlData), false);
    try {
      final XPath xPath = XPath.newInstance(String.format(
        "string(//Instances/Instance[@id='%s']/InputEndpoints/Endpoint[@name='%s']/@loadBalancedPublicAddress)",
        "paksvvm-53eb78da", AzurePropertiesNames.ENDPOINT_NAME));
      final Object value = xPath.selectSingleNode(element);
      final String loadBalancedAddress = String.valueOf(value);
      final String externalIp = loadBalancedAddress.indexOf(":") == -1 ? loadBalancedAddress : loadBalancedAddress.substring(0, loadBalancedAddress.indexOf(":"));
      System.out.println(externalIp);
      /*
      final XPath xPath = XPath.newInstance("string(//wa:LinuxProvisioningConfigurationSet/wa:CustomData)");
      final Object value = xPath.selectSingleNode(element);
      System.out.println(new String(Base64.decode(String.valueOf(value))));
      */
    } catch (JDOMException e) {
      e.printStackTrace();
    }
  }

  public void testCustomData() throws JDOMException, IOException {
    final String xmlData = FileUtil.readText(new File("teamcity-azure-plugin-agent/test/resources/ovf-env.xml"));
    final Element element = FileUtil.parseDocument(new ByteArrayInputStream(xmlData.getBytes()), false);
    XPath xPath = XPath.newInstance("string(//wa:LinuxProvisioningConfigurationSet/wa:CustomData)");
    //final Namespace wa = Namespace.getNamespace("wa", "http://schemas.microsoft.com/windowsazure");
    //xPath.addNamespace(wa);
    final Object value = xPath.selectSingleNode(element);
    System.out.println(new String(Base64.decode(String.valueOf(value))));
  }
}
