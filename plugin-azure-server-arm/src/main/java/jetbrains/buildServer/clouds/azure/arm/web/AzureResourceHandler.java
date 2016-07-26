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

package jetbrains.buildServer.clouds.azure.arm.web;

import jetbrains.buildServer.clouds.azure.arm.AzureConstants;
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnectorImpl;
import jetbrains.buildServer.clouds.azure.utils.PluginPropertiesUtil;
import jetbrains.buildServer.controllers.BasePropertiesBean;
import org.jdeferred.Promise;
import org.jdom.Content;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Azure resource handler.
 */
abstract class AzureResourceHandler implements ResourceHandler {

    @Override
    public Promise<Content, Throwable, Void> handle(@NotNull HttpServletRequest request) {
        BasePropertiesBean propsBean = new BasePropertiesBean(null);
        PluginPropertiesUtil.bindPropertiesFromRequest(request, propsBean, true);

        final Map<String, String> props = propsBean.getProperties();
        final String tenantId = props.get(AzureConstants.TENANT_ID);
        final String clientId = props.get(AzureConstants.CLIENT_ID);
        final String clientSecret = props.get("secure:" + AzureConstants.CLIENT_SECRET);
        final String subscriptionId = props.get(AzureConstants.SUBSCRIPTION_ID);
        final String location = props.get(AzureConstants.LOCATION);

        final AzureApiConnectorImpl apiConnector = new AzureApiConnectorImpl(tenantId, clientId, clientSecret);
        apiConnector.setSubscriptionId(subscriptionId);
        apiConnector.setLocation(location);

        return handle(apiConnector, request);
    }

    protected abstract Promise<Content, Throwable, Void> handle(AzureApiConnector connector, HttpServletRequest request);
}
