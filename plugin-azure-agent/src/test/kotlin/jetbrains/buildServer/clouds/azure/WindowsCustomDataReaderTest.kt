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
import jetbrains.buildServer.util.StringUtil
import org.jmock.Expectations
import org.jmock.Mockery
import org.testng.annotations.Test

import java.io.File

/**
 * @author Dmitry.Tretyakov
 * Date: 20.06.2016
 * Time: 15:05
 */
@Test
class WindowsCustomDataReaderTest {

    fun testProcessWindowsConfig() {
        val m = Mockery()

        val fileUtils = m.mock(FileUtils::class.java)
        val agentConfiguration = m.mock(BuildAgentConfigurationEx::class.java)
        val drive = System.getenv("SystemDrive")

        m.checking(object : Expectations() {
            init {
                one(agentConfiguration).serverUrl = "https://teamcityserver.url"
                one(agentConfiguration).addConfigurationParameter("system.cloud.profile_id", "arm-1")
                one(agentConfiguration).addConfigurationParameter("teamcity.cloud.instance.hash", "C7PfiVOkdPi6Ak5cAO8Iq5NWPtHbO14q")

                one(fileUtils).readFile(File("$drive\\AzureData\\CustomData.bin"))
                will(Expectations.returnValue(FileUtil.readText(File("src/test/resources/CustomData.bin"))))
            }
        })

        WindowsCustomDataReader(agentConfiguration, fileUtils).process()

        m.assertIsSatisfied()
    }

    fun testDisableIntegrationWithoutCustomDataFile() {
        val m = Mockery()
        val fileUtils = m.mock(FileUtils::class.java)
        val agentConfiguration = m.mock(BuildAgentConfigurationEx::class.java)
        val drive = System.getenv("SystemDrive")

        m.checking(object : Expectations() {
            init {
                one(fileUtils).readFile(File("$drive\\AzureData\\CustomData.bin"))
                will(Expectations.returnValue(StringUtil.EMPTY))
            }
        })

        WindowsCustomDataReader(agentConfiguration, fileUtils).process()

        m.assertIsSatisfied()
    }

    fun testDisableIntegrationWithoutPropertiesFile() {
        val m = Mockery()
        val fileUtils = m.mock(FileUtils::class.java)
        val agentConfiguration = m.mock(BuildAgentConfigurationEx::class.java)
        val drive = System.getenv("SystemDrive")

        m.checking(object : Expectations() {
            init {
                one(fileUtils).readFile(File("$drive\\AzureData\\CustomData.bin"))
                will(Expectations.returnValue("data"))
            }
        })

        WindowsCustomDataReader(agentConfiguration, fileUtils).process()

        m.assertIsSatisfied()
    }
}
