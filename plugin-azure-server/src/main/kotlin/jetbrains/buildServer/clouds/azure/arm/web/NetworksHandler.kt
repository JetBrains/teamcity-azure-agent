/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.azure.arm.web

import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Element
import javax.servlet.http.HttpServletRequest

/**
 * Handles networks request.
 */
internal class NetworksHandler : AzureResourceHandler() {
    override suspend fun handle(connector: AzureApiConnector, request: HttpServletRequest) = coroutineScope {
        val region = request.getParameter("region")
        val networks = connector.getNetworks(region)
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
