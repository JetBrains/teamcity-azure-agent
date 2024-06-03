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

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.BuildAgentConfigurationEx
import jetbrains.buildServer.util.FileUtil
import jetbrains.buildServer.util.StringUtil
import org.apache.commons.codec.binary.Base64
import org.jdom.Element
import org.jdom.Namespace
import org.jdom.xpath.XPath

import java.io.ByteArrayInputStream

class UnixCustomDataReader(agentConfiguration: BuildAgentConfigurationEx,
                           fileUtils: FileUtils)
    : AzureCustomDataReader(agentConfiguration, fileUtils) {

    override val customDataFileName = UNIX_CUSTOM_DATA_FILE

    override fun parseCustomData(customData: String): MetadataReaderResult {
        // Process custom data
        return try {
            val documentElement = FileUtil.parseDocument(ByteArrayInputStream(customData.toByteArray()), false)
            if (documentElement == null) {
                LOG.warn(String.format(UNABLE_TO_READ_CUSTOM_DATA_FILE, customDataFileName))
                MetadataReaderResult.SKIP
            } else {
                readCustomData(documentElement)
            }
        } catch (e: Exception) {
            LOG.warnAndDebugDetails(String.format(UNABLE_TO_READ_CUSTOM_DATA_FILE, customDataFileName), e)
            MetadataReaderResult.SKIP
        }
    }

    private fun readCustomData(documentElement: Element): MetadataReaderResult {

        val namespaces = documentElement.additionalNamespaces
                .filterIsInstance(Namespace::class.java)
                .toMutableList()
        namespaces.add(documentElement.namespace)

        var prefix: String? = null
        for (namespace in namespaces) {
            if (namespace.uri == WINDOWS_AZURE_NAMESPACE) {
                prefix = namespace.prefix
                break
            }
        }

        if (prefix == null) {
            LOG.warn("Unable to find $WINDOWS_AZURE_NAMESPACE namespace in file $UNIX_CUSTOM_DATA_FILE")
            return MetadataReaderResult.SKIP
        }

        val customDataQuery = "string(//$prefix:LinuxProvisioningConfigurationSet/$prefix:CustomData)"
        val xPath = XPath.newInstance(customDataQuery)
        val value = xPath.selectSingleNode(documentElement)
        if (value == null) {
            LOG.warn("Unable to read CustomData element in file $UNIX_CUSTOM_DATA_FILE")
            return MetadataReaderResult.SKIP
        }

        var serializedCustomData = value.toString()
        if (StringUtil.isEmpty(serializedCustomData)) {
            LOG.warn("CustomData element in file $UNIX_CUSTOM_DATA_FILE is empty")
            return MetadataReaderResult.SKIP
        }

        val bytes = serializedCustomData.toByteArray()
        if (!Base64.isBase64(bytes)) {
            LOG.warn("CustomData value should be Base64 encoded in file $UNIX_CUSTOM_DATA_FILE is empty")
            return MetadataReaderResult.SKIP
        }

        // New azure linux agent execute additional Base64 encode
        val decodedBytes = Base64.decodeBase64(bytes)
        if (Base64.isBase64(decodedBytes)) {
            serializedCustomData = String(decodedBytes)
        }

        return processCustomData(serializedCustomData)
    }

    companion object {
        private val LOG = Logger.getInstance(UnixCustomDataReader::class.java.name)
        private const val UNIX_CONFIG_DIR = "/var/lib/waagent/"
        private const val UNIX_CUSTOM_DATA_FILE = UNIX_CONFIG_DIR + "ovf-env.xml"
        private const val WINDOWS_AZURE_NAMESPACE = "http://schemas.microsoft.com/windowsazure"
    }
}
