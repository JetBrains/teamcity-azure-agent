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
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.testng.annotations.Test;

import java.io.File;

/**
 * @author Dmitry.Tretyakov
 * Date: 20.06.2016
 * Time: 15:58
 */
public class UnixCustomDataReaderTest {
    @Test
    public void testProcessUnixConfig() throws Exception {
        final Mockery m = new Mockery() {{
            setImposteriser(ClassImposteriser.INSTANCE);
        }};
        final FileUtils fileUtils = m.mock(FileUtils.class);
        final BuildAgentConfigurationEx agentConfiguration = m.mock(BuildAgentConfigurationEx.class);
        final IdleShutdown idleShutdown = m.mock(IdleShutdown.class);

        m.checking(new Expectations() {{
            one(agentConfiguration).setServerUrl("http://tc-srv.cloudapp.net:8111");
            one(agentConfiguration).addConfigurationParameter("system.cloud.profile_id", "cp1");
            one(agentConfiguration).addConfigurationParameter("teamcity.cloud.instance.hash", "Nx50NAfzeoljh3iJf77jvtci1BSWtaZ2");

            one(fileUtils).readFile(new File("/var/lib/waagent/ovf-env.xml"));
            will(returnValue(FileUtil.readText(new File("src/test/resources/ovf-env.xml"))));

            one(idleShutdown).setIdleTime(2400000L);
        }});

        new UnixCustomDataReader(agentConfiguration, idleShutdown, fileUtils).process();

        m.assertIsSatisfied();
    }

    @Test
    public void testProcessUnixConfig2() throws Exception {
        final Mockery m = new Mockery() {{
            setImposteriser(ClassImposteriser.INSTANCE);
        }};
        final FileUtils fileUtils = m.mock(FileUtils.class);
        final BuildAgentConfigurationEx agentConfiguration = m.mock(BuildAgentConfigurationEx.class);
        final IdleShutdown idleShutdown = m.mock(IdleShutdown.class);

        m.checking(new Expectations() {{
            one(agentConfiguration).setName("paksvvm-53eb78da");
            one(agentConfiguration).setServerUrl("http://tc-srv.cloudapp.net:8111");
            one(agentConfiguration).addConfigurationParameter(AzurePropertiesNames.INSTANCE_NAME, "paksvvm-53eb78da");
            one(agentConfiguration).addConfigurationParameter("system.cloud.profile_id", "cp1");
            one(agentConfiguration).addConfigurationParameter("teamcity.cloud.instance.hash", "Nx50NAfzeoljh3iJf77jvtci1BSWtaZ2");

            one(fileUtils).readFile(new File("/var/lib/waagent/ovf-env.xml"));
            will(returnValue(FileUtil.readText(new File("src/test/resources/ovf-env2.xml"))));

            one(idleShutdown).setIdleTime(2400000L);
        }});

        new UnixCustomDataReader(agentConfiguration, idleShutdown, fileUtils).process();

        m.assertIsSatisfied();
    }
}
