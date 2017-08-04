package jetbrains.buildServer.clouds.azure;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class WindowsCustomDataReader extends AzureCustomDataReader {

    private static final Logger LOG = Logger.getInstance(WindowsCustomDataReader.class.getName());
    private static final String SYSTEM_DRIVE = System.getenv("SystemDrive");
    private static final String WINDOWS_CUSTOM_DATA_FILE = SYSTEM_DRIVE + "\\AzureData\\CustomData.bin";
    private final FileUtils myFileUtils;

    public WindowsCustomDataReader(@NotNull final BuildAgentConfigurationEx agentConfiguration,
                                   @NotNull final IdleShutdown idleShutdown,
                                   @NotNull final FileUtils fileUtils) {
        super(agentConfiguration, idleShutdown);
        myFileUtils = fileUtils;
    }

    @Override
    void process() {
        final File customDataFile = new File(WINDOWS_CUSTOM_DATA_FILE);
        final String customData = myFileUtils.readFile(customDataFile);
        if (StringUtil.isEmpty(customData)) {
            LOG.info(String.format(CUSTOM_DATA_FILE_IS_EMPTY, customDataFile));
        } else {
            // Process custom data
            try {
                processCustomData(customData);
            } catch (Exception e) {
                LOG.warnAndDebugDetails(String.format(UNABLE_TO_READ_CUSTOM_DATA_FILE, customDataFile), e);
            }
        }
    }
}
