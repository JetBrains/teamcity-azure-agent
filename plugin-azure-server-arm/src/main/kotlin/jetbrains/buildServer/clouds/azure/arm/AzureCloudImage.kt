/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.InstanceStatus
import jetbrains.buildServer.clouds.QuotaException
import jetbrains.buildServer.clouds.azure.AzureUtils
import jetbrains.buildServer.clouds.azure.IdProvider
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.connector.AzureInstance
import jetbrains.buildServer.clouds.base.AbstractCloudImage
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async

/**
 * Azure cloud image.
 */
class AzureCloudImage constructor(private val myImageDetails: AzureCloudImageDetails,
                                  private val myApiConnector: AzureApiConnector,
                                  private val myIdProvider: IdProvider)
    : AbstractCloudImage<AzureCloudInstance, AzureCloudImageDetails>(myImageDetails.sourceName, myImageDetails.sourceName) {
    init {
        try {
            val realInstances = myApiConnector.fetchInstances<AzureInstance>(this)
            for (azureInstance in realInstances.values) {
                val instance = createInstanceFromReal(azureInstance)
                instance.status = azureInstance.instanceStatus
                myInstances.put(instance.instanceId, instance)
            }
        } catch (e: CheckedCloudException) {
            val message = String.format("Failed to get instances for image %s: %s", name, e.message)
            LOG.warnAndDebugDetails(message, e)
            updateErrors(TypedCloudErrorInfo.fromException(e))
        }
    }

    override fun getImageDetails(): AzureCloudImageDetails {
        return myImageDetails
    }

    override fun createInstanceFromReal(realInstance: AbstractInstance): AzureCloudInstance {
        return AzureCloudInstance(this, realInstance.name)
    }

    override fun canStartNewInstance(): Boolean {
        return activeInstances.size < myImageDetails.maxInstances
    }

    override fun startNewInstance(userData: CloudInstanceUserData): AzureCloudInstance {
        if (!canStartNewInstance()) {
            throw QuotaException("Unable to start more instances. Limit reached")
        }

        val instance = if (!myImageDetails.behaviour.isDeleteAfterStop) tryToStartStoppedInstance() else null
        return instance ?: startInstance(userData)
    }

    /**
     * Creates a new virtual machine.
     *
     * @param userData info about server.
     * @return created instance.
     */
    private fun startInstance(userData: CloudInstanceUserData): AzureCloudInstance {
        val name = myImageDetails.vmNamePrefix.toLowerCase() + myIdProvider.nextId
        val instance = AzureCloudInstance(this, name)
        instance.status = InstanceStatus.SCHEDULED_TO_START
        val data = AzureUtils.setVmNameForTag(userData, name)

        async(CommonPool) {
            try {
                myApiConnector.createVmAsync(instance, data).await()
                LOG.info("Virtual machine $name has been successfully created")
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)

                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))

                LOG.info("Removing allocated resources for virtual machine " + name)
                try {
                    myApiConnector.deleteVmAsync(instance).await()
                    LOG.info("Allocated resources for virtual machine $name have been removed")
                } catch (e: Throwable) {
                    val message = "Failed to delete allocated resources for virtual machine $name: ${e.message}"
                    LOG.warnAndDebugDetails(message, e)
                }
            }
        }

        myInstances.put(instance.instanceId, instance)

        return instance
    }

    /**
     * Tries to find and start stopped instance.
     *
     * @return instance if it found.
     */
    private fun tryToStartStoppedInstance(): AzureCloudInstance? {
        val instances = stoppedInstances
        if (instances.isNotEmpty()) {
            val instance = instances[0]
            instance.status = InstanceStatus.SCHEDULED_TO_START

            async(CommonPool) {
                try {
                    myApiConnector.startVmAsync(instance).await()
                    LOG.info(String.format("Virtual machine %s has been successfully started", instance.name))
                } catch (e: Throwable) {
                    LOG.warnAndDebugDetails(e.message, e)
                    instance.status = InstanceStatus.ERROR
                    instance.updateErrors(TypedCloudErrorInfo.fromException(e))
                }
            }

            return instance
        }

        return null
    }

    override fun restartInstance(instance: AzureCloudInstance) {
        instance.status = InstanceStatus.RESTARTING

        async(CommonPool) {
            try {
                myApiConnector.restartVmAsync(instance).await()
                LOG.info(String.format("Virtual machine %s has been successfully restarted", instance.name))
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)
                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))
            }
        }
    }

    override fun terminateInstance(instance: AzureCloudInstance) {
        instance.status = InstanceStatus.SCHEDULED_TO_STOP

        async(CommonPool) {
            try {
                if (myImageDetails.behaviour.isDeleteAfterStop) {
                    myApiConnector.deleteVmAsync(instance).await()
                } else {
                    myApiConnector.stopVmAsync(instance).await()
                }

                instance.status = InstanceStatus.STOPPED

                if (myImageDetails.behaviour.isDeleteAfterStop) {
                    myInstances.remove(instance.instanceId)
                }

                LOG.info(String.format("Virtual machine %s has been successfully stopped", instance.name))
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)
                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))
            }
        }
    }

    override fun getAgentPoolId(): Int? {
        return myImageDetails.agentPoolId
    }

    /**
     * Returns active instances.
     *
     * @return instances.
     */
    private val activeInstances: List<AzureCloudInstance>
        get() = myInstances.values.filter { instance -> instance.status.isStartingOrStarted }

    /**
     * Returns stopped instances.
     *
     * @return instances.
     */
    private val stoppedInstances: List<AzureCloudInstance>
        get() = myInstances.values.filter { instance -> instance.status == InstanceStatus.STOPPED }

    companion object {
        private val LOG = Logger.getInstance(AzureCloudImage::class.java.name)
    }
}
