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

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.CloudException
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.InstanceStatus
import jetbrains.buildServer.clouds.QuotaException
import jetbrains.buildServer.clouds.azure.AzureUtils
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.connector.AzureInstance
import jetbrains.buildServer.clouds.azure.arm.types.AzureContainerHandler
import jetbrains.buildServer.clouds.azure.arm.types.AzureHandler
import jetbrains.buildServer.clouds.azure.arm.types.AzureImageHandler
import jetbrains.buildServer.clouds.azure.arm.types.AzureInstanceHandler
import jetbrains.buildServer.clouds.azure.arm.types.AzureTemplateHandler
import jetbrains.buildServer.clouds.azure.arm.types.AzureVhdHandler
import jetbrains.buildServer.clouds.base.AbstractCloudImage
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.serverSide.TeamCityProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Azure cloud image.
 */
class AzureCloudImage(
    private val myImageDetails: AzureCloudImageDetails,
    private val myApiConnector: AzureApiConnector,
    private val myScope: CoroutineScope
) : AbstractCloudImage<AzureCloudInstance, AzureCloudImageDetails>(myImageDetails.sourceId, myImageDetails.sourceId) {

    private val myImageHandlers = mapOf(
        AzureCloudImageType.Vhd to AzureVhdHandler(myApiConnector),
        AzureCloudImageType.Image to AzureImageHandler(myApiConnector),
        AzureCloudImageType.GalleryImage to AzureImageHandler(myApiConnector),
        AzureCloudImageType.Template to AzureTemplateHandler(myApiConnector),
        AzureCloudImageType.Container to AzureContainerHandler(myApiConnector)
    )
    private val myInstanceHandler = AzureInstanceHandler(myApiConnector)

    private val myActiveStatuses = setOf(
            InstanceStatus.SCHEDULED_TO_START,
            InstanceStatus.STARTING,
            InstanceStatus.RUNNING,
            InstanceStatus.RESTARTING,
            InstanceStatus.SCHEDULED_TO_STOP,
            InstanceStatus.STOPPING,
            InstanceStatus.ERROR
    )

    private var azureCpuQuotaExceeded: Set<String>? = null

    private val overlimitInstanceToDelete = AtomicReference<AzureCloudInstance?>(null)

    override fun getImageDetails(): AzureCloudImageDetails = myImageDetails

    override fun createInstanceFromReal(realInstance: AbstractInstance): AzureCloudInstance {
        return AzureCloudInstance(this, realInstance.name).apply {
            properties = realInstance.properties
        }
    }

    override fun detectNewInstances(realInstances: MutableMap<String, out AbstractInstance>?) {
        super.detectNewInstances(realInstances)
        if (realInstances == null) {
            return
        }

        // Update properties
        instances.forEach { instance ->
            realInstances[instance.instanceId]?.let {
                instance.properties = it.properties
            }
        }
    }

    override fun canStartNewInstance(): Boolean {
        if (activeInstances.size >= myImageDetails.maxInstances) {
            LOG.warn("ActiveInstances count reached MaxInstances value: ${myImageDetails.maxInstances}")
            return false
        }
        // Check Azure CPU quota state
        azureCpuQuotaExceeded?.let { instances ->
            if (instances == getInstanceIds()) {
                LOG.warn("Azure CPU quota exceeded at some point. Lookup logs above for detailed info message.")
                return false
            } else {
                azureCpuQuotaExceeded = null
                LOG.info("Azure CPU quota limit has been reset due to change in the number of active instances for image ${imageDetails.sourceId}.")
            }
        }
        if (imageDetails.deployTarget == AzureCloudDeployTarget.Instance && stoppedInstances.isEmpty()) {
            LOG.warn("Stopped Instances pool is empty, but we trying to run yet another instance. Check inactive agents for errors.")
            return false
        }
        return true
    }

    override fun startNewInstance(userData: CloudInstanceUserData) = runBlocking {
        if (!canStartNewInstance()) {
            throw QuotaException("Unable to start more instances. Limit has reached. See WARN message above for details.")
        }

        val instance = if (myImageDetails.deployTarget == AzureCloudDeployTarget.Instance) {
            startStoppedInstance()
        } else {
            tryToStartStoppedInstance(userData) ?: createInstance(userData)
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
        val instance = createAzureCloudInstance()
        val data = AzureUtils.setVmNameForTag(userData, instance.name)
        val image = this

        myScope.launch {
            val hash = handler!!.getImageHash(imageDetails)

            instance.properties[AzureConstants.TAG_PROFILE] = userData.profileId
            instance.properties[AzureConstants.TAG_SOURCE] = imageDetails.sourceId
            instance.properties[AzureConstants.TAG_DATA_HASH] = getDataHash(data)
            instance.properties[AzureConstants.TAG_IMAGE_HASH] = hash
            parseCustomTags(myImageDetails.customTags).forEach {
                instance.properties[it.first] = it.second
            }

            try {
                instance.provisioningInProgress = true
                instance.status = InstanceStatus.STARTING
                LOG.info("Creating new virtual machine ${instance.name}")
                myApiConnector.createInstance(instance, data)
                updateInstanceStatus(image, instance)
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)
                handleDeploymentError(e)

                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))

                if (TeamCityProperties.getBooleanOrTrue(AzureConstants.PROP_DEPLOYMENT_DELETE_FAILED)) {
                    LOG.info("Removing allocated resources for virtual machine ${instance.name}")
                    try {
                        myApiConnector.deleteInstance(instance)
                        LOG.info("Allocated resources for virtual machine ${instance.name} have been removed")
                    } catch (e: Throwable) {
                        val message = "Failed to delete allocated resources for virtual machine ${instance.name}: ${e.message}"
                        LOG.warnAndDebugDetails(message, e)
                    }
                } else {
                    LOG.info("Allocated resources for virtual machine ${instance.name} would not be deleted. Cleanup them manually.")
                }
            } finally {
                instance.provisioningInProgress = false
            }
        }
        return instance
    }

    private fun createAzureCloudInstance(): AzureCloudInstance {
        do {
            val instance = AzureCloudInstance(this, getInstanceName())
            instance.status = InstanceStatus.SCHEDULED_TO_START

            if (addInstanceIfAbsent(instance)) {
                while(activeInstances.size > myImageDetails.maxInstances) {
                    instances.filter { it.status == InstanceStatus.SCHEDULED_TO_START }.maxByOrNull { it.name }
                        ?: throw QuotaException("Unable to start more instances. Limit has reached")

                    if (overlimitInstanceToDelete.compareAndSet(null, instance)) {
                        removeInstance(instance.instanceId)
                        overlimitInstanceToDelete.set(null)
                        throw QuotaException("Unable to start more instances. Limit has reached")
                    }
                }
                return instance
            }
        } while (true)
    }

    /**
     * Tries to find and start stopped instance.
     *
     * @return instance if it found.
     */
    private suspend fun tryToStartStoppedInstance(userData: CloudInstanceUserData): AzureCloudInstance? {
        val image = this
        return coroutineScope {
            if (myImageDetails.behaviour.isDeleteAfterStop && myImageDetails.spotVm != true) return@coroutineScope null
            if (stoppedInstances.isEmpty()) return@coroutineScope null

            val instances = stoppedInstances
            val validInstances =
                instances.filter {
                    if (!isSameImageInstance(it)) {
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

            val invalidInstances = instances - validInstances.toSet()
            val instance = validInstances.firstOrNull()

            instance?.status = InstanceStatus.SCHEDULED_TO_START

            myScope.launch {
                invalidInstances.forEach {
                    val instanceToRemove = removeInstance(it.instanceId)
                    if (instanceToRemove != null) {
                        try {
                            LOG.info("Removing virtual machine ${it.name}")
                            it.provisioningInProgress = true
                            myApiConnector.deleteInstance(it)
                        } catch (e: Throwable) {
                            LOG.warnAndDebugDetails(e.message, e)
                            it.status = InstanceStatus.ERROR
                            it.updateErrors(TypedCloudErrorInfo.fromException(e))
                            addInstance(instanceToRemove)
                        } finally {
                            it.provisioningInProgress = false
                        }
                    }
                }

                instance?.let {
                    try {
                        instance.provisioningInProgress = true
                        it.status = InstanceStatus.STARTING
                        LOG.info("Starting stopped virtual machine ${it.name}")
                        myApiConnector.startInstance(it)
                        updateInstanceStatus(image, it)
                    } catch (e: Throwable) {
                        LOG.warnAndDebugDetails(e.message, e)
                        handleDeploymentError(e)

                        it.status = InstanceStatus.ERROR
                        it.updateErrors(TypedCloudErrorInfo.fromException(e))
                    } finally {
                        instance.provisioningInProgress = false
                    }
                }
            }
            return@coroutineScope instance
        }
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

        val image = this
        myScope.launch {
            try {
                instance.provisioningInProgress = true
                instance.status = InstanceStatus.STARTING
                LOG.info("Starting virtual machine ${instance.name}")
                myApiConnector.startInstance(instance)
                updateInstanceStatus(image, instance)
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)
                handleDeploymentError(e)

                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))
            } finally {
                instance.provisioningInProgress = false
            }
        }

        return instance
    }

    private fun updateInstanceStatus(image: AzureCloudImage, instance: AzureCloudInstance) {
        val instances = myApiConnector.fetchInstances<AzureInstance>(image)

        instances[instance.name]?.let { azureInstance ->
            azureInstance.startDate?.let { instance.setStartDate(it) }
            azureInstance.ipAddress?.let { instance.setNetworkIdentify(it) }
            instance.status = azureInstance.instanceStatus
        }
    }

    private suspend fun isSameImageInstance(instance: AzureCloudInstance) = coroutineScope {
        if (imageDetails.deployTarget == AzureCloudDeployTarget.Instance) {
            return@coroutineScope true
        }
        handler?.let {
            val hash = it.getImageHash(imageDetails)
            return@coroutineScope hash == instance.properties[AzureConstants.TAG_IMAGE_HASH]
        }
        false
    }

    private fun getDataHash(userData: CloudInstanceUserData): String {
        val dataHash = StringBuilder(userData.agentName)
                .append(userData.profileId)
                .append(userData.serverAddress)
                .toString()
                .hashCode()
        return Integer.toHexString(dataHash)
    }

    override fun restartInstance(instance: AzureCloudInstance) {
        instance.status = InstanceStatus.RESTARTING

        val image = this
        myScope.launch {
            try {
                instance.provisioningInProgress = true
                instance.status = InstanceStatus.STARTING
                LOG.info("Restarting virtual machine ${instance.name}")
                myApiConnector.restartInstance(instance)
                updateInstanceStatus(image, instance)
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)
                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))
            } finally {
                instance.provisioningInProgress = false
            }
        }
    }

    override fun terminateInstance(instance: AzureCloudInstance) {
        if (instance.properties.containsKey(AzureConstants.TAG_INVESTIGATION)) {
            LOG.info("Could not stop virtual machine ${instance.name} under investigation. To do that remove ${AzureConstants.TAG_INVESTIGATION} tag from it.")
            return
        }

        val image = this
        instance.status = InstanceStatus.SCHEDULED_TO_STOP

        myScope.launch {
            try {
                instance.provisioningInProgress = true
                instance.status = InstanceStatus.STOPPING
                val sameVhdImage = isSameImageInstance(instance)
                if (myImageDetails.behaviour.isDeleteAfterStop) {
                    LOG.info("Removing virtual machine ${instance.name} due to cloud image settings")
                    myApiConnector.deleteInstance(instance)
                    instance.status = InstanceStatus.STOPPED
                } else if (!sameVhdImage) {
                    LOG.info("Removing virtual machine ${instance.name} due to cloud image retention policy")
                    myApiConnector.deleteInstance(instance)
                    instance.status = InstanceStatus.STOPPED
                } else {
                    LOG.info("Stopping virtual machine ${instance.name}")
                    myApiConnector.stopInstance(instance)
                    updateInstanceStatus(image, instance)
                }

                LOG.info("Virtual machine ${instance.name} has been successfully terminated")
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)
                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))
            } finally {
                instance.provisioningInProgress = false
            }
        }
    }

    override fun getAgentPoolId(): Int? = myImageDetails.agentPoolId

    val handler: AzureHandler?
        get() = if (imageDetails.deployTarget == AzureCloudDeployTarget.Instance) {
            myInstanceHandler
        } else {
            myImageHandlers[imageDetails.type]
        }

    private fun getInstanceName(): String {
        val keys = instances.map { it.instanceId.lowercase(Locale.getDefault()) }
        val sourceName = myImageDetails.sourceId.lowercase(Locale.getDefault())
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
        get() = instances.filter { instance ->
            instance.status == InstanceStatus.STOPPED &&
            !instance.properties.containsKey(AzureConstants.TAG_INVESTIGATION)
        }

    private fun handleDeploymentError(e: Throwable) {
        if (AZURE_CPU_QUOTA_EXCEEDED.containsMatchIn(e.message!!)) {
            azureCpuQuotaExceeded = getInstanceIds()
            LOG.info("Exceeded Azure CPU quota limit for image ${imageDetails.sourceId}. Would not start new cloud instances until active instances termination.")
        }
    }

    private fun getInstanceIds() = activeInstances.asSequence().map { it.instanceId }.toSortedSet()

    companion object {
        private val LOG = Logger.getInstance(AzureCloudImage::class.java.name)
        private val AZURE_CPU_QUOTA_EXCEEDED = Regex("Operation results in exceeding quota limits of Core\\. Maximum allowed: \\d+, Current in use: \\d+, Additional requested: \\d+\\.")

        fun parseCustomTags(rawString: String?): List<Pair<String, String>> {
            return if (!rawString.isNullOrEmpty()) {
                rawString.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull {
                    val tag = it
                    val equalsSignIndex = tag.indexOf("=")
                    if (equalsSignIndex >= 1) {
                        Pair(tag.substring(0, equalsSignIndex), tag.substring(equalsSignIndex + 1))
                    } else {
                        null
                    }
                }
            } else {
                Collections.emptyList()
            }
        }
    }
}
