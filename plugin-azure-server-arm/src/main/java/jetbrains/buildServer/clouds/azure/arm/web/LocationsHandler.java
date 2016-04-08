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
import java.util.Map;

/**
 * Handles locations request.
 */
class LocationsHandler extends AzureResourceHandler {

    @Override
    protected Promise<Content, Throwable, Void> handle(AzureApiConnector connector, HttpServletRequest request) {
        final String subscription = request.getParameter("subscription");
        return connector.getLocationsAsync(subscription).then(new DonePipe<Map<String, String>, Content, Throwable, Void>() {
            @Override
            public Promise<Content, Throwable, Void> pipeDone(Map<String, String> locations) {
                final Element locationsElement = new Element("locations");
                for (String id : locations.keySet()) {
                    final Element locationElement = new Element("location");
                    locationElement.setAttribute("id", id);
                    locationElement.setText(locations.get(id));
                    locationsElement.addContent(locationElement);
                }

                return new DeferredObject<Content, Throwable, Void>().resolve(locationsElement);
            }
        });
    }
}
