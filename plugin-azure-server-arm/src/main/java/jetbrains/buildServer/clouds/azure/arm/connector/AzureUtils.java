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

package jetbrains.buildServer.clouds.azure.arm.connector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.util.io.StreamUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * Utilities.
 */
final class AzureUtils {

    @NotNull
    static String getResourceAsString(@NotNull final String name) {
        final InputStream stream = AzureUtils.class.getResourceAsStream(name);
        if (stream == null) {
            return StringUtil.EMPTY;
        }

        try {
            return StreamUtil.readText(stream);
        } catch (IOException e) {
            return StringUtil.EMPTY;
        }
    }

    @NotNull
    static String serializeObject(@NotNull final Object object) {
        final ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            return StringUtil.EMPTY;
        }
    }
}
