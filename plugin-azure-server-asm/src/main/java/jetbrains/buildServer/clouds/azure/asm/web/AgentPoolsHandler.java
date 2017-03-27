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

package jetbrains.buildServer.clouds.azure.asm.web;

import jetbrains.buildServer.clouds.azure.asm.connector.AzureApiConnector;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * Handles list of images request.
 */
public class AgentPoolsHandler implements ResourceHandler {

    private static final String NAME = "AgentPools";
    private final AgentPoolManager myPoolManager;

    public AgentPoolsHandler(@NotNull final AgentPoolManager poolManager) {
        myPoolManager = poolManager;
    }

    @NotNull
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Promise<Content, Throwable, Void> handle(@NotNull AzureApiConnector connector) {
        final Element agentPools = new Element(NAME);
        for (AgentPool agentPool : myPoolManager.getAllAgentPools()) {
            Element child = new Element("AgentPool");
            child.setAttribute("name", Integer.toString(agentPool.getAgentPoolId()));
            child.setAttribute("label", agentPool.getName());
            agentPools.addContent(child);
        }

        return new DeferredObject<Content, Throwable, Void>().resolve(agentPools);
    }
}
