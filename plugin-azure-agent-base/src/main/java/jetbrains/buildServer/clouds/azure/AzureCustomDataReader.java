package jetbrains.buildServer.clouds.azure;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.BuildAgentConfigurationEx;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public abstract class AzureCustomDataReader {

    private static final Logger LOG = Logger.getInstance(AzureCustomDataReader.class.getName());
    private final BuildAgentConfigurationEx myAgentConfiguration;
    private final IdleShutdown myIdleShutdown;

    protected static final String CUSTOM_DATA_FILE_IS_EMPTY = "Azure custom data file %s is empty";
    protected static final String UNABLE_TO_READ_CUSTOM_DATA_FILE = "Unable to read azure custom data file %s: will use existing parameters";

    public AzureCustomDataReader(@NotNull final BuildAgentConfigurationEx agentConfiguration,
                                 @NotNull final IdleShutdown idleShutdown) {
        myAgentConfiguration = agentConfiguration;
        myIdleShutdown = idleShutdown;
    }

    abstract void process();

    protected void processCustomData(@NotNull final String serializedCustomData) {
        final CloudInstanceUserData data = CloudInstanceUserData.deserialize(serializedCustomData);
        if (data == null) {
            LOG.info(String.format("Unable to deserialize customData: '%s'", serializedCustomData));
            return;
        }

        final String serverAddress = data.getServerAddress();
        LOG.info("Set server URL to " + serverAddress);
        myAgentConfiguration.setServerUrl(serverAddress);

        final String agentName = data.getAgentName();
        if (!StringUtil.isEmptyOrSpaces(agentName)) {
            LOG.info("Set azure instance name " + agentName);
            myAgentConfiguration.setName(agentName);
            myAgentConfiguration.addConfigurationParameter(AzurePropertiesNames.INSTANCE_NAME, agentName);
        }

        if (data.getIdleTimeout() == null) {
            LOG.debug("Idle timeout in custom data is null");
        } else {
            LOG.info("Set idle timeout to " + data.getIdleTimeout());
            myIdleShutdown.setIdleTime(data.getIdleTimeout());
        }

        final Map<String, String> customParams = data.getCustomAgentConfigurationParameters();
        for (String key : customParams.keySet()) {
            final String value = customParams.get(key);
            myAgentConfiguration.addConfigurationParameter(key, value);
            LOG.info(String.format("Added config parameter: {%s, %s}", key, value));
        }
    }
}
