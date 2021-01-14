/*
 * Copyright 2000-2021 JetBrains s.r.o.
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
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.openapi.WebControllerManager
import jetbrains.buildServer.web.util.WebUtil
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
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
                         manager: WebControllerManager,
                         agentPoolManager: AgentPoolManager) : BaseController(server) {

    private val myHandlers = TreeMap<String, ResourceHandler>(String.CASE_INSENSITIVE_ORDER)
    private val myJspPath: String = myPluginDescriptor.getPluginResourcesPath("settings.jsp")
    private val myHtmlPath: String = myPluginDescriptor.getPluginResourcesPath("settings.html")

    init {
        manager.registerController(myHtmlPath, this)
        myHandlers["resourceGroups"] = ResourceGroupsHandler()
        myHandlers["instances"] = InstancesHandler()
        myHandlers["images"] = ImagesHandler()
        myHandlers["vmSizes"] = VmSizesHandler()
        myHandlers["osType"] = OsTypeHandler()
        myHandlers["subscriptions"] = SubscriptionsHandler()
        myHandlers["networks"] = NetworksHandler()
        myHandlers["regions"] = RegionsHandler()
        myHandlers["agentPools"] = AgentPoolHandler(agentPoolManager)
        myHandlers["storageAccounts"] = StorageAccountsHandler()
    }

    @Throws(Exception::class)
    override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
        request.setAttribute("org.apache.catalina.ASYNC_SUPPORTED", true)

        try {
            if (isPost(request)) {
                doPost(request, response)
                return null
            }

            return doGet(request)
        } catch (e: Throwable) {
            LOG.infoAndDebugDetails("Failed to handle request: " + e.message, e)
            throw e
        }
    }

    private fun doGet(request: HttpServletRequest): ModelAndView {
        val mv = ModelAndView(myJspPath)
        mv.model["basePath"] = myHtmlPath
        mv.model["resPath"] = myPluginDescriptor.pluginResourcesPath
        mv.model["projectId"] = request.getParameter("projectId")
        mv.model["contextPath"] = WebUtil.getRootUrl(request)
        return mv
    }

    private fun doPost(request: HttpServletRequest,
                       response: HttpServletResponse) = runBlocking {
        val xmlResponse = XmlResponseUtil.newXmlResponse()
        val errors = ActionErrors()
        val resources = request.getParameterValues("resource")
        val context = request.startAsync(request, response)

        resources.filterNotNull().map { resource ->
            myHandlers[resource]?.let { handler ->
                return@map async {
                    try {
                        xmlResponse.addContent(handler.handle(request))
                    } catch (e: Throwable) {
                        LOG.infoAndDebugDetails("Failed to process $resource request", e)
                        errors.addError(resource, e.message)
                    }
                }
            }
        }.filterNotNull().awaitAll()

        if (errors.hasErrors()) {
            errors.serialize(xmlResponse)
        }

        writeResponse(xmlResponse, context.response)
        context.complete()
    }

    companion object {
        private val LOG = Logger.getInstance(SettingsController::class.java.name)

        private fun writeResponse(xmlResponse: Element, response: ServletResponse) {
            try {
                XmlResponseUtil.writeXmlResponse(xmlResponse, response as HttpServletResponse)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }
}
