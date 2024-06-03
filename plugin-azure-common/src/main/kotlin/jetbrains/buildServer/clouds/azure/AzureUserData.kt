package jetbrains.buildServer.clouds.azure

import com.google.gson.GsonBuilder
import com.google.gson.annotations.Since
import org.apache.commons.codec.binary.Base64

class AzureUserData
{
    @Since(1.0)
    val version: String

    @Since(1.0)
    val cloudInstanceUserData: String

    @Since(1.0)
    val instanceId: String

    @Since(1.0)
    val pluginCode: String

    private constructor(cloudInstanceUserData: String, instanceId: String) {
        version = "1.0"
        this.cloudInstanceUserData = cloudInstanceUserData
        this.instanceId = instanceId
        pluginCode = PLUGIN_CODE
    }

    fun serialize(): String =
        Base64.encodeBase64String(gson.toJson(this).toByteArray(Charsets.UTF_8))

    companion object {
        const val PLUGIN_CODE = "arm"
        private val gson = GsonBuilder().setVersion(1.0).create()
        fun createV1(customData: String, instanceId: String): AzureUserData =
            AzureUserData(customData, instanceId)

        fun serializeV1(customData: String, instanceId: String): String =
            createV1(customData, instanceId).serialize()

        fun deserialize(data: String): AzureUserData =
            gson.fromJson(String(Base64.decodeBase64(data)), AzureUserData::class.java)
    }
}
