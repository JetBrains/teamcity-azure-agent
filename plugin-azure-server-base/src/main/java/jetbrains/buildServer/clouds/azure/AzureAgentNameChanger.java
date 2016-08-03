package jetbrains.buildServer.clouds.azure;

import jetbrains.buildServer.serverSide.BuildAgentEx;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildAgent;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Changes name of authorized agents by cloud integration.
 */
public class AzureAgentNameChanger extends BuildServerAdapter {

    private static final String AUTHORIZATION_MESSAGE = "Virtual agent is authorized automatically";

    @Override
    public void agentStatusChanged(@NotNull SBuildAgent agent, boolean wasEnabled, boolean wasAuthorized) {
        if (!(agent instanceof BuildAgentEx)) {
            return;
        }

        // Check that it's an authorized virtual agent
        final String comment = agent.getAuthorizeComment().getComment();
        if (!agent.isAuthorized() || comment == null || !comment.contains(AUTHORIZATION_MESSAGE)) {
            return;
        }

        // Check that it's an azure agent
        final Map<String, String> config = agent.getConfigurationParameters();
        final String agentName = config.get(AzurePropertiesNames.INSTANCE_NAME);
        if (StringUtil.isEmpty(agentName)) {
            return;
        }

        // Set name
        final BuildAgentEx buildAgent = (BuildAgentEx) agent;
        buildAgent.setName(agentName);
    }
}
