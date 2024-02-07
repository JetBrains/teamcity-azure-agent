

package jetbrains.buildServer.clouds.azure

import jetbrains.buildServer.agent.BuildAgentConfigurationEx
import jetbrains.buildServer.util.FileUtil
import org.jmock.Expectations
import org.jmock.Mockery
import org.testng.annotations.Test
import java.io.File

@Test
class AzureMetadataReaderTest {

    fun testReadMetadata() {
        val m = Mockery()
        val agentConfiguration = m.mock(BuildAgentConfigurationEx::class.java)
        val spotTerminationChecker = m.mock(SpotInstanceTerminationChecker::class.java)
        val json = FileUtil.readText(File("src/test/resources/metadata.json"))

        m.checking(object : Expectations() {
            init {
                one(agentConfiguration).name
                will(returnValue(""))
                one(agentConfiguration).name = "IMDSSample"
                one(agentConfiguration).addAlternativeAgentAddress("X.X.X.X")
                one(agentConfiguration).addSystemProperty("ec2.public-hostname", "X.X.X.X")
            }
        })

        val metadata = AzureMetadata.deserializeInstanceMetadata(json)
        AzureMetadataReader(agentConfiguration, spotTerminationChecker).updateConfiguration(metadata)

        m.assertIsSatisfied()
    }
}
