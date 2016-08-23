package jetbrains.buildServer.clouds.azure;

import com.intellij.openapi.diagnostic.Logger;
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

    private static final Logger LOG = Logger.getInstance(AzureAgentNameChanger.class.getName());
    private static final String AUTHORIZATION_MESSAGE = "Virtual agent is authorized automatically";

    @Override
    public void agentRegistered(@NotNull SBuildAgent agent, long currentlyRunningBuildId) {
        updateAgentName(agent);
    }

    @Override
    public void agentDescriptionUpdated(@NotNull SBuildAgent agent) {
        updateAgentName(agent);
    }

    @Override
    public void agentStatusChanged(@NotNull SBuildAgent agent, boolean wasEnabled, boolean wasAuthorized) {
        updateAgentName(agent);
    }

    private void updateAgentName(@NotNull SBuildAgent agent) {
        if (!(agent instanceof BuildAgentEx)) {
            LOG.warn("Provided agent instance is not a BuildAgentEx implementation: " + agent.getClass().getName());
            return;
        }

        // Check that it's an authorized virtual agent
        final BuildAgentEx buildAgent = (BuildAgentEx) agent;
        final String comment = buildAgent.getAuthorizeComment().getComment();
        final String name = buildAgent.getName();
        if (comment == null || !comment.contains(AUTHORIZATION_MESSAGE)) {
            LOG.debug(String.format("Agent %s is not authorized by cloud integration", name));
            return;
        }

        // Check that it's an azure agent
        final Map<String, String> config = buildAgent.getConfigurationParameters();
        final String agentName = config.get(AzurePropertiesNames.INSTANCE_NAME);
        if (StringUtil.isEmpty(agentName)) {
            LOG.debug(String.format("Agent %s does not have %s parameter", name, AzurePropertiesNames.INSTANCE_NAME));
            return;
        }

        // Check agent name
        if (agentName.equals(name)) {
            return;
        }

        // Set name
        LOG.info(String.format("Set name %s for cloud agent %s", agentName, name));
        buildAgent.setName(agentName);
    }
}
