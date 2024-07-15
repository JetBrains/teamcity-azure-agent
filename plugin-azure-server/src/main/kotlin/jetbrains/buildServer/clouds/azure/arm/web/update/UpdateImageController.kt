package jetbrains.buildServer.clouds.azure.arm.web.update

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.utils.Serialization
import jetbrains.buildServer.clouds.azure.utils.PluginPropertiesUtil
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.controllers.XmlResponseUtil
import jetbrains.buildServer.serverSide.SBuildServer
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.springframework.web.servlet.ModelAndView
import java.util.EnumSet
import java.util.stream.Collectors
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class UpdateImageController(
    server: SBuildServer,
    manager: WebControllerManager,
    private val updateProcessor: UpdateImageProcessor
) : BaseController(server) {

    init {
        manager.registerController(ImageUpdateConstants.UPDATE_IMAGE_REQUEST_PATH, this)
    }

    companion object {
        private val LOG = Logger.getInstance(UpdateImageController::class.java.getName())
        private val requiredProps = EnumSet.allOf(ImageUpdateProperties::class.java)
    }

    override fun doHandle(req: HttpServletRequest, resp: HttpServletResponse): ModelAndView? {
        if (!isPost(req)) {
            processExceptionalState("${req.method} not supported for updating images data", resp, null)
            return null
        }

        val props = parseRequestData(req)
        val missingProps = checkBasicProperties(props)

        if (missingProps.isNotEmpty()) {
            processExceptionalState("Missing required properties for the images data update: $missingProps", resp, null)
            return null
        }

        try {
            val result = updateProcessor.processImageUpdate(props)
            Serialization.writeResponse(result.asElement(XmlResponseUtil.newXmlResponse()), resp)
        } catch (e: Exception) {
            processExceptionalState("Unexpected exception during the images data update", resp, e)
        }

        return null
    }

    private fun processExceptionalState(message: String, resp: HttpServletResponse, ex: Throwable?) {
        LOG.error(message, ex)
        completeExceptionally(ActionError("Update Image", message), resp)
    }

    private fun completeExceptionally(error: ActionError, resp: HttpServletResponse) {
        val res = UpdateImageResult(null)
        res.registerErrors(error)

        Serialization.writeResponse(res.asElement(XmlResponseUtil.newXmlResponse()), resp)
    }

    private fun parseRequestData(req: HttpServletRequest): BasePropertiesBean {
        val props = BasePropertiesBean(null)
        PluginPropertiesUtil.bindPropertiesFromRequest(req, props, true)
        return props
    }

    private fun checkBasicProperties(props: BasePropertiesBean): MutableList<String> {
        val missingProps = requiredProps.stream()
            .filter { props.properties[it.propertyName] == null }
            .map { it.propertyName }
            .collect(Collectors.toList())

        return missingProps
    }
}
