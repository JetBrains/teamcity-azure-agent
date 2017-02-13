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

package jetbrains.buildServer.clouds.azure;

import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author Dmitry.Tretyakov
 *         Date: 20.06.2016
 *         Time: 15:05
 */
public class WindowsConfigReaderTest {

    @Test
    public void testProcessWindowsConfig() throws IOException {
        final Mockery m = new Mockery() {{
            setImposteriser(ClassImposteriser.INSTANCE);
        }};

        final FileUtils fileUtils = m.mock(FileUtils.class);
        final BuildAgentConfigurationEx agentConfiguration = m.mock(BuildAgentConfigurationEx.class);
        final IdleShutdown idleShutdown = m.mock(IdleShutdown.class);
        final String drive = System.getenv("SystemDrive");

        m.checking(new Expectations() {{
            one(fileUtils).listFiles(new File(drive + "\\WindowsAzure\\Config"));
            will(returnValue(new File[]{new File("src/test/resources/SharedConfig.xml")}));

            one(fileUtils).getCreationDate(with(any(File.class)));
            will(returnValue(1L));

            one(agentConfiguration).setOwnPort(9090);
            one(agentConfiguration).addAlternativeAgentAddress("191.233.107.5");
            one(agentConfiguration).setServerUrl("https://teamcityserver.url");
            one(agentConfiguration).addConfigurationParameter("system.cloud.profile_id", "arm-1");
            one(agentConfiguration).addConfigurationParameter("teamcity.cloud.instance.hash", "C7PfiVOkdPi6Ak5cAO8Iq5NWPtHbO14q");

            one(fileUtils).readFile(new File(drive + "\\AzureData\\CustomData.bin"));
            will(returnValue(FileUtil.readText(new File("src/test/resources/CustomData.bin"))));

            one(idleShutdown).setIdleTime(2400000L);
        }});

        WindowsConfigReader configReader = new WindowsConfigReader(agentConfiguration, idleShutdown, fileUtils);
        configReader.process();

        m.assertIsSatisfied();
    }

    @Test
    public void testProcessWindowsConfigWithoutEndpoints() throws IOException {
        final Mockery m = new Mockery() {{
            setImposteriser(ClassImposteriser.INSTANCE);
        }};
        final FileUtils fileUtils = m.mock(FileUtils.class);
        final BuildAgentConfigurationEx agentConfiguration = m.mock(BuildAgentConfigurationEx.class);
        final IdleShutdown idleShutdown = m.mock(IdleShutdown.class);
        final String drive = System.getenv("SystemDrive");

        m.checking(new Expectations() {{
            one(fileUtils).listFiles(new File(drive + "\\WindowsAzure\\Config"));
            will(returnValue(new File[]{new File("src/test/resources/windows-config.xml")}));

            one(fileUtils).getCreationDate(with(any(File.class)));
            will(returnValue(1L));

            one(agentConfiguration).setServerUrl("https://teamcityserver.url");
            one(agentConfiguration).addConfigurationParameter("system.cloud.profile_id", "arm-1");
            one(agentConfiguration).addConfigurationParameter("teamcity.cloud.instance.hash", "C7PfiVOkdPi6Ak5cAO8Iq5NWPtHbO14q");

            one(fileUtils).readFile(new File(drive + "\\AzureData\\CustomData.bin"));
            will(returnValue(FileUtil.readText(new File("src/test/resources/CustomData.bin"))));

            one(idleShutdown).setIdleTime(2400000L);
        }});

        WindowsConfigReader configReader = new WindowsConfigReader(agentConfiguration, idleShutdown, fileUtils);
        configReader.process();

        m.assertIsSatisfied();
    }

    @Test
    public void testDisableIntegrationWithoutCustomDataFile() throws IOException {
        final Mockery m = new Mockery() {{
            setImposteriser(ClassImposteriser.INSTANCE);
        }};
        final FileUtils fileUtils = m.mock(FileUtils.class);
        final BuildAgentConfigurationEx agentConfiguration = m.mock(BuildAgentConfigurationEx.class);
        final IdleShutdown idleShutdown = m.mock(IdleShutdown.class);
        final String drive = System.getenv("SystemDrive");

        m.checking(new Expectations() {{
            one(fileUtils).readFile(new File(drive + "\\AzureData\\CustomData.bin"));
            will(returnValue(StringUtil.EMPTY));

            one(fileUtils).listFiles(new File(drive + "\\WindowsAzure\\Config"));
            will(returnValue(new File[]{new File("src/test/resources/windows-config.xml")}));

            one(fileUtils).getCreationDate(with(any(File.class)));
            will(returnValue(1L));
        }});

        WindowsConfigReader configReader = new WindowsConfigReader(agentConfiguration, idleShutdown, fileUtils);
        configReader.process();

        m.assertIsSatisfied();
    }

    @Test
    public void testDisableIntegrationWithoutPropertiesFile() throws IOException {
        final Mockery m = new Mockery() {{
            setImposteriser(ClassImposteriser.INSTANCE);
        }};
        final FileUtils fileUtils = m.mock(FileUtils.class);
        final BuildAgentConfigurationEx agentConfiguration = m.mock(BuildAgentConfigurationEx.class);
        final IdleShutdown idleShutdown = m.mock(IdleShutdown.class);
        final String drive = System.getenv("SystemDrive");

        m.checking(new Expectations() {{
            one(fileUtils).readFile(new File(drive + "\\AzureData\\CustomData.bin"));
            will(returnValue("data"));

            one(fileUtils).listFiles(new File(drive + "\\WindowsAzure\\Config"));
            will(returnValue(new File[]{}));
        }});

        WindowsConfigReader configReader = new WindowsConfigReader(agentConfiguration, idleShutdown, fileUtils);
        configReader.process();

        m.assertIsSatisfied();
    }
}
