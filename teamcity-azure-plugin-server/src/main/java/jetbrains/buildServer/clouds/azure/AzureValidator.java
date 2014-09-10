package jetbrains.buildServer.clouds.azure;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.management.compute.ComputeManagementClient;
import com.microsoft.windowsazure.management.compute.models.HostedServiceGetResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import javax.xml.parsers.ParserConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXException;

/**
 * @author Sergey.Pak
 *         Date: 8/1/2014
 *         Time: 7:59 PM
 */
public class AzureValidator {

  private final ComputeManagementClient myClient;

  public AzureValidator(@NotNull final ComputeManagementClient client){

    myClient = client;
  }

  public void validateServiceName(String serviceName) throws IllegalArgumentException,
                                                             ServiceException,
                                                             ParserConfigurationException,
                                                             URISyntaxException,
                                                             SAXException,
                                                             IOException {
    final HostedServiceGetResponse response = myClient.getHostedServicesOperations().get(serviceName);
    final int statusCode = response.getStatusCode();
    if (statusCode != 200) throw new IllegalArgumentException("Bad response code: " + statusCode);
  }

  public void validateImageName(String serviceName, String vmName, String imageName){

  }

  public void validateVmName(String serviceName, String vmName){

  }



}
