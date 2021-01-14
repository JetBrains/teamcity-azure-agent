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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.xml.bind.DatatypeConverter

object AzureCompress {

    fun encode(env: Map<String, String>): String {
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream).use { stream ->
            stream.bufferedWriter().use { writer ->
                env.forEach {
                    writer.appendln("${it.key}=${it.value}")
                }
            }
        }

        return DatatypeConverter.printBase64Binary(byteStream.toByteArray())
    }

    fun decode(text: String): Map<String, String> {
        val map = hashMapOf<String, String>()
        val byteStream = ByteArrayInputStream(DatatypeConverter.parseBase64Binary(text))
        GZIPInputStream(byteStream).use { stream ->
            stream.bufferedReader().lines().forEach {
                val (key, value) = it.split('=')
                map[key] = value
            }
        }
        return map
    }
}
