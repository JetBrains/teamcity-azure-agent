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
import jetbrains.buildServer.clouds.azure.asm.models.Image;
import org.jdeferred.DonePipe;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Handles list of images request.
 */
public class ImagesHandler implements ResourceHandler {

    private static final String NAME = "Images";

    @NotNull
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Promise<Content, Throwable, Void> handle(@NotNull AzureApiConnector connector) {
        return connector.listImagesAsync().then(new DonePipe<List<Image>, Content, Throwable, Void>() {
            @Override
            public Promise<Content, Throwable, Void> pipeDone(List<Image> imageList) {
                final Element images = new Element(NAME);
                for (Image image : imageList) {
                    final Element imageElem = new Element("Image");
                    imageElem.setAttribute("name", image.getName());
                    imageElem.setAttribute("label", image.getLabel());
                    imageElem.setAttribute("generalized", String.valueOf(image.getGeneralized()));
                    imageElem.setAttribute("osType", image.getOs());
                    images.addContent(imageElem);
                }

                return new DeferredObject<Content, Throwable, Void>().resolve(images);
            }
        });
    }
}
