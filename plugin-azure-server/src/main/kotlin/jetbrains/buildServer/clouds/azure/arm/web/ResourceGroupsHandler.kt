

package jetbrains.buildServer.clouds.azure.arm.web

import kotlinx.coroutines.coroutineScope
import org.jdom.Element
import javax.servlet.http.HttpServletRequest

/**
 * Handles resource groups request.
 */
internal class ResourceGroupsHandler : ResourceHandler {
    override suspend fun handle(request: HttpServletRequest, context: ResourceHandlerContext) = coroutineScope {
        val resourceGroups = context.apiConnector.getResourceGroups()

        val resourceGroupsElement = Element("resourceGroups")
        for ((name, region) in resourceGroups) {
            resourceGroupsElement.addContent(Element("resourceGroup").apply {
                setAttribute("region", region)
                text = name
            })
        }

        resourceGroupsElement
    }
}
