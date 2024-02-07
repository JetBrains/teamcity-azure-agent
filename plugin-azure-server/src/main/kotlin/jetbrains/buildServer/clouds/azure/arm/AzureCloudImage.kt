

package jetbrains.buildServer.clouds.azure.arm

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.CanStartNewInstanceResult
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
        return AzureCloudInstance(this, realInstance).apply {
            hasVmInstance = true
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

    override fun canStartNewInstance(): CanStartNewInstanceResult {
        if (activeInstances.size >= myImageDetails.maxInstances) {
            return CanStartNewInstanceResult.no("Quota exceeded: ActiveInstances count reached MaxInstances value of ${myImageDetails.maxInstances}")
        }
        // Check Azure CPU quota state
        azureCpuQuotaExceeded?.let { instances ->
            if (instances == getInstanceIds()) {
                return CanStartNewInstanceResult.no("Azure CPU quota exceeded")
            } else {
                azureCpuQuotaExceeded = null
                LOG.info("Azure CPU quota limit has been reset due to change in the number of active instances for image ${imageDetails.sourceId}.")
            }
        }
        if (imageDetails.deployTarget == AzureCloudDeployTarget.Instance && stoppedInstances.isEmpty()) {
            return CanStartNewInstanceResult.no("Stopped Instances pool is empty, but we trying to run yet another instance. Check inactive agents for errors")
        }
        return CanStartNewInstanceResult.yes()
    }

    override fun startNewInstance(userData: CloudInstanceUserData) = runBlocking {
        if (!canStartNewInstance().isPositive) {
            throw QuotaException("Unable to start more instances. Limit has reached")
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
                LOG.info("Creating new virtual machine ${instance.describe()}")
                myApiConnector.createInstance(instance, data)
                instance.hasVmInstance = true
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)
                handleDeploymentError(e)

                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))

                if (TeamCityProperties.getBooleanOrTrue(AzureConstants.PROP_DEPLOYMENT_DELETE_FAILED)) {
                    LOG.info("Removing allocated resources for virtual machine ${instance.describe()}")
                    try {
                        myApiConnector.deleteInstance(instance)
                        LOG.info("Allocated resources for virtual machine ${instance.describe()} have been removed")
                    } catch (e: Throwable) {
                        val message = "Failed to delete allocated resources for virtual machine ${instance.describe()}: ${e.message}"
                        LOG.warnAndDebugDetails(message, e)
                    }
                } else {
                    LOG.info("Allocated resources for virtual machine ${instance.describe()} would not be deleted. Cleanup them manually.")
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

            var stoppedInstancesCopy = ArrayList(stoppedInstances)
            var instanceToStart : AzureCloudInstance? = null
            while(stoppedInstancesCopy.isNotEmpty()) {
                instanceToStart = stoppedInstancesCopy
                    .firstOrNull {
                        if (isSameImageInstance(it)) {
                            val data = AzureUtils.setVmNameForTag(userData, it.name)
                            return@firstOrNull it.properties[AzureConstants.TAG_DATA_HASH] == getDataHash(data)
                        }
                        return@firstOrNull false
                    }

                if (instanceToStart == null) return@coroutineScope null

                if (instanceToStart.compareAndSetStatus(InstanceStatus.STOPPED, InstanceStatus.SCHEDULED_TO_START)) {
                    LOG.info("Found stopped instance ${instanceToStart.describe()}. Starting it.")
                    break
                } else {
                    LOG.debug("Found stopped instance ${instanceToStart.describe()}. Could not start it. Instance has just been changed.")
                    instanceToStart = null
                }
                stoppedInstancesCopy = ArrayList(stoppedInstances)
            }

            val invalidStoppedInstances = stoppedInstances
                .map {
                    var result: Pair<AzureCloudInstance, String>? = null
                    if (!isSameImageInstance(it)) {
                        result = it to "Remove virtual machine ${it.describe()} due to changes in image source"
                    } else {
                        val data = AzureUtils.setVmNameForTag(userData, it.name)
                        if (it.properties[AzureConstants.TAG_DATA_HASH] != getDataHash(data)) {
                            result = it to "Remove virtual machine ${it.describe()} due to changes in cloud profile"
                        }
                    }
                    result
                }
                .filterNotNull()
                .toList()


            myScope.launch {
                invalidStoppedInstances.forEach { (instance, reason) ->
                    val instanceToRemove = removeInstance(instance.instanceId)
                    if (instanceToRemove != null) {
                        try {
                            LOG.info("Removing virtual machine ${instance.describe()}. Reason: ${reason}")
                            instance.provisioningInProgress = true
                            myApiConnector.deleteInstance(instance)
                        } catch (e: Throwable) {
                            LOG.warnAndDebugDetails(e.message, e)
                            instance.status = InstanceStatus.ERROR
                            instance.updateErrors(TypedCloudErrorInfo.fromException(e))
                            addInstance(instanceToRemove)
                        } finally {
                            instance.provisioningInProgress = false
                        }
                    }
                }

                instanceToStart?.let {
                    try {
                        instanceToStart.provisioningInProgress = true
                        instanceToStart.status = InstanceStatus.STARTING
                        LOG.info("Starting stopped virtual machine ${instanceToStart.describe()}")
                        myApiConnector.startInstance(instanceToStart)
                    } catch (e: Throwable) {
                        LOG.warnAndDebugDetails(e.message, e)
                        handleDeploymentError(e)

                        instanceToStart.status = InstanceStatus.ERROR
                        instanceToStart.updateErrors(TypedCloudErrorInfo.fromException(e))
                    } finally {
                        instanceToStart.provisioningInProgress = false
                    }
                }
            }
            instanceToStart
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
                LOG.info("Starting virtual machine ${instance.describe()}")
                myApiConnector.startInstance(instance)
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
                LOG.info("Restarting virtual machine ${instance.describe()}")
                myApiConnector.restartInstance(instance)
                instance.hasVmInstance = true
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
            LOG.info("Could not stop virtual machine ${instance.describe()} under investigation. To do that remove ${AzureConstants.TAG_INVESTIGATION} tag from it.")
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
                    LOG.info("Removing virtual machine ${instance.describe()} due to cloud image settings")
                    myApiConnector.deleteInstance(instance)
                    instance.status = InstanceStatus.STOPPED
                } else if (!sameVhdImage) {
                    LOG.info("Removing virtual machine ${instance.describe()} due to cloud image retention policy")
                    myApiConnector.deleteInstance(instance)
                    instance.status = InstanceStatus.STOPPED
                } else {
                    LOG.info("Stopping virtual machine ${instance.describe()}")
                    myApiConnector.stopInstance(instance)
                    instance.status = InstanceStatus.STOPPED
                }

                LOG.info("Virtual machine ${instance.describe()} has been successfully terminated")
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
            !instance.properties.containsKey(AzureConstants.TAG_INVESTIGATION) &&
            !instance.provisioningInProgress &&
            instance.hasVmInstance
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
