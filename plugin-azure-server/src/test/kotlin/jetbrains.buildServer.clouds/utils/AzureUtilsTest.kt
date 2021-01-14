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

package jetbrains.buildServer.clouds.utils

import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import org.testng.Assert
import org.testng.annotations.Test

@Test
class AzureUtilsTest {

    fun testCloudErrorDeserializationSuccess() {
        val json = """{
  "error": {
    "code": "code",
    "message": "message"
  }
}"""
        val errorMessage = AzureUtils.deserializeAzureError(json)

        Assert.assertEquals(errorMessage, "message (code)")
    }

    fun testCloudErrorDeserializationFailure() {
        val json = "mymessage"
        val errorMessage = AzureUtils.deserializeAzureError(json)

        Assert.assertEquals(errorMessage, json)
    }

    fun testAuthErrorDeserialization() {
        val json = """{
	"error_description": "description\r\nTimestamp: 2017-08-22 08:08:01Z",
	"error": "invalid_request"
}"""
        val errorMessage = AzureUtils.deserializeAuthError(json)

        Assert.assertEquals(errorMessage, "description")
    }
}
