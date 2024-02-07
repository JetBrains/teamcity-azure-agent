

package jetbrains.buildServer.clouds.azure.arm

import jetbrains.buildServer.clouds.InstanceStatus
import jetbrains.buildServer.clouds.azure.AzureProperties
import jetbrains.buildServer.clouds.base.AbstractCloudInstance
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
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

    internal constructor(image: AzureCloudImage, instance: AbstractInstance) : this(image, instance.name) {
        properties.putAll(instance.properties)
        setInstanceState(
            InstanceState()
                .withStatus(instance.instanceStatus)
                .withStartDate(instance.startDate)
                .withNetworkIdentity(instance.ipAddress)
        )
    }

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
