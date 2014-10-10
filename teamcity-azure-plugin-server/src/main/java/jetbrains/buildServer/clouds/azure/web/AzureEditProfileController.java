package jetbrains.buildServer.clouds.azure.web;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.clouds.azure.connector.AzureApiConnector;
import jetbrains.buildServer.controllers.*;
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author Sergey.Pak
 *         Date: 8/6/2014
 *         Time: 3:01 PM
 */
public class AzureEditProfileController extends BaseFormXmlController {

  private static final Logger LOG = Logger.getInstance(AzureEditProfileController.class.getName());

  @NotNull private final String myJspPath;
  @NotNull private final String myHtmlPath;
  @NotNull private final PluginDescriptor myPluginDescriptor;

  public AzureEditProfileController(@NotNull final SBuildServer server,
                                    @NotNull final PluginDescriptor pluginDescriptor,
                                    @NotNull final WebControllerManager manager) {
    super(server);
    myPluginDescriptor = pluginDescriptor;
    myHtmlPath = pluginDescriptor.getPluginResourcesPath("azure-settings.html");
    myJspPath = pluginDescriptor.getPluginResourcesPath("azure-settings.jsp");

    manager.registerController(myHtmlPath, this);
    manager.registerController(pluginDescriptor.getPluginResourcesPath("uploadManagementCertificate.html"), new MultipartFormController() {
      @Override
      protected ModelAndView doPost(final HttpServletRequest request, final HttpServletResponse response) {
        final ModelAndView modelAndView = new ModelAndView("/_fileUploadResponse.jsp");
        final String fileName = request.getParameter("fileName");
        boolean exists;
        try {
          final MultipartFile file = getMultipartFileOrFail(request, "file:fileToUpload");
          if (file == null) {
            return error(modelAndView, "No file set");
          }
          final File pluginDataDirectory = FileUtil.createDir(new File(""));
          final File destinationFile = new File(pluginDataDirectory, fileName);
          exists = destinationFile.exists();
          file.transferTo(destinationFile);
        } catch (IOException e) {
          return error(modelAndView, e.getMessage());
        } catch (IllegalStateException e) {
          return error(modelAndView, e.getMessage());
        }
        if (exists) {
          Loggers.SERVER.info("File " + fileName + " is overwritten");
          ActionMessages.getOrCreateMessages(request).addMessage("mavenSettingsUploaded", "Maven settings file " + fileName + " was updated");
        } else {
          ActionMessages.getOrCreateMessages(request).addMessage("mavenSettingsUploaded", "Maven settings file " + fileName + " was uploaded");
        }
        return modelAndView;

      }
      protected ModelAndView error(@NotNull ModelAndView modelAndView, @NotNull String error) {
        modelAndView.getModel().put("error", error);
        return modelAndView;
      }

    });
  }

  @Override
  protected ModelAndView doGet(final HttpServletRequest request, final HttpServletResponse response) {
    ModelAndView mv = new ModelAndView(myJspPath);
    mv.getModel().put("refreshablePath", myHtmlPath);
    mv.getModel().put("resPath", myPluginDescriptor.getPluginResourcesPath());
    return mv;
  }

  @Override
  protected void doPost(final HttpServletRequest request, final HttpServletResponse response, final Element xmlResponse) {
    ActionErrors errors = new ActionErrors();

    BasePropertiesBean propsBean = new BasePropertiesBean(null);
    PluginPropertiesUtil.bindPropertiesFromRequest(request, propsBean, true);

    final Map<String, String> props = propsBean.getProperties();
    final String subscriptionId = props.get(AzureWebConstants.SUBSCRIPTION_ID);
    final String certificate = props.get(AzureWebConstants.MANAGEMENT_CERTIFICATE);

    AzureApiConnector apiConnector = new AzureApiConnector(subscriptionId, certificate);

    try {
      final List<String> servicesList = apiConnector.listServicesNames();
      Element services = new Element("Services");
      for (String serviceName : servicesList) {
        final Element service = new Element("Service");
        service.setAttribute("name", serviceName);

        try {
          final Map<String, String> instances = apiConnector.listServiceInstances(serviceName);
          service.addContent(getServiceInstances(instances));
        } catch (Exception ex){
          service.setAttribute("inactive", "true");
          service.setAttribute("errorMsg", ex.getMessage());
        }
        services.addContent(service);
      }
      xmlResponse.addContent(services);
      xmlResponse.addContent(getImages(apiConnector.listImages()));
      xmlResponse.addContent(getVmSizes(apiConnector.listVmSizes()));


    } catch (Exception e) {
      errors.addError("Error fetching details", e.toString());
      writeErrors(xmlResponse, errors);
      LOG.warn("An error during fetching options: " + e.toString());
      LOG.debug("An error during fetching options", e);
    }
  }

  private Element getImages(final Map<String, Pair<Boolean, String>> imagesMap) {
    Element images = new Element("Images");
    for (String imageName : imagesMap.keySet()) {
      final Element imageElem = new Element("Image");
      imageElem.setAttribute("name", imageName);
      final Pair<Boolean, String> pair = imagesMap.get(imageName);
      imageElem.setAttribute("generalized", String.valueOf(pair.getFirst()));
      imageElem.setAttribute("osType", pair.getSecond());
      images.addContent(imageElem);
    }
    return images;
  }

  private Element getVmSizes(final Map<String, String> vmSizesMap) {
    Element vmSizes = new Element("VmSizes");
    for (String size : vmSizesMap.keySet()) {
      final Element vmSize = new Element("VmSize");
      vmSize.setAttribute("name", size);
      vmSize.setAttribute("label", vmSizesMap.get(size));
      vmSizes.addContent(vmSize);
    }
    return vmSizes;
  }

  private List<Element> getServiceInstances(final Map<String, String> instancesMap) {
    List<Element> elements = new ArrayList<Element>();
    for (String instanceName : instancesMap.keySet()) {
      final Element instanceElem = new Element("Instance");
      instanceElem.setAttribute("name", instanceName);
      instanceElem.setAttribute("osType", instancesMap.get(instanceName));
      elements.add(instanceElem);
    }
    return elements;
  }
}
