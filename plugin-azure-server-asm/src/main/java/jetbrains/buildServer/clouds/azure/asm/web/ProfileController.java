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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.azure.asm.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.azure.asm.errors.InvalidCertificateException;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.controllers.XmlResponseUtil;
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdeferred.AlwaysCallback;
import org.jdeferred.DeferredManager;
import org.jdeferred.FailCallback;
import org.jdeferred.Promise;
import org.jdeferred.impl.DefaultDeferredManager;
import org.jdeferred.multiple.MultipleResults;
import org.jdeferred.multiple.OneReject;
import org.jdeferred.multiple.OneResult;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.AsyncContext;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Sergey.Pak
 *         Date: 8/6/2014
 *         Time: 3:01 PM
 */
public class ProfileController extends BaseController {

    private static final Logger LOG = Logger.getInstance(ProfileController.class.getName());
    private final String myJspPath;
    private final String myHtmlPath;
    private final String myResourcePath;
    private final List<ResourceHandler> myHandlers = new ArrayList<>();
    private final DeferredManager myManager;

    public ProfileController(@NotNull final SBuildServer server,
                             @NotNull final PluginDescriptor pluginDescriptor,
                             @NotNull final WebControllerManager manager) {
        super(server);

        myResourcePath = pluginDescriptor.getPluginResourcesPath();
        myHtmlPath = pluginDescriptor.getPluginResourcesPath("azure-settings.html");
        myJspPath = pluginDescriptor.getPluginResourcesPath("azure-settings.jsp");
        myManager = new DefaultDeferredManager();
        myHandlers.add(new VmSizesHandler());
        myHandlers.add(new ServicesHandler());
        myHandlers.add(new ImagesHandler());
        myHandlers.add(new NetworksHandler());

        manager.registerController(myHtmlPath, this);
    }

    @Nullable
    @Override
    protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
        request.setAttribute("org.apache.catalina.ASYNC_SUPPORTED", true);

        if (isPost(request)) {
            doPost(request, response);
            return null;
        }

        return doGet();
    }

    private ModelAndView doGet() {
        ModelAndView mv = new ModelAndView(myJspPath);
        mv.getModel().put("refreshablePath", myHtmlPath);
        mv.getModel().put("resPath", myResourcePath);
        return mv;
    }

    private void doPost(@NotNull final HttpServletRequest request,
                        @NotNull final HttpServletResponse response) {
        final Element xmlResponse = XmlResponseUtil.newXmlResponse();
        final ActionErrors errors = new ActionErrors();
        final BasePropertiesBean propsBean = new BasePropertiesBean(null);
        PluginPropertiesUtil.bindPropertiesFromRequest(request, propsBean, true);

        final Map<String, String> props = propsBean.getProperties();
        final String subscriptionId = props.get(AzureWebConstants.SUBSCRIPTION_ID);
        final String certificate = props.get("secure:" + AzureWebConstants.MANAGEMENT_CERTIFICATE);

        final AzureApiConnector apiConnector;
        try {
            apiConnector = new AzureApiConnector(subscriptionId, certificate);
            apiConnector.test();
        } catch (InvalidCertificateException ex) {
            errors.addError("certificateError", "Invalid Management certificate. Please enter the Management Certificate exactly as it is presented in the subscription file.");
            errors.serialize(xmlResponse);
            LOG.warnAndDebugDetails("An error during initializing connection: " + ex.getMessage(), ex);
            return;
        } catch (CheckedCloudException ex) {
            errors.addError("pingError", "Error connecting to Microsoft Azure. Please check that your Management Certificate and Subscription ID are valid.");
            errors.serialize(xmlResponse);
            LOG.warnAndDebugDetails("An error during initializing connection: " + ex.getMessage(), ex);
            return;
        }

        final List<Promise<Content, Throwable, Void>> promises = new ArrayList<>(myHandlers.size());
        for (final ResourceHandler handler : myHandlers) {
            try {
                final Promise<Content, Throwable, Void> promise = handler.handle(apiConnector).fail(new FailCallback<Throwable>() {
                    @Override
                    public void onFail(Throwable result) {
                        LOG.debug(result);
                        errors.addError(StringUtil.EMPTY, result.getMessage());
                    }
                });
                promises.add(promise);
            } catch (Throwable t) {
                LOG.debug(t);
                errors.addError(StringUtil.EMPTY, t.getMessage());
            }
        }

        if (promises.size() == 0) {
            if (errors.hasErrors()) {
                errors.serialize(xmlResponse);
            }

            writeResponse(xmlResponse, response);
            return;
        }

        final AsyncContext context = request.startAsync(request, response);
        myManager.when(promises.toArray(new Promise[]{})).always(new AlwaysCallback<MultipleResults, OneReject>() {
            @Override
            public void onAlways(Promise.State state, MultipleResults resolved, OneReject rejected) {
                if (errors.hasErrors()) {
                    errors.serialize(xmlResponse);
                } else {
                    for (OneResult oneResult : resolved) {
                        xmlResponse.addContent((Content) oneResult.getResult());
                    }
                }

                writeResponse(xmlResponse, context.getResponse());
                context.complete();
            }
        });
    }

    private static void writeResponse(final Element xmlResponse, final ServletResponse response) {
        try {
            XmlResponseUtil.writeXmlResponse(xmlResponse, (HttpServletResponse) response);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
