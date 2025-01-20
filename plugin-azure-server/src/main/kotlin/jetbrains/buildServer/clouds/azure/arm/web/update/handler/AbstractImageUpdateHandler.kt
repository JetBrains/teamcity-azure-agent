package jetbrains.buildServer.clouds.azure.arm.web.update.handler

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

abstract class AbstractImageUpdateHandler : ImageUpdateHandler {

    companion object {
        private val gson = Gson()
    }

    fun parseString(data: String): JsonObject? {
        return JsonParser.parseString(data).asJsonObject
    }

    fun serialize(obj: JsonObject): String {
        return gson.toJson(obj)
    }
}
