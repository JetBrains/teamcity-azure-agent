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

package jetbrains.buildServer.clouds.azure.arm.web

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.controllers.ActionErrors
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.controllers.XmlResponseUtil
import jetbrains.buildServer.serverSide.SBuildServer
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.runBlocking
import org.jdom.Content
import org.jdom.Element
import org.springframework.web.servlet.ModelAndView
import java.io.IOException
import java.util.*
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * ARM settings controller.
 */
class SettingsController(server: SBuildServer,
                         private val myPluginDescriptor: PluginDescriptor,
                         manager: WebControllerManager) : BaseController(server) {

    private val myJspPath: String = myPluginDescriptor.getPluginResourcesPath("settings.jsp")
    private val myHtmlPath: String = myPluginDescriptor.getPluginResourcesPath("settings.html")

    init {
        manager.registerController(myHtmlPath, this)
    }

    @Throws(Exception::class)
    override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
        request.setAttribute("org.apache.catalina.ASYNC_SUPPORTED", true)

        try {
            if (isPost(request)) {
                doPost(request, response)
                return null
            }

            return doGet()
        } catch (e: Throwable) {
            LOG.error("Failed to handle request: " + e.message, e)
            throw e
        }

    }

    private fun doGet(): ModelAndView {
        val mv = ModelAndView(myJspPath)
        mv.model.put("basePath", myHtmlPath)
        mv.model.put("resPath", myPluginDescriptor.pluginResourcesPath)
        return mv
    }

    private fun doPost(request: HttpServletRequest,
                       response: HttpServletResponse) = runBlocking {
        val xmlResponse = XmlResponseUtil.newXmlResponse()
        val errors = ActionErrors()
        val resources = request.getParameterValues("resource")
        val promises = hashMapOf<String, Deferred<Content>>()

        resources.filterNotNull()
                .forEach { resource ->
                    HANDLERS[resource]?.let {
                        promises += resource to it.handle(request)
                    }
                }

        val context = request.startAsync(request, response)
        promises.keys.forEach { resource ->
            try {
                xmlResponse.addContent(promises[resource]?.await())
            } catch (e: Throwable) {
                LOG.debug(e)
                errors.addError(resource, e.message)
            }
        }

        if (errors.hasErrors()) {
            errors.serialize(xmlResponse)
        }

        writeResponse(xmlResponse, context.response)
        context.complete()
    }

    companion object {
        private val LOG = Logger.getInstance(SettingsController::class.java.name)
        private val HANDLERS = TreeMap<String, ResourceHandler>(String.CASE_INSENSITIVE_ORDER)

        init {
            HANDLERS.put("vmSizes", VmSizesHandler())
            HANDLERS.put("osType", OsTypeHandler())
            HANDLERS.put("subscriptions", SubscriptionsHandler())
            HANDLERS.put("networks", NetworksHandler())
            HANDLERS.put("locations", LocationsHandler())
        }

        private fun writeResponse(xmlResponse: Element, response: ServletResponse) {
            try {
                XmlResponseUtil.writeXmlResponse(xmlResponse, response as HttpServletResponse)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }
}
