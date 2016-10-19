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
            allowing(fileUtils).readFile(new File("/var/lib/waagent/SharedConfig.xml"));
            will(returnValue(FileUtil.readText(new File("src/test/resources/SharedConfig.xml"))));

            allowing(agentConfiguration).setOwnPort(9090);
            allowing(agentConfiguration).addAlternativeAgentAddress("191.233.107.5");
            allowing(agentConfiguration).setName("paksvvm-53eb78da");
            allowing(agentConfiguration).setServerUrl("http://tc-srv.cloudapp.net:8111");
            allowing(agentConfiguration).getConfigurationParameters();
            will(returnValue(Collections.emptyMap()));
            allowing(agentConfiguration).addConfigurationParameter(AzurePropertiesNames.INSTANCE_NAME, "paksvvm-53eb78da");
            allowing(agentConfiguration).addConfigurationParameter("system.cloud.profile_id", "cp1");
            allowing(agentConfiguration).addConfigurationParameter("teamcity.cloud.instance.hash", "Nx50NAfzeoljh3iJf77jvtci1BSWtaZ2");

            allowing(fileUtils).readFile(new File("/var/lib/waagent/ovf-env.xml"));
            will(returnValue(FileUtil.readText(new File("src/test/resources/ovf-env.xml"))));

            allowing(idleShutdown).setIdleTime(2400000L);
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
            allowing(fileUtils).readFile(new File("/var/lib/waagent/SharedConfig.xml"));
            will(returnValue(FileUtil.readText(new File("src/test/resources/SharedConfig.xml"))));

            allowing(agentConfiguration).setOwnPort(9090);
            allowing(agentConfiguration).addAlternativeAgentAddress("191.233.107.5");
            allowing(agentConfiguration).getConfigurationParameters();
            will(returnValue(Collections.emptyMap()));
            allowing(agentConfiguration).addConfigurationParameter(AzurePropertiesNames.INSTANCE_NAME, "paksvvm-53eb78da");

            allowing(fileUtils).readFile(new File("/var/lib/waagent/ovf-env.xml"));
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
            allowing(fileUtils).readFile(new File("/var/lib/waagent/ovf-env.xml"));
            will(returnValue(StringUtil.EMPTY));

            allowing(fileUtils).readFile(new File("/var/lib/waagent/SharedConfig.xml"));
            will(returnValue(StringUtil.EMPTY));
        }});

        UnixConfigReader configReader = new UnixConfigReader(agentConfiguration, idleShutdown, fileUtils);
        configReader.process();

        m.assertIsSatisfied();
    }
}
