

package jetbrains.buildServer.clouds.azure.arm.web

import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Content
import org.jdom.Element
import javax.servlet.http.HttpServletRequest

/**
 * Handles storage accounts request.
 */
internal class StorageAccountsHandler : ResourceHandler {
    override suspend fun handle(request: HttpServletRequest, context: ResourceHandlerContext) = coroutineScope {
        val region = request.getParameter("region")
        val accounts = context.apiConnector.getStorageAccounts(region)

        val storagesElement = Element("storageAccounts")
        for (account in accounts) {
            storagesElement.addContent(Element("storageAccount").apply {
                text = account
            })
        }

        storagesElement
    }
}
