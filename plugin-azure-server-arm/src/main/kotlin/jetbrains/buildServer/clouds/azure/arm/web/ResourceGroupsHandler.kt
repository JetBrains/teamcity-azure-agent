package jetbrains.buildServer.clouds.azure.arm.web

import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import org.jdom.Element
import javax.servlet.http.HttpServletRequest

/**
 * Handles resource groups request.
 */
internal class ResourceGroupsHandler : AzureResourceHandler() {
    override fun handle(connector: AzureApiConnector, request: HttpServletRequest) = async(CommonPool) {
        val resourceGroups = connector.getResourceGroupsAsync().await()

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
