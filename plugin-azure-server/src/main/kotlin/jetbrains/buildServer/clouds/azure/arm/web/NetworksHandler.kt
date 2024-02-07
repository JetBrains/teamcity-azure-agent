

package jetbrains.buildServer.clouds.azure.arm.web

import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Content
import org.jdom.Element
import javax.servlet.http.HttpServletRequest

/**
 * Handles networks request.
 */
internal class NetworksHandler : ResourceHandler {
    override suspend fun handle(request: HttpServletRequest, context: ResourceHandlerContext) = coroutineScope {
        val region = request.getParameter("region")
        val networks = context.apiConnector.getNetworks(region)
        val networksElement = Element("networks")

        for ((name, subnets) in networks) {
            val networkElement = Element("network").apply {
                setAttribute("id", name)
            }

            for (subnet in subnets) {
                networkElement.addContent(Element("subnet").apply {
                    text = subnet
                })
            }

            networksElement.addContent(networkElement)
        }

        networksElement
    }
}
