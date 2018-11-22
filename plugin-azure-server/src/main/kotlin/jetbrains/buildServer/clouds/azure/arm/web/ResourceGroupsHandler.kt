package jetbrains.buildServer.clouds.azure.arm.web

import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Element
import javax.servlet.http.HttpServletRequest

/**
 * Handles resource groups request.
 */
internal class ResourceGroupsHandler : AzureResourceHandler() {
    override suspend fun handle(connector: AzureApiConnector, request: HttpServletRequest) = coroutineScope {
        val resourceGroups = connector.getResourceGroups()

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
