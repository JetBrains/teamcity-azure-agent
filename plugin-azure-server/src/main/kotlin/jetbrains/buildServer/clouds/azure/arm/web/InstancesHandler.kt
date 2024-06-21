package jetbrains.buildServer.clouds.azure.arm.web

import kotlinx.coroutines.coroutineScope
import org.jdom.Element
import javax.servlet.http.HttpServletRequest

/**
 * Handles instances request.
 */
internal class InstancesHandler : ResourceHandler {
    override suspend fun handle(request: HttpServletRequest, context: ResourceHandlerContext) = coroutineScope {
        val instances = context.apiConnector.getInstances()

        val instancesElement = Element("instances")
        for ((id, description, osType) in instances) {
            instancesElement.addContent(Element("instance").apply {
                setAttribute("id", id)
                setAttribute("osType", osType)
                text = description
            })
        }

        instancesElement
    }
}
