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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * File-based number provider.
 */
public class FileIdProvider implements IdProvider {

    private static final Logger LOG = Logger.getInstance(FileIdProvider.class.getName());
    private final File myStorageFile;

    public FileIdProvider(@NotNull File storageFile) {
        myStorageFile = storageFile;
        if (!myStorageFile.exists()) {
            try {
                FileUtil.writeFileAndReportErrors(myStorageFile, "1");
            } catch (IOException e) {
                LOG.warn(String.format("Unable to write idx file '%s': %s", myStorageFile.getAbsolutePath(), e.toString()));
            }
        }
    }

    @Override
    public int getNextId() {
        try {
            final int nextIdx = Integer.parseInt(FileUtil.readText(myStorageFile));
            FileUtil.writeFileAndReportErrors(myStorageFile, String.valueOf(nextIdx + 1));
            return nextIdx;
        } catch (Exception e) {
            LOG.warn("Unable to read idx file: " + e.toString());
            return 0;
        }
    }
}
