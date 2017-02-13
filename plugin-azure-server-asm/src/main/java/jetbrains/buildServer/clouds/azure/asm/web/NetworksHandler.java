/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.asm.web;

import jetbrains.buildServer.clouds.azure.asm.connector.AzureApiConnector;
import org.jdeferred.DonePipe;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Handles list of networks request.
 */
public class NetworksHandler implements ResourceHandler {

    private static final String NAME = "VirtualNetworks";

    @NotNull
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Promise<Content, Throwable, Void> handle(@NotNull AzureApiConnector connector) {
        return connector.listVirtualNetworksAsync().then(new DonePipe<List<String>, Content, Throwable, Void>() {
            @Override
            public Promise<Content, Throwable, Void> pipeDone(List<String> networksList) {
                Element virtualNetworksElement = new Element(NAME);
                for (String networkName : networksList) {
                    final Element vn = new Element("VirtualNetwork");
                    vn.setAttribute("name", networkName);
                    vn.setAttribute("label", networkName);
                    virtualNetworksElement.addContent(vn);
                }

                return new DeferredObject<Content, Throwable, Void>().resolve(virtualNetworksElement);
            }
        });
    }
}
