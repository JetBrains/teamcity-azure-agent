/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm

import jetbrains.buildServer.clouds.InstanceStatus
import jetbrains.buildServer.clouds.azure.AzureProperties
import jetbrains.buildServer.clouds.base.AbstractCloudInstance
import jetbrains.buildServer.serverSide.AgentDescription
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author Sergey.Pak
 * *         Date: 7/31/2014
 * *         Time: 7:15 PM
 */
class AzureCloudInstance internal constructor(image: AzureCloudImage, name: String)
    : AbstractCloudInstance<AzureCloudImage>(image, name, name) {
    private val myHasVmInstance = AtomicBoolean(false)

    var properties: MutableMap<String, String> = HashMap()

    public var hasVmInstance
        get() = myHasVmInstance.get()
        set(value) = myHasVmInstance.set(value)

    override fun canBeCollected(): Boolean {
        if (provisioningInProgress) return false
        if (status == InstanceStatus.SCHEDULED_TO_START) return false
        return statusUpdateTime.getTime() + STATUS_UPDATE_DELAY <= System.currentTimeMillis()
    }

    override fun containsAgent(agent: AgentDescription): Boolean {
        val agentInstanceName = agent.configurationParameters[AzureProperties.INSTANCE_NAME]
        return name.equals(agentInstanceName, ignoreCase = true)
    }

    private companion object {
        val STATUS_UPDATE_DELAY = 20 * 1000L // 20 sec;
    }
}
