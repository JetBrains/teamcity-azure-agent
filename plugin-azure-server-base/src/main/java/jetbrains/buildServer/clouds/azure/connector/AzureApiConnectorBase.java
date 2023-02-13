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

package jetbrains.buildServer.clouds.azure.connector;

import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * @author Dmitry.Tretyakov
 *         Date: 3/18/2016
 *         Time: 5:42 PM
 */
public abstract class AzureApiConnectorBase<T extends AbstractCloudImage, G extends AbstractCloudInstance> implements CloudApiConnector<T, G> {
    @NotNull
    public <R extends AbstractInstance> Map<String, R> fetchInstances(@NotNull final T image) throws CheckedCloudException {
        final Map<T, Map<String, R>> imageMap = fetchInstances(Collections.singleton(image));
        final Map<String, R> result = imageMap.get(image);
        return result != null ? result : Collections.<String, R>emptyMap();
    }
}
