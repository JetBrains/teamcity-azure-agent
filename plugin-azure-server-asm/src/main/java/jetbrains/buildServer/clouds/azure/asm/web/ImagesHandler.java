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

import com.intellij.openapi.util.Pair;
import jetbrains.buildServer.clouds.azure.asm.connector.AzureApiConnector;
import org.jdeferred.DonePipe;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Handles list of images request.
 */
public class ImagesHandler implements ResourceHandler {
    @Override
    public Promise<Content, Throwable, Void> handle(@NotNull AzureApiConnector connector) {
        return connector.listImagesAsync().then(new DonePipe<Map<String, Pair<Boolean, String>>, Content, Throwable, Void>() {
            @Override
            public Promise<Content, Throwable, Void> pipeDone(Map<String, Pair<Boolean, String>> imagesMap) {
                final Element images = new Element("Images");
                for (String imageName : imagesMap.keySet()) {
                    final Element imageElem = new Element("Image");
                    imageElem.setAttribute("name", imageName);
                    final Pair<Boolean, String> pair = imagesMap.get(imageName);
                    imageElem.setAttribute("generalized", String.valueOf(pair.getFirst()));
                    imageElem.setAttribute("osType", pair.getSecond());
                    images.addContent(imageElem);
                }

                return new DeferredObject<Content, Throwable, Void>().resolve(images);
            }
        });
    }
}
