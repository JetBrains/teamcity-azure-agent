/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
