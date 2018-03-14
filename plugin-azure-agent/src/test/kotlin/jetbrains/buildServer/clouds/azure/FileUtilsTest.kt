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

import org.testng.Assert
import org.testng.annotations.Test

import java.io.File

/**
 * @author Dmitry.Tretyakov
 * Date: 20.06.2016
 * Time: 16:13
 */
@Test
class FileUtilsTest {

    fun getCreationDate() {
        val file = File("src/test/resources/SharedConfig.xml")
        val fileUtils = FileUtilsImpl()
        val creationDate = fileUtils.getCreationDate(file)

        Assert.assertTrue(creationDate <= file.lastModified())
    }
}
