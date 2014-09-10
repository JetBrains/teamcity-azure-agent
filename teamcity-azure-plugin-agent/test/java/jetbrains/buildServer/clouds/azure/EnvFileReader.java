package jetbrains.buildServer.clouds.azure;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import jetbrains.buildServer.util.FileUtil;
import org.jdom.Element;
import org.jdom.JDOMException;

/**
 * @author Sergey.Pak
 *         Date: 8/28/2014
 *         Time: 9:22 PM
 */

public class EnvFileReader {

  public static void main(String[] args) throws IOException, JDOMException {
    final String xmlData = FileUtil.readText(new File("D:\\Projects\\TeamCity\\Plugins\\teamcity-azure-plugin\\teamcity-azure-plugin-agent\\test\\resources\\SharedConfig.xml"));
    final Element element = FileUtil.parseDocument(new StringReader(xmlData),false);
    System.out.println(element.getChild("Deployment").getValue());
  }
}
