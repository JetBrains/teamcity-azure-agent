package jetbrains.buildServer.clouds.azure.arm.web.update

import jetbrains.buildServer.clouds.azure.arm.utils.Serialization
import jetbrains.buildServer.clouds.azure.utils.PluginPropertiesUtil
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.controllers.XmlResponseUtil
import jetbrains.buildServer.serverSide.SBuildServer
import jetbrains.buildServer.serverSide.crypt.RSACipher
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.apache.commons.lang3.StringUtils
import org.jdom.Element
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
        private val requiredProps = EnumSet.allOf(ImageUpdateProperties::class.java)
    }

    override fun doHandle(req: HttpServletRequest, resp: HttpServletResponse): ModelAndView? {
        if (!isPost(req)) {
            return null
        }

        val props = parseRequestData(req)
        checkBasicProperties(props)

        val data = updateProcessor.processImageUpdate(props)
        val xmlResponse = assembleResponse(data)
        Serialization.writeResponse(xmlResponse, resp)

        return null
    }

    private fun assembleResponse(data: String): Element {
        val encrypted = RSACipher.encryptDataForWeb(data)
        val xmlResponse = XmlResponseUtil.newXmlResponse()
        xmlResponse.addContent(Element("passwords_data")
            .apply { text = encrypted }
        )
        return xmlResponse
    }

    private fun parseRequestData(req: HttpServletRequest): BasePropertiesBean {
        val props = BasePropertiesBean(null)
        PluginPropertiesUtil.bindPropertiesFromRequest(req, props, true)
        return props
    }

    private fun checkBasicProperties(props: BasePropertiesBean) {
        val missingProps = requiredProps.stream()
            .filter { StringUtils.isEmpty(props.properties[it.propertyName]) }
            .map { it.propertyName }
            .collect(Collectors.toList())

        if (missingProps.isNotEmpty()) {
            throw IllegalArgumentException("Missing required properties: $missingProps")
        }
    }
}
