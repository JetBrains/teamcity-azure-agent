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

package jetbrains.buildServer.clouds.azure.arm

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.CloudException
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.InstanceStatus
import jetbrains.buildServer.clouds.QuotaException
import jetbrains.buildServer.clouds.azure.AzureUtils
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.types.*
import jetbrains.buildServer.clouds.base.AbstractCloudImage
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import java.lang.StringBuilder
import java.util.*

/**
 * Azure cloud image.
 */
class AzureCloudImage constructor(private val myImageDetails: AzureCloudImageDetails,
                                  private val myApiConnector: AzureApiConnector)
    : AbstractCloudImage<AzureCloudInstance, AzureCloudImageDetails>(myImageDetails.sourceId, myImageDetails.sourceId) {

    private val myImageHandlers = mapOf(
            AzureCloudImageType.Vhd to AzureVhdHandler(myApiConnector),
            AzureCloudImageType.Image to AzureImageHandler(myApiConnector),
            AzureCloudImageType.Template to AzureTemplateHandler(),
            AzureCloudImageType.Container to AzureContainerHandler()
    )

    private val myActiveStatuses = setOf(
            InstanceStatus.SCHEDULED_TO_START,
            InstanceStatus.STARTING,
            InstanceStatus.RUNNING,
            InstanceStatus.RESTARTING,
            InstanceStatus.SCHEDULED_TO_STOP,
            InstanceStatus.STOPPING,
            InstanceStatus.ERROR
    )

    override fun getImageDetails(): AzureCloudImageDetails = myImageDetails

    override fun createInstanceFromReal(realInstance: AbstractInstance): AzureCloudInstance {
        return AzureCloudInstance(this, realInstance.name).apply {
            properties = realInstance.properties
        }
    }

    override fun canStartNewInstance(): Boolean = activeInstances.size < myImageDetails.maxInstances

    override fun startNewInstance(userData: CloudInstanceUserData): AzureCloudInstance = runBlocking {
        if (!canStartNewInstance()) {
            throw QuotaException("Unable to start more instances. Limit has reached")
        }

        val instance = if (myImageDetails.deployTarget == AzureCloudDeployTarget.Instance) {
            startStoppedInstance()
        } else {
            tryToStartStoppedInstanceAsync(userData).await() ?: createInstance(userData)
        }
        instance.apply {
            setStartDate(Date())
        }
    }

    /**
     * Creates a new virtual machine.
     *
     * @param userData info about server.
     * @return created instance.
     */
    private fun createInstance(userData: CloudInstanceUserData): AzureCloudInstance {
        val name = getInstanceName()
        val instance = AzureCloudInstance(this, name)
        instance.status = InstanceStatus.SCHEDULED_TO_START
        val data = AzureUtils.setVmNameForTag(userData, name)

        async(CommonPool) {
            val hash = handler!!.getImageHashAsync(imageDetails).await()

            instance.properties[AzureConstants.TAG_PROFILE] = userData.profileId
            instance.properties[AzureConstants.TAG_SOURCE] = imageDetails.sourceId
            instance.properties[AzureConstants.TAG_DATA_HASH] = getDataHash(data)
            instance.properties[AzureConstants.TAG_IMAGE_HASH] = hash

            try {
                LOG.info("Creating new virtual machine ${instance.name}")
                myApiConnector.createInstanceAsync(instance, data).await()
                instance.status = InstanceStatus.RUNNING
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)

                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))

                LOG.info("Removing allocated resources for virtual machine ${instance.name}")
                try {
                    myApiConnector.deleteInstanceAsync(instance).await()
                    LOG.info("Allocated resources for virtual machine ${instance.name} have been removed")
                    removeInstance(instance.instanceId)
                } catch (e: Throwable) {
                    val message = "Failed to delete allocated resources for virtual machine ${instance.name}: ${e.message}"
                    LOG.warnAndDebugDetails(message, e)
                }
            }
        }

        addInstance(instance)

        return instance
    }

    /**
     * Tries to find and start stopped instance.
     *
     * @return instance if it found.
     */
    private fun tryToStartStoppedInstanceAsync(userData: CloudInstanceUserData) = async(CommonPool) {
        val instances = stoppedInstances
        if (instances.isNotEmpty()) {
            val validInstances = if (myImageDetails.behaviour.isDeleteAfterStop) {
                LOG.info("Will remove all virtual machines due to cloud image settings")
                emptyList()
            } else {
                instances.filter {
                    if (!isSameImageInstance(it).await()) {
                        LOG.info("Will remove virtual machine ${it.name} due to changes in image source")
                        return@filter false
                    }
                    val data = AzureUtils.setVmNameForTag(userData, it.name)
                    if (it.properties[AzureConstants.TAG_DATA_HASH] != getDataHash(data)) {
                        LOG.info("Will remove virtual machine ${it.name} due to changes in cloud profile")
                        return@filter false
                    }
                    return@filter true
                }
            }

            val invalidInstances = instances - validInstances
            val instance = validInstances.firstOrNull()

            instance?.status = InstanceStatus.SCHEDULED_TO_START

            async(CommonPool) {
                invalidInstances.forEach {
                    try {
                        LOG.info("Removing virtual machine ${it.name}")
                        myApiConnector.deleteInstanceAsync(it).await()
                        removeInstance(it.instanceId)
                    } catch (e: Throwable) {
                        LOG.warnAndDebugDetails(e.message, e)
                        it.status = InstanceStatus.ERROR
                        it.updateErrors(TypedCloudErrorInfo.fromException(e))
                    }
                }

                instance?.let {
                    try {
                        LOG.info("Starting stopped virtual machine ${it.name}")
                        myApiConnector.startInstanceAsync(it).await()
                        instance.status = InstanceStatus.RUNNING
                    } catch (e: Throwable) {
                        LOG.warnAndDebugDetails(e.message, e)
                        it.status = InstanceStatus.ERROR
                        it.updateErrors(TypedCloudErrorInfo.fromException(e))
                    }
                }
            }

            return@async instance
        }

        null
    }

    /**
     * Starts stopped instance.
     *
     * @return instance.
     */
    private fun startStoppedInstance(): AzureCloudInstance {
        val instance = stoppedInstances.singleOrNull()
                ?: throw CloudException("Instance ${imageDetails.vmNamePrefix ?: imageDetails.sourceId} was not found")

        instance.status = InstanceStatus.SCHEDULED_TO_START

        async(CommonPool) {
            try {
                LOG.info("Starting virtual machine ${instance.name}")
                myApiConnector.startInstanceAsync(instance).await()
                instance.status = InstanceStatus.RUNNING
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)
                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))
            }
        }

        return instance
    }

    private fun isSameImageInstance(instance: AzureCloudInstance) = async(CommonPool) {
        handler?.let {
            val hash = it.getImageHashAsync(imageDetails).await()
            return@async hash == instance.properties[AzureConstants.TAG_IMAGE_HASH]
        }
        return@async true
    }

    private fun getDataHash(userData: CloudInstanceUserData): String {
        val dataHash = StringBuilder(userData.agentName)
                .append(userData.idleTimeout)
                .append(userData.profileId)
                .append(userData.serverAddress)
                .toString()
                .hashCode()
        return Integer.toHexString(dataHash)
    }

    override fun restartInstance(instance: AzureCloudInstance) {
        instance.status = InstanceStatus.RESTARTING

        async(CommonPool) {
            try {
                LOG.info("Restarting virtual machine ${instance.name}")
                myApiConnector.restartInstanceAsync(instance).await()
                instance.status = InstanceStatus.RUNNING
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
                val sameVhdImage = isSameImageInstance(instance).await()
                if (myImageDetails.behaviour.isDeleteAfterStop) {
                    LOG.info("Removing virtual machine ${instance.name} due to cloud image settings")
                    myApiConnector.deleteInstanceAsync(instance).await()
                    removeInstance(instance.instanceId)
                } else if (!sameVhdImage) {
                    LOG.info("Removing virtual machine ${instance.name} due to cloud image retention policy")
                    myApiConnector.deleteInstanceAsync(instance).await()
                    removeInstance(instance.instanceId)
                } else {
                    LOG.info("Stopping virtual machine ${instance.name}")
                    myApiConnector.stopInstanceAsync(instance).await()
                    instance.status = InstanceStatus.STOPPED
                }

                LOG.info("Virtual machine ${instance.name} has been successfully terminated")
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)
                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))
            }
        }
    }

    override fun getAgentPoolId(): Int? = myImageDetails.agentPoolId

    val handler: AzureHandler?
        get() = if (imageDetails.deployTarget == AzureCloudDeployTarget.Instance) {
            null
        } else {
            myImageHandlers[imageDetails.type]
        }

    private fun getInstanceName(): String {
        val keys = instances.map { it.instanceId.toLowerCase() }
        val sourceName = myImageDetails.sourceId.toLowerCase()
        var i = 1

        while (keys.contains(sourceName + i)) i++

        return sourceName + i
    }

    /**
     * Returns active instances.
     *
     * @return instances.
     */
    private val activeInstances: List<AzureCloudInstance>
        get() = instances.filter { instance -> myActiveStatuses.contains(instance.status) }

    /**
     * Returns stopped instances.
     *
     * @return instances.
     */
    private val stoppedInstances: List<AzureCloudInstance>
        get() = instances.filter { instance -> instance.status == InstanceStatus.STOPPED }

    companion object {
        private val LOG = Logger.getInstance(AzureCloudImage::class.java.name)
    }
}
