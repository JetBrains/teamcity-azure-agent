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
                    writer.appendLine("${it.key}=${it.value}")
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
