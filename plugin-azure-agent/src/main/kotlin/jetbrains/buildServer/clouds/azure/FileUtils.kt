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

package jetbrains.buildServer.clouds.azure

import java.io.File

/**
 * File utilities.
 */
interface FileUtils {
    /**
     * Gets a file contents.
     *
     * @param file is a file.
     * @return contents.
     */
    fun readFile(file: File): String

    /**
     * Gets a file creation date.
     *
     * @param file is a file.
     * @return creation date as timestamp.
     */
    fun getCreationDate(file: File): Long

    /**
     * Lists files in the directory.
     *
     * @param directory is a directory.
     * @return list of files.
     */
    fun listFiles(directory: File): Array<File>
}
