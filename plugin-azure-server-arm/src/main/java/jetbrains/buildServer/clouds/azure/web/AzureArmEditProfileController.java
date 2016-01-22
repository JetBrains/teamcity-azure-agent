/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.web;

import com.intellij.openapi.diagnostic.Logger;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author Sergey.Pak
 *         Date: 8/6/2014
 *         Time: 3:01 PM
 */
public class AzureArmEditProfileController extends BaseFormXmlController {

  private static final Logger LOG = Logger.getInstance(AzureArmEditProfileController.class.getName());

  @NotNull
  private final String myJspPath;
  @NotNull
  private final String myHtmlPath;
  @NotNull
  private final PluginDescriptor myPluginDescriptor;

  public AzureArmEditProfileController(@NotNull final SBuildServer server,
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
    final String subscriptionId = props.get(AzureArmWebConstants.SUBSCRIPTION_ID);
    final String certificate = props.get("secure:" + AzureArmWebConstants.MANAGEMENT_CERTIFICATE);
  }
}
