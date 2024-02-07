

package jetbrains.buildServer.clouds.azure.arm.web

import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.controllers.BasePropertiesBean
import org.jdom.Content
import javax.servlet.http.HttpServletRequest

/**
 * Request handler.
 */
internal interface ResourceHandler {
    suspend fun handle(request: HttpServletRequest, context: ResourceHandlerContext): Content
}

internal data class ResourceHandlerContext(val apiConnector: AzureApiConnector, val propertiesBean: BasePropertiesBean)
