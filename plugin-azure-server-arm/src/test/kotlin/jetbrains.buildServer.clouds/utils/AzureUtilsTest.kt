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
        val errorMessage = AzureUtils.getAzureErrorMessage(json)

        Assert.assertEquals(errorMessage, "message (code)")
    }

    fun testCloudErrorDeserializationFailure() {
        val json = "mymessage"
        val errorMessage = AzureUtils.getAzureErrorMessage(json)

        Assert.assertEquals(errorMessage, json)
    }
}
