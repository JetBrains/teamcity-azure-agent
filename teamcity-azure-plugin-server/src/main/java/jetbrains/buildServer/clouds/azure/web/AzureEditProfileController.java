package jetbrains.buildServer.clouds.azure.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.BaseFormXmlController;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author Sergey.Pak
 *         Date: 8/6/2014
 *         Time: 3:01 PM
 */
public class AzureEditProfileController extends BaseFormXmlController {

  @NotNull private final String myJspPath;
  @NotNull private final String myHtmlPath;

  public AzureEditProfileController(@NotNull final SBuildServer server,
                                    @NotNull final PluginDescriptor pluginDescriptor,
                                    @NotNull final WebControllerManager manager) {
    super(server);
    myHtmlPath = pluginDescriptor.getPluginResourcesPath("azure-settings.html");
    myJspPath = pluginDescriptor.getPluginResourcesPath("azure-settings.jsp");
    manager.registerController(myHtmlPath, this);
  }

  @Override
  protected ModelAndView doGet(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) {
    ModelAndView mv = new ModelAndView(myJspPath);
    mv.getModel().put("refreshablePath", myHtmlPath);
    return mv;
  }

  @Override
  protected void doPost(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse, final Element element) {

  }
}
