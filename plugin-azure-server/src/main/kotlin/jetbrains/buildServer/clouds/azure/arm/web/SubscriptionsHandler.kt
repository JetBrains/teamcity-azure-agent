package jetbrains.buildServer.clouds.azure.arm.web

import kotlinx.coroutines.coroutineScope
import org.jdom.Element
import javax.servlet.http.HttpServletRequest

/**
 * Handles subscriptions request.
 */
internal class SubscriptionsHandler : ResourceHandler {
    override suspend fun handle(request: HttpServletRequest, context: ResourceHandlerContext) = coroutineScope {
        val subscriptions = context.apiConnector.getSubscriptions()
        val subscriptionsElement = Element("subscriptions")

        for ((id, displayName) in subscriptions) {
            subscriptionsElement.addContent(Element("subscription").apply {
                setAttribute("id", id)
                text = displayName
            })
        }

        subscriptionsElement
    }
}
