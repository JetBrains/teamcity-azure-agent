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
