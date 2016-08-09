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

import java.util.Map;

/**
 * Handles vm sizes request.
 */
public class VmSizesHandler implements ResourceHandler {

    private static final String NAME = "VmSizes";

    @NotNull
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Promise<Content, Throwable, Void> handle(@NotNull AzureApiConnector connector) {
        return connector.listVmSizesAsync().then(new DonePipe<Map<String, String>, Content, Throwable, Void>() {
            @Override
            public Promise<Content, Throwable, Void> pipeDone(Map<String, String> sizes) {
                final Element vmSizes = new Element(NAME);
                for (String size : sizes.keySet()) {
                    final Element vmSize = new Element("VmSize");
                    vmSize.setAttribute("name", size);
                    vmSize.setAttribute("label", sizes.get(size).replace("_", " "));
                    vmSizes.addContent(vmSize);
                }

                return new DeferredObject<Content, Throwable, Void>().resolve(vmSizes);
            }
        });
    }
}
