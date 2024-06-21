

package jetbrains.buildServer.clouds.azure.arm.web

import kotlinx.coroutines.coroutineScope
import org.jdom.Element
import javax.servlet.http.HttpServletRequest

/**
 * Handles region request.
 */
internal class RegionsHandler : ResourceHandler {
    override suspend fun handle(request: HttpServletRequest, context: ResourceHandlerContext) = coroutineScope {
        val regions = context.apiConnector.getRegions()
        val regionsElement = Element("regions")

        for ((id, displayName) in regions) {
            regionsElement.addContent(Element("region").apply {
                setAttribute("id", id)
                text = displayName
            })
        }

        regionsElement
    }
}
