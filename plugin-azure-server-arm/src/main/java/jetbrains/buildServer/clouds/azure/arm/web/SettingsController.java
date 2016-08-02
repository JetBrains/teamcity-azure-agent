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

package jetbrains.buildServer.clouds.azure.arm.web;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.controllers.ActionErrors;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.XmlResponseUtil;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jdeferred.AlwaysCallback;
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
import java.util.TreeMap;

/**
 * ARM settings controller.
 */
public class SettingsController extends BaseController {

    private static final Logger LOG = Logger.getInstance(SettingsController.class.getName());
    private static final Map<String, ResourceHandler> HANDLERS = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final DefaultDeferredManager myManager;

    static {
        HANDLERS.put("vmSizes", new VmSizesHandler());
        HANDLERS.put("osType", new OsTypeHandler());
        HANDLERS.put("subscriptions", new SubscriptionsHandler());
        HANDLERS.put("networks", new NetworksHandler());
        HANDLERS.put("locations", new LocationsHandler());
    }

    @NotNull
    private final String myJspPath;
    @NotNull
    private final String myHtmlPath;
    @NotNull
    private final PluginDescriptor myPluginDescriptor;

    public SettingsController(@NotNull final SBuildServer server,
                              @NotNull final PluginDescriptor pluginDescriptor,
                              @NotNull final WebControllerManager manager) {
        super(server);

        myPluginDescriptor = pluginDescriptor;
        myHtmlPath = pluginDescriptor.getPluginResourcesPath("settings.html");
        myJspPath = pluginDescriptor.getPluginResourcesPath("settings.jsp");

        manager.registerController(myHtmlPath, this);
        myManager = new DefaultDeferredManager();
    }

    @Nullable
    @Override
    protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception {
        request.setAttribute("org.apache.catalina.ASYNC_SUPPORTED", true);

        try {
            if (isPost(request)) {
                doPost(request, response);
                return null;
            }

            return doGet();
        } catch (Throwable e){
            LOG.error("Failed to handle request: " + e.getMessage(), e);
            throw e;
        }
    }

    private ModelAndView doGet() {
        ModelAndView mv = new ModelAndView(myJspPath);
        mv.getModel().put("basePath", myHtmlPath);
        mv.getModel().put("resPath", myPluginDescriptor.getPluginResourcesPath());
        return mv;
    }

    private void doPost(@NotNull final HttpServletRequest request,
                        @NotNull final HttpServletResponse response) {
        final Element xmlResponse = XmlResponseUtil.newXmlResponse();
        final ActionErrors errors = new ActionErrors();
        final String[] resources = request.getParameterValues("resource");
        final List<Promise<Content, Throwable, Void>> promises = new ArrayList<>(resources.length);

        for (final String resource : resources) {
            final ResourceHandler handler = HANDLERS.get(resource);
            if (handler == null) continue;

            try {
                final Promise<Content, Throwable, Void> promise = handler.handle(request).fail(new FailCallback<Throwable>() {
                    @Override
                    public void onFail(Throwable result) {
                        LOG.debug(result);
                        errors.addError(resource, result.getMessage());
                    }
                });
                promises.add(promise);
            } catch (Throwable t) {
                LOG.debug(t);
                errors.addError(resource, t.getMessage());
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
