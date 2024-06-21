package jetbrains.buildServer.clouds.azure.arm.web

import jetbrains.buildServer.BuildProject
import jetbrains.buildServer.serverSide.agentPools.AgentPool
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager
import kotlinx.coroutines.coroutineScope
import org.jdom.Element
import java.lang.reflect.Method
import javax.servlet.http.HttpServletRequest


/**
 * Handles agent pools request.
 */
internal class AgentPoolHandler(private val agentPoolManager: AgentPoolManager) : ResourceHandler {
    override suspend fun handle(request: HttpServletRequest, context: ResourceHandlerContext) = coroutineScope {
        val projectId: String? = request.getParameter("projectId")
        val agentPoolsElement = Element("agentPools")

        for ((id, name) in getAgentPools(agentPoolManager, projectId)) {
            agentPoolsElement.addContent(Element("agentPool").apply {
                setAttribute("id", id)
                text = name
            })
        }

        agentPoolsElement
    }

    private fun getAgentPools(poolManager: AgentPoolManager, projectId: String?): Map<String, String> {
        val pools = linkedMapOf<String, String>()
        if (BuildProject.ROOT_PROJECT_ID != projectId) {
            pools["-2"] = "<Project pool>"
        }

        var agentPools: Collection<AgentPool>? = null
        if (projectId != null && getProjectOwnedAgentPools != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                agentPools = getProjectOwnedAgentPools.invoke(poolManager, projectId) as Collection<AgentPool>
            } catch (ignored: Exception) {
            }
        }

        (agentPools ?: agentPoolManager.allAgentPools).forEach {
            pools[it.agentPoolId.toString()] = it.name
        }

        return pools
    }

    companion object {
        private val getProjectOwnedAgentPools: Method? = try {
            AgentPoolManager::class.java.getMethod("getProjectOwnedAgentPools", String::class.java)
        } catch (ignored: Exception) {
            null
        }
    }
}
