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
                           idleShutdown: IdleShutdown,
                           fileUtils: FileUtils)
    : AzureCustomDataReader(agentConfiguration, idleShutdown, fileUtils) {

    override val customDataFileName = UNIX_CUSTOM_DATA_FILE

    override fun parseCustomData(customData: String) {
        // Process custom data
        try {
            val documentElement = FileUtil.parseDocument(ByteArrayInputStream(customData.toByteArray()), false)
            if (documentElement == null) {
                LOG.warn(String.format(UNABLE_TO_READ_CUSTOM_DATA_FILE, customDataFileName))
            } else {
                readCustomData(documentElement)
            }
        } catch (e: Exception) {
            LOG.warnAndDebugDetails(String.format(UNABLE_TO_READ_CUSTOM_DATA_FILE, customDataFileName), e)
        }
    }

    private fun readCustomData(documentElement: Element) {

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
            return
        }

        val customDataQuery = "string(//$prefix:LinuxProvisioningConfigurationSet/$prefix:CustomData)"
        val xPath = XPath.newInstance(customDataQuery)
        val value = xPath.selectSingleNode(documentElement)
        if (value == null) {
            LOG.warn("Unable to read CustomData element in file $UNIX_CUSTOM_DATA_FILE")
            return
        }

        var serializedCustomData = value.toString()
        if (StringUtil.isEmpty(serializedCustomData)) {
            LOG.warn("CustomData element in file $UNIX_CUSTOM_DATA_FILE is empty")
            return
        }

        val bytes = serializedCustomData.toByteArray()
        if (!Base64.isBase64(bytes)) {
            LOG.warn("CustomData value should be Base64 encoded in file $UNIX_CUSTOM_DATA_FILE is empty")
            return
        }

        // New azure linux agent execute additional Base64 encode
        val decodedBytes = Base64.decodeBase64(bytes)
        if (Base64.isBase64(decodedBytes)) {
            serializedCustomData = String(decodedBytes)
        }

        processCustomData(serializedCustomData)
    }

    companion object {
        private val LOG = Logger.getInstance(UnixCustomDataReader::class.java.name)
        private const val UNIX_CONFIG_DIR = "/var/lib/waagent/"
        private const val UNIX_CUSTOM_DATA_FILE = UNIX_CONFIG_DIR + "ovf-env.xml"
        private const val WINDOWS_AZURE_NAMESPACE = "http://schemas.microsoft.com/windowsazure"
    }
}
