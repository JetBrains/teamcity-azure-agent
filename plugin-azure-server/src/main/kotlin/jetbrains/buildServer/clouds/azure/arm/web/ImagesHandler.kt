

package jetbrains.buildServer.clouds.azure.arm.web

import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnectorFactory
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureRequestThrottlerCache
import kotlinx.coroutines.coroutineScope
import org.jdom.Content
import org.jdom.Element
import javax.servlet.http.HttpServletRequest

/**
 * Handles images request.
 */
internal class ImagesHandler: ResourceHandler {
    override suspend fun handle(request: HttpServletRequest, context: ResourceHandlerContext) = coroutineScope {
        val region = request.getParameter("region")
        val images = context.apiConnector.getImages(region)

        val imagesElement = Element("images")
        for ((id, props) in images) {
            imagesElement.addContent(Element("image").apply {
                setAttribute("id", id)
                setAttribute("osType", props[1])
                setAttribute("isGalleryImage", props[2])
                text = props[0]
            })
        }

        imagesElement
    }
}
