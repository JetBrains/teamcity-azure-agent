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
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * @author Dmitry.Tretyakov
 *         Date: 20.06.2016
 *         Time: 15:58
 */
public class UnixConfigReaderTest {
    @Test
    public void testProcessUnixConfig() throws IOException {
        final Mockery m = new Mockery() {{
            setImposteriser(ClassImposteriser.INSTANCE);
        }};
        final FileUtils fileUtils = m.mock(FileUtils.class);
        final BuildAgentConfigurationEx agentConfiguration = m.mock(BuildAgentConfigurationEx.class);
        final IdleShutdown idleShutdown = m.mock(IdleShutdown.class);

        m.checking(new Expectations() {{
            one(fileUtils).readFile(new File("/var/lib/waagent/SharedConfig.xml"));
            will(returnValue(FileUtil.readText(new File("src/test/resources/SharedConfig.xml"))));

            one(agentConfiguration).setOwnPort(9090);
            one(agentConfiguration).addAlternativeAgentAddress("191.233.107.5");
            one(agentConfiguration).setServerUrl("http://tc-srv.cloudapp.net:8111");
            one(agentConfiguration).getConfigurationParameters();
            will(returnValue(Collections.emptyMap()));
            one(agentConfiguration).addConfigurationParameter(AzurePropertiesNames.INSTANCE_NAME, "paksvvm-53eb78da");
            one(agentConfiguration).addConfigurationParameter("system.cloud.profile_id", "cp1");
            one(agentConfiguration).addConfigurationParameter("teamcity.cloud.instance.hash", "Nx50NAfzeoljh3iJf77jvtci1BSWtaZ2");

            one(fileUtils).readFile(new File("/var/lib/waagent/ovf-env.xml"));
            will(returnValue(FileUtil.readText(new File("src/test/resources/ovf-env.xml"))));

            one(idleShutdown).setIdleTime(2400000L);
        }});

        UnixConfigReader configReader = new UnixConfigReader(agentConfiguration, idleShutdown, fileUtils);
        configReader.process();

        m.assertIsSatisfied();
    }

    @Test
    public void testProcessUnixConfig2() throws IOException {
        final Mockery m = new Mockery() {{
            setImposteriser(ClassImposteriser.INSTANCE);
        }};
        final FileUtils fileUtils = m.mock(FileUtils.class);
        final BuildAgentConfigurationEx agentConfiguration = m.mock(BuildAgentConfigurationEx.class);
        final IdleShutdown idleShutdown = m.mock(IdleShutdown.class);

        m.checking(new Expectations() {{
            one(fileUtils).readFile(new File("/var/lib/waagent/SharedConfig.xml"));
            will(returnValue(FileUtil.readText(new File("src/test/resources/SharedConfig.xml"))));

            one(agentConfiguration).setOwnPort(9090);
            one(agentConfiguration).addAlternativeAgentAddress("191.233.107.5");
            one(agentConfiguration).setName("paksvvm-53eb78da");
            one(agentConfiguration).setServerUrl("http://tc-srv.cloudapp.net:8111");
            one(agentConfiguration).getConfigurationParameters();
            will(returnValue(CollectionsUtil.asMap(AzurePropertiesNames.INSTANCE_NAME, "")));
            one(agentConfiguration).addConfigurationParameter(AzurePropertiesNames.INSTANCE_NAME, "paksvvm-53eb78da");
            one(agentConfiguration).addConfigurationParameter("system.cloud.profile_id", "cp1");
            one(agentConfiguration).addConfigurationParameter("teamcity.cloud.instance.hash", "Nx50NAfzeoljh3iJf77jvtci1BSWtaZ2");

            one(fileUtils).readFile(new File("/var/lib/waagent/ovf-env.xml"));
            will(returnValue(FileUtil.readText(new File("src/test/resources/ovf-env2.xml"))));

            one(idleShutdown).setIdleTime(2400000L);
        }});

        UnixConfigReader configReader = new UnixConfigReader(agentConfiguration, idleShutdown, fileUtils);
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

        m.checking(new Expectations() {{
            one(fileUtils).readFile(new File("/var/lib/waagent/SharedConfig.xml"));
            will(returnValue(FileUtil.readText(new File("src/test/resources/SharedConfig.xml"))));

            one(agentConfiguration).setOwnPort(9090);
            one(agentConfiguration).addAlternativeAgentAddress("191.233.107.5");
            one(agentConfiguration).getConfigurationParameters();
            will(returnValue(Collections.emptyMap()));
            one(agentConfiguration).addConfigurationParameter(AzurePropertiesNames.INSTANCE_NAME, "paksvvm-53eb78da");

            one(fileUtils).readFile(new File("/var/lib/waagent/ovf-env.xml"));
            will(returnValue(StringUtil.EMPTY));
        }});

        UnixConfigReader configReader = new UnixConfigReader(agentConfiguration, idleShutdown, fileUtils);
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

        m.checking(new Expectations() {{
            one(fileUtils).readFile(new File("/var/lib/waagent/ovf-env.xml"));
            will(returnValue(StringUtil.EMPTY));

            one(fileUtils).readFile(new File("/var/lib/waagent/SharedConfig.xml"));
            will(returnValue(StringUtil.EMPTY));
        }});

        UnixConfigReader configReader = new UnixConfigReader(agentConfiguration, idleShutdown, fileUtils);
        configReader.process();

        m.assertIsSatisfied();
    }
}
