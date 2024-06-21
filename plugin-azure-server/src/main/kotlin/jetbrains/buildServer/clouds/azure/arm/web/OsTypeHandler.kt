

package jetbrains.buildServer.clouds.azure.arm.web

import kotlinx.coroutines.coroutineScope
import org.jdom.Element
import javax.servlet.http.HttpServletRequest

/**
 * Handles storage blob request.
 */
internal class OsTypeHandler : ResourceHandler {
    override suspend fun handle(request: HttpServletRequest, context: ResourceHandlerContext) = coroutineScope {
        val imageUrl = request.getParameter("imageUrl")
        val region = request.getParameter("region")
        val osType = context.apiConnector.getVhdOsType(imageUrl, region)

        Element("osType").apply {
            text = osType
        }
    }
}
