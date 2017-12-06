package jetbrains.buildServer.clouds.azure;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import org.jetbrains.annotations.NotNull;

public class WindowsCustomDataReader extends AzureCustomDataReader {

    private static final Logger LOG = Logger.getInstance(WindowsCustomDataReader.class.getName());
    private static final String SYSTEM_DRIVE = System.getenv("SystemDrive");
    private static final String WINDOWS_CUSTOM_DATA_FILE = SYSTEM_DRIVE + "\\AzureData\\CustomData.bin";


    public WindowsCustomDataReader(@NotNull final BuildAgentConfigurationEx agentConfiguration,
                                   @NotNull final IdleShutdown idleShutdown,
                                   @NotNull final FileUtils fileUtils) {
        super(agentConfiguration, idleShutdown, fileUtils);
    }

    @Override
    protected String getCustomDataFileName() {
        return WINDOWS_CUSTOM_DATA_FILE;
    }

    @Override
    protected void parseCustomData(String customData) {
        // Process custom data
        try {
            processCustomData(customData);
        } catch (Exception e) {
            LOG.warnAndDebugDetails(String.format(UNABLE_TO_READ_CUSTOM_DATA_FILE, getCustomDataFileName()), e);
        }
    }
}
