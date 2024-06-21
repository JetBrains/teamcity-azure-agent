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
