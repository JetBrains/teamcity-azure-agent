package jetbrains.buildServer.clouds.azure.arm.utils

import jetbrains.buildServer.controllers.XmlResponseUtil
import org.jdom.Element
import java.io.IOException
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletResponse

class Serialization {
    companion object {
        fun writeResponse(xmlResponse: Element, response: ServletResponse) {
            try {
                XmlResponseUtil.writeXmlResponse(xmlResponse, response as HttpServletResponse)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }
}
