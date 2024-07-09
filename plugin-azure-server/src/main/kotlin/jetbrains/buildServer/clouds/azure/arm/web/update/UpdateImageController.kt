package jetbrains.buildServer.clouds.azure.arm.web.update

import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.utils.PluginPropertiesUtil
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.serverSide.SBuildServer
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.apache.commons.lang3.StringUtils
import org.springframework.web.servlet.ModelAndView
import java.util.EnumSet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class UpdateImageController(
    server: SBuildServer,
    manager: WebControllerManager,
    private val myImageHandler: UpdateImageProcessor
) : BaseController(server) {

    init {
        manager.registerController(AzureConstants.UPDATE_IMAGE_REQUEST_PATH, this)
    }

    companion object {
        private val requiredProps = EnumSet.allOf(RequiredBasicUpdateProperties::class.java)
    }

    override fun doHandle(req: HttpServletRequest, resp: HttpServletResponse): ModelAndView? {
        if (!isPost(req)) {
            return null
        }

        val props = parseRequestData(req)
        checkBasicProperties(props)

        myImageHandler.updateImage(props)

        return null
    }

    private fun parseRequestData(req: HttpServletRequest): BasePropertiesBean {
        val props = BasePropertiesBean(null)
        PluginPropertiesUtil.bindPropertiesFromRequest(req, props, true)
        return props
    }

    private fun checkBasicProperties(props: BasePropertiesBean) {
        requiredProps.stream()
            .filter { StringUtils.isBlank(props.properties[it.propertyName]) }
            .findAny()
            .ifPresent { throw IllegalArgumentException("${it.propertyName} is null or blank") }
    }
}
