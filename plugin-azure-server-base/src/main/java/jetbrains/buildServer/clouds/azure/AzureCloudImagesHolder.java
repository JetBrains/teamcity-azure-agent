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

package jetbrains.buildServer.clouds.azure;

import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores info about cloud images and instances
 */
public class AzureCloudImagesHolder {
  private final Map<String, AbstractCloudImage> myImages = new ConcurrentHashMap<>();

  @Nullable
  public AbstractCloudImage findImage(@NotNull final String profileId, @NotNull final String sourceId) {
    final String key = getKey(profileId, sourceId);
    return myImages.get(key);
  }

  public void addImage(@NotNull String profileId, @NotNull AbstractCloudImage image) {
    final String key = getKey(profileId, image.getId());
    myImages.put(key, image);
  }

  private static String getKey(@NotNull String profileId, @NotNull String sourceId) {
    return String.format("%s-%s", profileId, sourceId);
  }
}
