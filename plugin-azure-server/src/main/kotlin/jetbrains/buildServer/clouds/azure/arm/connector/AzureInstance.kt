

package jetbrains.buildServer.clouds.azure.arm.connector

import jetbrains.buildServer.clouds.InstanceStatus
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import java.util.*

/**
 * Azure cloud instance.
 */
class AzureInstance internal constructor(private val myName: String) : AbstractInstance() {
    private var myProvisioningState: String? = null
    private var myStartDate: Date? = null
    private var myPowerState: String? = null
    private var myIpAddress: String? = null
    private var myProperties: Map<String, String> = HashMap()

    override fun getName(): String {
        return myName
    }

    override fun getStartDate(): Date? {
        return myStartDate
    }

    internal fun setStartDate(startDate: Date) {
        myStartDate = startDate
    }

    override fun getIpAddress(): String? {
        return myIpAddress
    }

    internal fun setIpAddress(ipAddress: String) {
        myIpAddress = ipAddress
    }

    override fun getInstanceStatus(): InstanceStatus {
        val provisioningState = myProvisioningState
        if (provisioningState is String) {
            PROVISIONING_STATES[provisioningState]?.let {
                return it
            }
        }

        val powerState = myPowerState
        if (powerState is String) {
            POWER_STATES[powerState]?.let {
                return it
            }
        }

        return InstanceStatus.UNKNOWN
    }

    internal fun setProvisioningState(provisioningState: String) {
        myProvisioningState = provisioningState
    }

    internal fun setPowerState(powerState: String) {
        myPowerState = powerState
    }

    override fun getProperty(name: String): String? {
        return myProperties[name]
    }

    override fun getProperties() = myProperties

    internal fun setProperties(properties: Map<String, String>) {
        myProperties = properties
    }

    companion object {
        private var PROVISIONING_STATES: MutableMap<String, InstanceStatus> = TreeMap(String.CASE_INSENSITIVE_ORDER)
        private var POWER_STATES: MutableMap<String, InstanceStatus> = TreeMap(String.CASE_INSENSITIVE_ORDER)

        init {
            PROVISIONING_STATES["Creating"] = InstanceStatus.STARTING
            PROVISIONING_STATES["Deleting"] = InstanceStatus.STOPPING
            PROVISIONING_STATES["Failed"] = InstanceStatus.ERROR
            PROVISIONING_STATES["Canceled"] = InstanceStatus.ERROR
            PROVISIONING_STATES["Investigation"] = InstanceStatus.STOPPED

            POWER_STATES["Starting"] = InstanceStatus.STARTING
            POWER_STATES["Running"] = InstanceStatus.RUNNING
            POWER_STATES["Restarting"] = InstanceStatus.RESTARTING
            POWER_STATES["Stopping"] = InstanceStatus.STOPPING
            POWER_STATES["Deallocating"] = InstanceStatus.STOPPING
            POWER_STATES["Stopped"] = InstanceStatus.STOPPED
            POWER_STATES["Deallocated"] = InstanceStatus.STOPPED
        }
    }
}
