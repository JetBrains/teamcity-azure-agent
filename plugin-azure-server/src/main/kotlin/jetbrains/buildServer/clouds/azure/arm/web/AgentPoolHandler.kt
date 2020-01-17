/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm.web

import jetbrains.buildServer.BuildProject
import jetbrains.buildServer.serverSide.agentPools.AgentPool
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager
import kotlinx.coroutines.coroutineScope
import org.jdom.Element
import javax.servlet.http.HttpServletRequest
import java.lang.reflect.Method


/**
 * Handles agent pools request.
 */
internal class AgentPoolHandler(private val agentPoolManager: AgentPoolManager) : ResourceHandler {
    override suspend fun handle(request: HttpServletRequest) = coroutineScope {
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
