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

package jetbrains.buildServer.clouds.azure.arm.utils

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.util.io.StreamUtil
import jetbrains.buildServer.util.StringUtil
import java.io.IOException

/**
 * Utilities.
 */
object AzureUtils {
    private val mapper = ObjectMapper()

    fun getResourceAsString(name: String): String {
        val stream = AzureUtils::class.java.getResourceAsStream(name) ?: return ""

        try {
            return StreamUtil.readText(stream)
        } catch (e: IOException) {
            return ""
        }
    }

    fun serializeObject(data: Any): String {
        try {
            return mapper.writeValueAsString(data)
        } catch (e: JsonProcessingException) {
            return StringUtil.EMPTY
        }
    }

    fun setTags(templateValue: String, tags: Map<String, String>): String {
        val reader = mapper.reader()
        try {
            val template = reader.readTree(templateValue) as ObjectNode
            val resources = template["resources"] as ArrayNode
            val machine = resources[resources.size() - 1] as ObjectNode
            machine.putObject("tags").apply {
                for ((key, value) in tags) {
                    this.put(key, value)
                }
            }
            return template.toString()
        } catch (e: Exception) {
            return templateValue
        }
    }
}
