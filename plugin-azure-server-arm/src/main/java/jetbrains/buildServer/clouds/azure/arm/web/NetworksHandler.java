/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm.web;

import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector;
import org.jdeferred.DonePipe;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.jdom.Content;
import org.jdom.Element;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Handles networks request.
 */
class NetworksHandler extends AzureResourceHandler {

    @Override
    protected Promise<Content, Throwable, Object> handle(AzureApiConnector connector, HttpServletRequest request) {
        final String group = request.getParameter("group");
        return connector.getNetworksByGroupAsync(group).then(new DonePipe<Map<String, List<String>>, Content, Throwable, Object>() {
            @Override
            public Promise<Content, Throwable, Object> pipeDone(Map<String, List<String>> networks) {
                final Element networksElement = new Element("networks");
                for (String name : networks.keySet()) {
                    final Element networkElement = new Element("network");
                    networkElement.setAttribute("id", name);

                    for (String subnet : networks.get(name)) {
                        final Element subnetElement = new Element("subnet");
                        subnetElement.setText(subnet);
                        networkElement.addContent(subnetElement);
                    }

                    networksElement.addContent(networkElement);
                }

                return new DeferredObject<Content, Throwable, Object>().resolve(networksElement);
            }
        });
    }
}
