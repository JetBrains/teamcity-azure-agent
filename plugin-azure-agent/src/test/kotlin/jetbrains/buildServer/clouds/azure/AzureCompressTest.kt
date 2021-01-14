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

package jetbrains.buildServer.clouds.azure

import org.testng.Assert
import org.testng.annotations.Test

class AzureCompressTest {

    private val map = mapOf("system.cloud.profile_id" to "arm",
    "teamcity.cloud.instance.hash" to "aabbbssccdsfsdf",
    "azure.instance.name" to "build-agent1")

    @Test
    fun encodeData() {
        val data = AzureCompress.encode(map)
        Assert.assertNotNull(data)
    }

    @Test
    fun decodeData() {
        val result = AzureCompress.decode("H4sIAAAAAAAAAEXKQQrEIAwF0L3gUSr0AJ6lfJM4DagtJi7a089mYNbv2WMuPVG7Fqd7XlWbHMoZs8fggk7qz491mGOQpBN2ZqCUYkbEVo1rDHjXlH8a6JLL0sYbPjJ8j+ELuRtxb20AAAA=")
        Assert.assertEquals(result, map)
    }
}
