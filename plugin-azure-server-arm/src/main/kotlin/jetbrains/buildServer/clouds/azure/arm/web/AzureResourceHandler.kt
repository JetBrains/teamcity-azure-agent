/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnectorImpl
import jetbrains.buildServer.clouds.azure.utils.PluginPropertiesUtil
import jetbrains.buildServer.controllers.BasePropertiesBean
import kotlinx.coroutines.experimental.Deferred
import org.jdom.Content

import javax.servlet.http.HttpServletRequest

/**
 * Azure resource handler.
 */
internal abstract class AzureResourceHandler : ResourceHandler {
    override fun handle(request: HttpServletRequest): Deferred<Content> {
        val propsBean = BasePropertiesBean(null)
        PluginPropertiesUtil.bindPropertiesFromRequest(request, propsBean, true)

        val props = propsBean.properties
        val tenantId = props[AzureConstants.TENANT_ID]!!
        val clientId = props[AzureConstants.CLIENT_ID]!!
        val clientSecret = props["secure:" + AzureConstants.CLIENT_SECRET]!!
        val subscriptionId = props[AzureConstants.SUBSCRIPTION_ID]!!
        val location = props[AzureConstants.LOCATION]!!

        val apiConnector = AzureApiConnectorImpl(tenantId, clientId, clientSecret)
        apiConnector.setSubscriptionId(subscriptionId)
        apiConnector.setLocation(location)

        return handle(apiConnector, request)
    }

    protected abstract fun handle(connector: AzureApiConnector, request: HttpServletRequest): Deferred<Content>
}
