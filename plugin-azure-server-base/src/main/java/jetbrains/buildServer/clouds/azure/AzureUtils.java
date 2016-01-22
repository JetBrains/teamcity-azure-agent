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

package jetbrains.buildServer.clouds.azure;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.clouds.CloudClientParameters;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Provides utils for azure services.
 */
public final class AzureUtils {
  private static final Type stringStringMapType = new TypeToken<Map<String, String>>() {
  }.getType();

  public static Collection<AzureCloudImageDetails> parseImageData(final CloudClientParameters params) {
    Gson gson = new Gson();
    final String imageData = params.getParameter("images_data");
    if (StringUtil.isEmpty(imageData)) {
      return Collections.emptyList();
    }
    final AzureCloudImageDetails[] images = gson.fromJson(imageData, AzureCloudImageDetails[].class);

    final String passwordData = params.getParameter("secure:passwords_data");
    final Map<String, String> data = gson.fromJson(passwordData, stringStringMapType);
    if (data != null) {
      for (AzureCloudImageDetails image : images) {
        if (data.get(image.getSourceName()) != null) {
          image.setPassword(data.get(image.getSourceName()));
        }
      }
    }
    return Arrays.asList(images);
  }
}
