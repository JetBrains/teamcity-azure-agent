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
