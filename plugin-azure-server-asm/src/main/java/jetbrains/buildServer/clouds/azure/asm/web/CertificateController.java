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

package jetbrains.buildServer.clouds.azure.asm.web;

import jetbrains.buildServer.controllers.ActionMessages;
import jetbrains.buildServer.controllers.MultipartFormController;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * Handles management certificate uploads.
 */
public class CertificateController extends MultipartFormController {

    public CertificateController(@NotNull final SBuildServer server,
                                 @NotNull final PluginDescriptor pluginDescriptor,
                                 @NotNull final WebControllerManager manager) {
        super(server);
        manager.registerController(pluginDescriptor.getPluginResourcesPath("uploadManagementCertificate.html"), this);
    }

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

    private static ModelAndView error(@NotNull ModelAndView modelAndView, @NotNull String error) {
        modelAndView.getModel().put("error", error);
        return modelAndView;
    }
}
