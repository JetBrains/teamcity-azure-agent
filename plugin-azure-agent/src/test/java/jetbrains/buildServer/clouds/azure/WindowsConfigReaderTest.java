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
            allowing(fileUtils).listFiles(new File(drive + "\\WindowsAzure\\Config"));
            will(returnValue(new File[]{new File("src/test/resources/SharedConfig.xml")}));

            allowing(fileUtils).getCreationDate(with(any(File.class)));
            will(returnValue(1L));

            allowing(agentConfiguration).setOwnPort(9090);
            allowing(agentConfiguration).setName("paksvvm-53eb78da");
            allowing(agentConfiguration).addAlternativeAgentAddress("191.233.107.5");
            allowing(agentConfiguration).setServerUrl("https://teamcityserver.url");
            allowing(agentConfiguration).addConfigurationParameter(AzurePropertiesNames.INSTANCE_NAME, "paksvvm-53eb78da");
            allowing(agentConfiguration).addConfigurationParameter("system.cloud.profile_id", "arm-1");
            allowing(agentConfiguration).addConfigurationParameter("teamcity.cloud.instance.hash", "C7PfiVOkdPi6Ak5cAO8Iq5NWPtHbO14q");

            allowing(fileUtils).readFile(new File(drive + "\\AzureData\\CustomData.bin"));
            will(returnValue(FileUtil.readText(new File("src/test/resources/CustomData.bin"))));

            allowing(idleShutdown).setIdleTime(2400000L);
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
            allowing(fileUtils).listFiles(new File(drive + "\\WindowsAzure\\Config"));
            will(returnValue(new File[]{new File("src/test/resources/windows-config.xml")}));

            allowing(fileUtils).getCreationDate(with(any(File.class)));
            will(returnValue(1L));

            allowing(agentConfiguration).setName("tc-win-agent");
            allowing(agentConfiguration).setServerUrl("https://teamcityserver.url");
            allowing(agentConfiguration).addConfigurationParameter(AzurePropertiesNames.INSTANCE_NAME, "tc-win-agent");
            allowing(agentConfiguration).addConfigurationParameter("system.cloud.profile_id", "arm-1");
            allowing(agentConfiguration).addConfigurationParameter("teamcity.cloud.instance.hash", "C7PfiVOkdPi6Ak5cAO8Iq5NWPtHbO14q");

            allowing(fileUtils).readFile(new File(drive + "\\AzureData\\CustomData.bin"));
            will(returnValue(FileUtil.readText(new File("src/test/resources/CustomData.bin"))));

            allowing(idleShutdown).setIdleTime(2400000L);
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
            allowing(fileUtils).readFile(new File(drive + "\\AzureData\\CustomData.bin"));
            will(returnValue(StringUtil.EMPTY));
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
            allowing(fileUtils).readFile(new File(drive + "\\AzureData\\CustomData.bin"));
            will(returnValue("data"));

            allowing(fileUtils).listFiles(new File(drive + "\\WindowsAzure\\Config"));
            will(returnValue(new File[]{}));
        }});

        WindowsConfigReader configReader = new WindowsConfigReader(agentConfiguration, idleShutdown, fileUtils);
        configReader.process();

        m.assertIsSatisfied();
    }
}
