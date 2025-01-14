package jetbrains.buildServer.clouds.azure

import jetbrains.buildServer.agent.BuildAgentConfigurationEx
import jetbrains.buildServer.util.FileUtil
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.concurrent.Synchroniser
import org.testng.annotations.Test
import java.io.File


@Test
class AzureMetadataReaderTest {

    fun testReadMetadata() {
        val m = Mockery()
        m.setThreadingPolicy(Synchroniser())
        val agentConfiguration = m.mock(BuildAgentConfigurationEx::class.java)
        val spotTerminationChecker = m.mock(SpotInstanceTerminationChecker::class.java)
        val json = FileUtil.readText(File("src/test/resources/metadata.json"))

        m.checking(object : Expectations() {
            init {
                one(agentConfiguration).name
                will(returnValue(""))
                one(agentConfiguration).name = "IMDSSample"
                one(agentConfiguration).addSystemProperty("azure.instance.name", "IMDSSample")
                one(agentConfiguration).addSystemProperty("azure.instance.offer", "UbuntuServer")
                one(agentConfiguration).addSystemProperty("azure.instance.osType", "Linux")
                one(agentConfiguration).addSystemProperty("azure.instance.sku", "16.04.0-LTS")
                one(agentConfiguration).addSystemProperty("azure.instance.version", "16.04.201610200")
                one(agentConfiguration).addSystemProperty("azure.instance.vmId", "5d33a910-a7a0-4443-9f01-6a807801b29b")
                one(agentConfiguration).addSystemProperty("azure.instance.vmSize", "Standard_A1")
                one(agentConfiguration).addAlternativeAgentAddress("X.X.X.X")
                one(agentConfiguration).addSystemProperty("ec2.public-hostname", "X.X.X.X")
            }
        })

        val metadata = AzureMetadata.deserializeInstanceMetadata(json)
        AzureMetadataReader(agentConfiguration, spotTerminationChecker).updateConfiguration(metadata)

        m.assertIsSatisfied()
    }

    fun testReadMetadata2() {
        val m = Mockery()
        m.setThreadingPolicy(Synchroniser())
        val agentConfiguration = m.mock(BuildAgentConfigurationEx::class.java)
        val spotTerminationChecker = m.mock(SpotInstanceTerminationChecker::class.java)
        val json = FileUtil.readText(File("src/test/resources/metadata2.json"))

        m.checking(object : Expectations() {
            init {
                one(agentConfiguration).name
                will(returnValue(""))
                one(agentConfiguration).name = "examplevmname"
                one(agentConfiguration).addSystemProperty("azure.instance.name", "examplevmname")
                one(agentConfiguration).addSystemProperty("azure.instance.licenseType", "Windows_Client")
                one(agentConfiguration).addSystemProperty("azure.instance.offer", "WindowsServer")
                one(agentConfiguration).addSystemProperty("azure.instance.osType", "Windows")
                one(agentConfiguration).addSystemProperty("azure.instance.sku", "2019-Datacenter")
                one(agentConfiguration).addSystemProperty("azure.instance.version", "15.05.22")
                one(agentConfiguration).addSystemProperty("azure.instance.vmId", "02aab8a4-74ef-476e-8182-f6d2ba4166a6")
                one(agentConfiguration).addSystemProperty("azure.instance.vmSize", "Standard_A3")
                one(agentConfiguration).addSystemProperty("azure.instance.vmScaleSetName", "crpteste9vflji9")
                one(agentConfiguration).addSystemProperty("azure.instance.tags", "baz:bash;foo:bar")
                one(agentConfiguration).addSystemProperty("azure.instance.userData", "Zm9vYmFy")
                one(agentConfiguration).addAlternativeAgentAddress("X.X.X.X")
                one(agentConfiguration).addSystemProperty("ec2.public-hostname", "X.X.X.Y")
            }
        })

        val metadata = AzureMetadata.deserializeInstanceMetadata(json)
        AzureMetadataReader(agentConfiguration, spotTerminationChecker).updateConfiguration(metadata)

//        m.assertIsSatisfied()
    }
}
