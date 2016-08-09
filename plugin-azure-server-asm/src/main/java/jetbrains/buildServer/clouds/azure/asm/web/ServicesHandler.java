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

package jetbrains.buildServer.clouds.azure.asm.web;

import jetbrains.buildServer.clouds.azure.asm.connector.AzureApiConnector;
import org.jdeferred.DonePipe;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles list of services request.
 */
public class ServicesHandler implements ResourceHandler {

    private static final String NAME = "Services";

    @NotNull
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Promise<Content, Throwable, Void> handle(@NotNull AzureApiConnector connector) {
        return connector.listServicesAsync().then(new DonePipe<Map<String, Map<String, String>>, Content, Throwable, Void>() {
            @Override
            public Promise<Content, Throwable, Void> pipeDone(Map<String, Map<String, String>> servicesList) {
                Element services = new Element(NAME);
                for (String serviceName : servicesList.keySet()) {
                    final Element service = new Element("Service");
                    service.setAttribute("name", serviceName);

                    try {
                        final Map<String, String> instances = servicesList.get(serviceName);
                        service.addContent(getServiceInstances(instances));
                    } catch (Exception ex) {
                        service.setAttribute("inactive", "true");
                        service.setAttribute("errorMsg", ex.getMessage());
                    }
                    services.addContent(service);
                }

                return new DeferredObject<Content, Throwable, Void>().resolve(services);
            }
        });
    }

    private static List<Element> getServiceInstances(final Map<String, String> instances) {
        final List<Element> elements = new ArrayList<>();
        for (String instanceName : instances.keySet()) {
            final Element instanceElem = new Element("Instance");
            instanceElem.setAttribute("name", instanceName);
            instanceElem.setAttribute("osType", instances.get(instanceName));
            elements.add(instanceElem);
        }

        return elements;
    }
}
