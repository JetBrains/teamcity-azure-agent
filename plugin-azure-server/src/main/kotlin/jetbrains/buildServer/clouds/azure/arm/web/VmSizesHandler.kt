package jetbrains.buildServer.clouds.azure.arm.web

import kotlinx.coroutines.coroutineScope
import org.jdom.Element
import javax.servlet.http.HttpServletRequest

/**
 * Handles vm sizes request.
 */
internal class VmSizesHandler : ResourceHandler {
    override suspend fun handle(request: HttpServletRequest, context: ResourceHandlerContext) = coroutineScope {
        val region = request.getParameter("region")
        val sizes = context.apiConnector.getVmSizes(region)

        val sizesElement = Element("vmSizes")
        for (size in sizes) {
            sizesElement.addContent(Element("vmSize").apply {
                text = size
            })
        }

        sizesElement
    }
}
