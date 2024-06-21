package jetbrains.buildServer.clouds.azure

import jetbrains.buildServer.agent.BuildAgentConfigurationEx
import jetbrains.buildServer.util.FileUtil
import org.jmock.Expectations
import org.jmock.Mockery
import org.testng.annotations.Test

import java.io.File

/**
 * @author Dmitry.Tretyakov
 * Date: 20.06.2016
 * Time: 15:58
 */
@Test
class UnixCustomDataReaderTest {

    fun testProcessUnixConfig() {
        val m = Mockery()
        val fileUtils = m.mock(FileUtils::class.java)
        val agentConfiguration = m.mock(BuildAgentConfigurationEx::class.java)

        m.checking(object : Expectations() {
            init {
                one(agentConfiguration).serverUrl = "http://tc-srv.cloudapp.net:8111"
                one(agentConfiguration).addConfigurationParameter("system.cloud.profile_id", "cp1")
                one(agentConfiguration).addConfigurationParameter("teamcity.cloud.instance.hash", "Nx50NAfzeoljh3iJf77jvtci1BSWtaZ2")

                one(fileUtils).readFile(File("/var/lib/waagent/ovf-env.xml"))
                will(Expectations.returnValue(FileUtil.readText(File("src/test/resources/ovf-env.xml"))))
            }
        })

        UnixCustomDataReader(agentConfiguration, fileUtils).process()

        m.assertIsSatisfied()
    }

    fun testProcessUnixConfig2() {
        val m =  Mockery()
        val fileUtils = m.mock(FileUtils::class.java)
        val agentConfiguration = m.mock(BuildAgentConfigurationEx::class.java)

        m.checking(object : Expectations() {
            init {
                one(agentConfiguration).name = "paksvvm-53eb78da"
                one(agentConfiguration).serverUrl = "http://tc-srv.cloudapp.net:8111"
                one(agentConfiguration).addConfigurationParameter(AzureProperties.INSTANCE_NAME, "paksvvm-53eb78da")
                one(agentConfiguration).addConfigurationParameter("system.cloud.profile_id", "cp1")
                one(agentConfiguration).addConfigurationParameter("teamcity.cloud.instance.hash", "Nx50NAfzeoljh3iJf77jvtci1BSWtaZ2")

                one(fileUtils).readFile(File("/var/lib/waagent/ovf-env.xml"))
                will(Expectations.returnValue(FileUtil.readText(File("src/test/resources/ovf-env2.xml"))))
            }
        })

        UnixCustomDataReader(agentConfiguration, fileUtils).process()

        m.assertIsSatisfied()
    }
}
