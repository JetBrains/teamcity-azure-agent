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

package jetbrains.buildServer.clouds.azure.arm.connector

import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.management.compute.OperatingSystemStateTypes
import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey
import com.microsoft.azure.storage.blob.CloudBlob
import com.microsoft.azure.storage.blob.CloudBlobContainer
import jetbrains.buildServer.clouds.CloudException
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.azure.AzureCompress
import jetbrains.buildServer.clouds.azure.AzureProperties
import jetbrains.buildServer.clouds.azure.arm.*
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.*
import jetbrains.buildServer.clouds.azure.arm.throttler.*
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import jetbrains.buildServer.clouds.azure.arm.utils.awaitOne
import jetbrains.buildServer.clouds.azure.arm.utils.isVmInstance
import jetbrains.buildServer.clouds.azure.connector.AzureApiConnectorBase
import jetbrains.buildServer.clouds.azure.utils.AlphaNumericStringComparator
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.codec.binary.Base64
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides azure arm management capabilities.
 */
@Suppress("UNUSED_DESTRUCTURED_PARAMETER_ENTRY")
class AzureApiConnectorImpl(params: Map<String, String>)
    : AzureApiConnectorBase<AzureCloudImage, AzureCloudInstance>(), AzureApiConnector {

    private var myAzureRequestsThrottler: AzureRequestThrottler
    private var myServerId: String? = null
    private var myProfileId: String? = null
    private val deploymentLocks = ConcurrentHashMap<String, Mutex>()

    init {
        myAzureRequestsThrottler = AzureRequestThrottlerCache.getOrCreateThrottler(params)
    }

    override fun test() = runBlocking {
        try {
            withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeReadTaskWithTimeout(AzureThrottlerReadTasks.FetchSubscriptions, Unit).awaitOne()
            }
            Unit
        } catch (e: ThrottlerExecutionTaskException) {
            Unit
        } catch (e: Exception) {
            val message = "Failed to get list of groups: " + e.message
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    override fun <R : AbstractInstance> fetchInstances(images: Collection<AzureCloudImage>) = runBlocking {
        val imageMap = hashMapOf<AzureCloudImage, Map<String, R>>()

        val parameter = FetchInstancesTaskParameter(
                myServerId,
                myProfileId,
                images.map {
                    val imageDetails = it.imageDetails
                    FetchInstancesTaskImageDescriptor(it.id,
                            FetchInstancesTaskCloudImageDetails(
                                    imageDetails.vmPublicIp,
                                    imageDetails.instanceId,
                                    imageDetails.sourceId,
                                    imageDetails.target,
                                    imageDetails.isVmInstance(),
                                    getResourceGroup(imageDetails, "")))
                }.sortedBy { it.imageId }.toTypedArray())

        try {
            val instanceDescriptorMap = withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeReadTaskWithTimeout(AzureThrottlerReadTasks.FetchInstances, parameter)
                        .awaitOne()
                        .groupBy { it.imageId }
            }

            LOG.debug("Received list of instances")
            for (image in images) {
                val map = hashMapOf<String, AbstractInstance>()

                @Suppress("UNCHECKED_CAST")
                imageMap[image] = map as Map<String, R>

                val errors = arrayListOf<TypedCloudErrorInfo>()
                for (instanceDescriptor in instanceDescriptorMap.getOrDefault(image.id, emptyList())) {
                    val instance = AzureInstance(instanceDescriptor.name)
                    instance.properties = instanceDescriptor.tags
                    instanceDescriptor.powerState?.let { instance.setPowerState(it) }
                    instanceDescriptor.provisioningState?.let { instance.setProvisioningState(it) }
                    instanceDescriptor.startDate?.let { instance.setStartDate(it) }
                    instanceDescriptor.publicIpAddress?.let { instance.setIpAddress(it) }

                    map[instance.name] = instance

                    instanceDescriptor.error?.let { errors.add(it) }
                }
                image.updateErrors(*errors.toTypedArray())
            }
        } catch (e: ThrottlerExecutionTaskException) {
        } catch (t: Throwable) {
            val message = "Failed to get list of virtual machines: " + t.message
            LOG.debug(message, t)
            throw CheckedCloudException(message, t)
        }
        imageMap
    }


    @Suppress("UselessCallOnNotNull")
    override fun checkImage(image: AzureCloudImage) = runBlocking {
        image.handler?.let { handler ->
            return@runBlocking handler.checkImage(image)
                    .map { TypedCloudErrorInfo.fromException(it) }
                    .toTypedArray()
        }
        return@runBlocking emptyArray<TypedCloudErrorInfo>()
    }

    override fun checkInstance(instance: AzureCloudInstance): Array<TypedCloudErrorInfo> = emptyArray()

    /**
     * Gets a list of resource groups.
     * @return list of resource groups.
     */
    override suspend fun getResourceGroups() = coroutineScope {
        try {
            val resourceGroupMap =
                myAzureRequestsThrottler.executeReadTaskWithTimeout(AzureThrottlerReadTasks.FetchResourceGroups, Unit)
                        .awaitOne()

            LOG.debug("Received map of resource groups")

            resourceGroupMap
        } catch (e: ThrottlerExecutionTaskException) {
            throw e
        } catch (e: Throwable) {
            val message = "Failed to get list of resource groups: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Gets a list of vm instances.
     * @return list of instances.
     */
    override suspend fun getInstances() = coroutineScope {
        try {
            val vmInstanceMap = withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeReadTask(AzureThrottlerReadTasks.FetchVirtualMachines, Unit)
                        .awaitOne()
                        .asSequence().map {
                            it.id to "${it.groupName}/${it.name}".toLowerCase()
                        }.sortedBy {
                            it.second
                        }.associateBy(
                                { it.first },
                                { it.second }
                        )
            }
            LOG.debug("Received list of vm instances")
            vmInstanceMap
        } catch (e: Throwable) {
            val message = "Failed to get list of instances: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Checks whether virtual machine exists.
     */
    override suspend fun hasInstance(id: String): Boolean {
        return getInstances().containsKey(id)
    }

    /**
     * Gets an image name.
     * @return image name.
     */
    override suspend fun getImageName(imageId: String): String = coroutineScope {
        try {
            val images = withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeReadTaskWithTimeout(AzureThrottlerReadTasks.FetchCustomImages, Unit)
                        .awaitOne()
            }
            LOG.debug("Received image $imageId")

            val image = images.first { it.id.equals(imageId, ignoreCase = true) }
            image.name
        } catch (e: ThrottlerExecutionTaskException) {
            throw e
        } catch (e: Throwable) {
            val message = "Failed to get image $imageId: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Gets a list of images.
     * @return list of images.
     */
    override suspend fun getImages(region: String) = coroutineScope {
        try {
            val list = withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeReadTaskWithTimeout(AzureThrottlerReadTasks.FetchCustomImages, Unit)
                        .awaitOne()
            }
            LOG.debug("Received list of images")

            list.asSequence()
                    .filter { it.regionName.equals(region, ignoreCase = true) }
                    .filter { it.osState == OperatingSystemStateTypes.GENERALIZED }
                    .toList()
                    .sortedBy { it.name }
                    .associateBy(
                            { it.id },
                            { listOf(it.name, it.osType.toString()) }
                    )
        } catch (e: ThrottlerExecutionTaskException) {
            throw e
        } catch (e: Throwable) {
            val message = "Failed to get list of images: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Gets a list of VM sizes.

     * @return list of sizes.
     */
    override suspend fun getVmSizes(region: String) = coroutineScope {
        try {
            val vmSizes = withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeReadTask(AzureThrottlerReadTasks.FetchVirtualMachineSizes, region)
                        .awaitOne()
            }
            LOG.debug("Received list of vm sizes in region $region")
            val comparator = AlphaNumericStringComparator()
            vmSizes.asSequence().sortedWith(comparator).toList()
        } catch (e: Throwable) {
            val message = "Failed to get list of vm sizes in region $region: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Gets a list of storage accounts.
     * @return list of sizes.
     */
    override suspend fun getStorageAccounts(region: String) = coroutineScope {
        try {
            val storageAccounts = withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeReadTask(AzureThrottlerReadTasks.FetchStorageAccounts, Unit)
                        .awaitOne()
                        .filter { region.equals(it.regionName, ignoreCase = true) }
                        .filter { !it.skuTypeName.contains("premium", ignoreCase = true) }
                        .toList()
            }
            LOG.debug("Received list of storage accounts in region $region")
            val comparator = AlphaNumericStringComparator()
            storageAccounts.asSequence().map { it.name }.sortedWith(comparator).toList()
        } catch (e: Throwable) {
            val message = "Failed to get list of storage accounts in region $region: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Creates a new cloud instance.
     * @param instance is a cloud instance.
     * @param userData is a custom data.
     * @return promise.
     */
    override suspend fun createInstance(instance: AzureCloudInstance, userData: CloudInstanceUserData) = coroutineScope {
        instance.properties[AzureConstants.TAG_SERVER] = myServerId!!
        if (!instance.image.imageDetails.isVmInstance()) {
            createContainer(instance, userData)
        } else {
            createVm(instance, userData)
        }


    }

    override fun isSuspended(): Boolean {
        return myAzureRequestsThrottler.isUpdateOperationSuspended() || myAzureRequestsThrottler.isReadOperationSuspended()
    }

    private suspend fun createVm(instance: AzureCloudInstance, userData: CloudInstanceUserData) = coroutineScope {
        val name = instance.name
        val customData = encodeCustomData(userData, name)

        val handler = instance.image.handler
        val builder = handler!!.prepareBuilder(instance)
                .setCustomData(customData)
                .setTags(VM_RESOURCE_NAME, instance.properties)

        val details = instance.image.imageDetails
        val groupId = when (details.target) {
            AzureCloudDeployTarget.NewGroup -> {
                createResourceGroup(name, details.region!!)
                name
            }
            AzureCloudDeployTarget.SpecificGroup -> details.groupId!!
            else -> throw CloudException("Creating virtual machine $name is prohibited")
        }

        builder.logDetails()
        val template = builder.toString()
        val parameters = builder.serializeParameters()

        createDeployment(groupId, name, template, parameters)
    }

    private suspend fun createContainer(instance: AzureCloudInstance, userData: CloudInstanceUserData) = coroutineScope {
        val name = instance.name

        val handler = instance.image.handler
        val builder = handler!!.prepareBuilder(instance)

        val details = instance.image.imageDetails
        val groupId = when (details.target) {
            AzureCloudDeployTarget.NewGroup -> {
                createResourceGroup(details.sourceId, details.region!!)
                details.sourceId
            }
            AzureCloudDeployTarget.SpecificGroup -> details.groupId!!
            else -> throw CloudException("Creating container $name is prohibited")
        }

        if ("Linux" == details.osType && details.storageAccount != null) {
            addContainerCustomData(instance, userData, builder)
        } else {
            addContainerEnvironment(instance, userData, builder)
        }

        builder.setParameterValue(AzureConstants.TEAMCITY_URL, userData.serverAddress)
                .setTags(CONTAINER_RESOURCE_NAME, instance.properties)
                .logDetails()

        val template = builder.toString()
        val parameters = builder.serializeParameters()

        createDeployment(groupId, name, template, parameters)
    }

    private suspend fun addContainerCustomData(instance: AzureCloudInstance, userData: CloudInstanceUserData, builder: ArmTemplateBuilder) = coroutineScope {
        val name = instance.name
        val imageDetails = instance.image.imageDetails

        val customData = encodeCustomData(userData, name)
        val fileContent = AzureUtils.getResourceAsString("/templates/ovf-env.xml")
                .format(customData)
                .toByteArray(Charsets.UTF_8)

        // Set storage account credentials
        val account = getStorageAccount(imageDetails.storageAccount!!, imageDetails.region!!)
        val credentials = account.credentials as StorageCredentialsAccountAndKey

        val fileClient = account.createCloudFileClient()
        val fileShare = fileClient.getShareReference(name)
        fileShare.createIfNotExists()

        val customDataFile = fileShare.rootDirectoryReference.getFileReference("ovf-env.xml")
        customDataFile.uploadFromByteArray(fileContent, 0, fileContent.size)

        AzureConstants.CONTAINER_VOLUMES.forEach {
            fileClient.getShareReference("$name-$it").createIfNotExists()
        }

        builder.addContainerVolumes(CONTAINER_RESOURCE_NAME, name)
                .setParameterValue("storageAccountName", credentials.accountName)
                .setParameterValue("storageAccountKey", credentials.exportBase64EncodedKey())
    }

    private fun addContainerEnvironment(instance: AzureCloudInstance, userData: CloudInstanceUserData, builder: ArmTemplateBuilder) {
        val environment = userData.customAgentConfigurationParameters.toMutableMap()
        environment[AzureProperties.INSTANCE_NAME] = instance.name
        val data = AzureCompress.encode(environment)
        builder.addContainerEnvironment(CONTAINER_RESOURCE_NAME, mapOf(AzureProperties.INSTANCE_ENV_VAR to data))
    }

    private fun encodeCustomData(userData: CloudInstanceUserData, name: String): String {
        return try {
            Base64.encodeBase64String(userData.serialize().toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            val message = "Failed to encode custom data for instance $name: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    private suspend fun createResourceGroup(groupId: String, region: String) = coroutineScope {
        try {
            withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeUpdateTask(
                        AzureThrottlerActionTasks.CreateResourceGroup,
                        CreateResourceGroupTaskParameter(groupId, region))
                        .awaitOne()
            }
            LOG.debug("Created resource group $groupId in region $region")
        } catch (e: com.microsoft.azure.CloudException) {
            val details = AzureUtils.getExceptionDetails(e)
            val message = "Failed to create resource group $groupId: $details"
            LOG.debug(message, e)
            throw CloudException(message, e)
        } catch (e: Throwable) {
            val message = "Failed to create resource group $groupId: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    private suspend fun createDeployment(groupId: String, deploymentId: String, template: String, params: String) = coroutineScope {
        deploymentLocks.getOrPut("$groupId/$deploymentId") { Mutex() }.withLock {
            try {
                withContext(Dispatchers.IO) {
                    myAzureRequestsThrottler.executeUpdateTask(
                            AzureThrottlerActionTasks.CreateDeployment,
                            CreateDeploymentTaskParameter(groupId, deploymentId, template, params))
                            .awaitOne()
                }
                LOG.debug("Created deployment in group $groupId")
            } catch (e: com.microsoft.azure.CloudException) {
                val details = AzureUtils.getExceptionDetails(e)
                val message = "Failed to create deployment $deploymentId in resource group $groupId: $details"
                LOG.debug(message, e)
                if (!message.endsWith("Canceled")) {
                    throw CloudException(message, e)
                }
            } catch (e: Throwable) {
                val message = "Failed to create deployment $deploymentId in resource group $groupId: ${e.message}"
                LOG.debug(message, e)
                throw CloudException(message, e)
            }
        }
    }

    /**
     * Deletes a cloud instance.
     *
     * @param instance is a cloud instance.
     * @return promise.
     */
    override suspend fun deleteInstance(instance: AzureCloudInstance) = coroutineScope {
        if (instance.image.imageDetails.isVmInstance()) {
            deleteVm(instance)
        } else {
            deleteDeployTarget(instance)
        }
        Unit
    }

    private suspend fun deleteVm(instance: AzureCloudInstance) = coroutineScope {
        val details = instance.image.imageDetails
        val name = instance.name
        val groupId = getResourceGroup(details, name)

        var virtualMachine : FetchVirtualMachinesTaskVirtualMachineDescriptor?
        try {
            virtualMachine = withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeReadTask(AzureThrottlerReadTasks.FetchVirtualMachines, Unit)
                        .awaitOne()
                        .firstOrNull {
                            it.groupName.equals(groupId, ignoreCase = true) && it.name == name
                        }
            }

            LOG.debug("Received virtual machine $name info")
        } catch (e: Throwable) {
            val message = "Failed to get virtual machine info: " + e.message
            LOG.debug(message, e)
            throw CloudException(message, e)
        }

        deleteDeployTarget(instance)

        // Remove OS disk
        virtualMachine?.let {
            if (!it.isManagedDiskEnabled && it.osUnmanagedDiskVhdUri != null) {
                try {
                    val osDisk = URI(it.osUnmanagedDiskVhdUri)
                    deleteBlobs(osDisk, details.region!!)
                } catch (e: Throwable) {
                    LOG.infoAndDebugDetails("Failed to delete disk ${it.osUnmanagedDiskVhdUri} for instance $name", e)
                }
            }
        }
    }

    private suspend fun deleteDeployTarget(instance: AzureCloudInstance) = coroutineScope {
        val details = instance.image.imageDetails
        val name = instance.name
        val groupId = getResourceGroup(details, details.sourceId)
        val isVm = instance.image.imageDetails.isVmInstance()

        when (details.target) {
            AzureCloudDeployTarget.NewGroup -> deleteResourceGroup(name)
            AzureCloudDeployTarget.SpecificGroup -> deleteDeployment(groupId, name)
            AzureCloudDeployTarget.Instance -> throw CloudException(
                    if (isVm) "Deleting virtual machine $name is prohibited" else "Deleting container instance is not supported")
        }
    }

    override suspend fun deleteVmBlobs(instance: AzureCloudInstance) = coroutineScope {
        val name = instance.name
        val details = instance.image.imageDetails
        val url = details.imageUrl
        val storageBlobs: URI
        try {
            val imageUrl = URI(url)
            storageBlobs = URI(imageUrl.scheme, imageUrl.host, "/vhds/$name", null)
        } catch (e: URISyntaxException) {
            val message = "Failed to parse VHD image URL $url for instance $name: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }

        val region = details.region!!
        deleteBlobs(storageBlobs, region)
    }

    private suspend fun deleteBlobs(storageBlobs: URI, region: String) = coroutineScope {
        val blobs = getBlobs(storageBlobs, region)
        for (blob in blobs) {
            try {
                blob.deleteIfExists()
            } catch (e: Exception) {
                val message = "Failed to delete blob ${blob.uri}: ${e.message}"
                LOG.warnAndDebugDetails(message, e)
            }
        }
    }

    private suspend fun deleteResourceGroup(groupId: String) = coroutineScope {
        try {
            withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeUpdateTask(AzureThrottlerActionTasks.DeleteResourceGroup, groupId)
                        .awaitOne()
            }
            LOG.debug("Resource group $groupId has been successfully deleted")
        } catch (e: com.microsoft.azure.CloudException) {
            val details = AzureUtils.getExceptionDetails(e)
            val message = "Failed to delete resource group $groupId: $details"
            LOG.debug(message, e)
            throw CloudException(message, e)
        } catch (e: Throwable) {
            val message = "Failed to delete resource group $groupId: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    private suspend fun deleteDeployment(groupId: String, deploymentId: String) = coroutineScope {
        deploymentLocks.getOrPut("$groupId/$deploymentId") { Mutex() }.withLock {
            withContext(Dispatchers.IO) {
                try {
                    myAzureRequestsThrottler.executeUpdateTask(AzureThrottlerActionTasks.DeleteDeployment, DeleteDeploymentTaskParameter(groupId, deploymentId))
                            .awaitOne()
                    LOG.debug("Deleted deployment $deploymentId in group $groupId")
                } catch (e: com.microsoft.azure.CloudException) {
                    val details = AzureUtils.getExceptionDetails(e)
                    val message = "Failed to delete deployment $deploymentId in resource group $groupId: $details"
                    LOG.debug(message, e)
                    throw CloudException(message, e)
                } catch (e: Throwable) {
                    val message = "Failed to delete deployment $deploymentId in resource group $groupId: ${e.message}"
                    LOG.debug(message, e)
                    throw CloudException(message, e)
                }
            }
        }
    }

    /**
     * Restarts an instance.
     *
     * @param instance is a cloud instance.
     * @return promise.
     */
    override suspend fun restartInstance(instance: AzureCloudInstance) = coroutineScope {
        if (!instance.image.imageDetails.isVmInstance()) {
            throw CloudException("Restarting container instances is not supported")
        } else {
            restartVm(instance)
        }
    }

    private suspend fun restartVm(instance: AzureCloudInstance) = coroutineScope {
        val name = instance.name
        val groupId = getResourceGroup(instance.image.imageDetails, name)

        try {
            withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeUpdateTask(AzureThrottlerActionTasks.RestartVirtualMachine, RestartVirtualMachineTaskParameter(groupId, name))
                        .awaitOne()
            }
            LOG.debug("Virtual machine $name has been successfully restarted")
        } catch (e: com.microsoft.azure.CloudException) {
            val details = AzureUtils.getExceptionDetails(e)
            val message = "Failed to restart virtual machine $name: $details"
            LOG.debug(message, e)
            throw CloudException(message, e)
        } catch (e: Throwable) {
            val message = "Failed to restart virtual machine $name: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Starts an instance.
     *
     * @param instance is a cloud instance.
     * @return promise.
     */
    override suspend fun startInstance(instance: AzureCloudInstance) = coroutineScope {
        if (!instance.image.imageDetails.isVmInstance()) {
            throw CloudException("Starting container instances is not supported")
        } else {
            startVm(instance)
        }
    }

    private suspend fun startVm(instance: AzureCloudInstance) = coroutineScope {
        val name = instance.name
        val groupId = getResourceGroup(instance.image.imageDetails, name)

        try {
            withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeUpdateTask(AzureThrottlerActionTasks.StartVirtualMachine, StartVirtualMachineTaskParameter(groupId, name))
                        .awaitOne()
            }
            LOG.debug("Virtual machine $name has been successfully started")
        } catch (e: com.microsoft.azure.CloudException) {
            val details = AzureUtils.getExceptionDetails(e)
            val message = "Failed to start virtual machine $name: $details"
            LOG.debug(message, e)
            throw CloudException(message, e)
        } catch (e: Throwable) {
            val message = "Failed to start virtual machine $name: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Stops an instance.
     *
     * @param instance is a cloud instance.
     * @return promise.
     */
    override suspend fun stopInstance(instance: AzureCloudInstance) = coroutineScope {
        if (!instance.image.imageDetails.isVmInstance()) {
            deleteDeployTarget(instance)
        } else {
            stopVm(instance)
        }
    }

    private suspend fun stopVm(instance: AzureCloudInstance) = coroutineScope {
        val name = instance.name
        val groupId = getResourceGroup(instance.image.imageDetails, name)

        try {
            withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeUpdateTask(
                        AzureThrottlerActionTasks.StopVirtualMachine,
                        StopVirtualMachineTaskParameter(groupId, name))
                        .awaitOne()
            }
            LOG.debug("Virtual machine $name has been successfully stopped")
        } catch (e: com.microsoft.azure.CloudException) {
            val details = AzureUtils.getExceptionDetails(e)
            val message = "Failed to stop virtual machine $name: $details"
            LOG.debug(message, e)
            throw CloudException(message, e)
        } catch (e: Throwable) {
            val message = "Failed to stop virtual machine $name: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Gets an OS type of VHD image.
     * @param imageUrl is image URL.
     * @return OS type (Linux, Windows).
     */
    override suspend fun getVhdOsType(imageUrl: String, region: String) = coroutineScope {
        val metadata = getVhdMetadata(imageUrl, region)
        when (metadata) {
            null -> null
            else -> {
                if ("OSDisk" != metadata["MicrosoftAzureCompute_ImageType"]) {
                    val message = "Found blob $imageUrl with invalid OSDisk metadata"
                    LOG.debug(message)
                    return@coroutineScope null
                }

                if ("Generalized" != metadata["MicrosoftAzureCompute_OSState"]) {
                    LOG.debug("Found blob $imageUrl with invalid Generalized metadata")
                    throw CloudException("VHD image should be generalized.")
                }

                metadata["MicrosoftAzureCompute_OSType"]
            }
        }
    }

    /**
     * Gets VHD image metadata.
     * @param imageUrl is image URL.
     * @return metadata.
     */
    override suspend fun getVhdMetadata(imageUrl: String, region: String) = coroutineScope {
        val uri: URI
        try {
            uri = URI.create(imageUrl)
        } catch (e: Exception) {
            val message = "Invalid image URL $imageUrl: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }

        val blobs: List<CloudBlob>
        try {
            blobs = getBlobs(uri, region)
        } catch (e: Throwable) {
            LOG.debug("Failed to receive blobs for url $imageUrl: ${e.message}")
            throw e
        }

        if (blobs.isEmpty()) {
            val message = "VHD file $imageUrl not found in storage account"
            LOG.debug(message)
            throw CloudException(message)
        }

        if (blobs.size > 1) {
            LOG.debug("Found more than one blobs for url $imageUrl")
            return@coroutineScope null
        }

        val blob = blobs[0]
        if (!imageUrl.endsWith(blob.name, true)) {
            val message = "For url $imageUrl found blob with invalid name ${blob.name}"
            LOG.debug(message)
            return@coroutineScope null
        }

        try {
            blob.downloadAttributes()
        } catch (e: Exception) {
            val message = "Failed to access storage blob: " + e.message
            LOG.debug(message, e)
            throw CloudException(message, e)
        }

        HashMap(blob.metadata).apply {
            this["blobType"] = blob.properties.blobType.name
            this["contentType"] = blob.properties.contentType
            this["etag"] = blob.properties.etag
            this["length"] = blob.properties.length.toString()
            this["contentMD5"] = blob.properties.contentMD5
        }
    }

    private suspend fun getBlobs(uri: URI, region: String) = coroutineScope {
        if (uri.host == null || uri.path == null) {
            throw CloudException("Invalid URL")
        }

        val hostSuffix = uri.host.indexOf(".blob.core.windows.net")
        if (hostSuffix <= 0) {
            throw CloudException("Invalid host name")
        }

        val storage = uri.host.substring(0, hostSuffix)
        val filesPrefix = uri.path
        val slash = filesPrefix.indexOf("/", 1)
        if (slash <= 0) {
            throw CloudException("File path must include container name")
        }

        val account = getStorageAccount(storage, region)
        val containerName = filesPrefix.substring(1, slash)
        val container: CloudBlobContainer

        try {
            container = account.createCloudBlobClient().getContainerReference(containerName)
            container.createIfNotExists()
        } catch (e: Throwable) {
            val message = "Failed to get container $containerName reference in storage account $storage: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }

        val blobName = filesPrefix.substring(slash + 1)
        try {
            container.listBlobs(blobName).mapNotNull { it as CloudBlob }
        } catch (e: Exception) {
            val message = "Failed to list container's $containerName blobs: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    private suspend fun getStorageAccount(storage: String, region: String) = coroutineScope {
        val accounts = getStorageAccounts()
        val account = accounts.firstOrNull { storage.equals(it.name, true) }

        if (account == null) {
            val message = "Storage account $storage not found"
            LOG.debug(message)
            throw CloudException(message)
        }

        if (!account.regionName.equals(region, true)) {
            val message = "VHD image should be located in storage account in the $region region"
            LOG.debug(message)
            throw CloudException(message)
        }

        val result = RESOURCE_GROUP_PATTERN.find(account.id)
        if (result == null) {
            val message = "Invalid storage account identifier " + account.id
            LOG.debug(message)
            throw CloudException(message)
        }

        val (groupId) = result.destructured
        val keys = getStorageAccountKeys(groupId, storage)
        try {
            CloudStorageAccount(StorageCredentialsAccountAndKey(storage, keys[0]), true)
        } catch (e: URISyntaxException) {
            val message = "Invalid storage account $storage credentials: ${e.message}"
            LOG.debug(message)
            throw CloudException(message, e)
        }
    }

    private suspend fun getStorageAccounts() = coroutineScope {
        try {
            val accounts = withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeReadTask(AzureThrottlerReadTasks.FetchStorageAccounts, Unit)
                        .awaitOne()
            }
            LOG.debug("Received list of storage accounts")
            accounts
        } catch (e: Throwable) {
            val message = "Failed to get list of storage accounts: " + e.message
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    private suspend fun getStorageAccountKeys(groupName: String, storageName: String) = coroutineScope {
        try {
            val account = withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeReadTask(AzureThrottlerReadTasks.FetchStorageAccounts, Unit)
                        .awaitOne()
                        .first { x -> x.resourceGroupName.equals(groupName, ignoreCase = true) && x.name == storageName }
            }
            LOG.debug("Received keys for storage account $storageName")
            account.keys
        } catch (e: Throwable) {
            val message = "Failed to get storage account $storageName key: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Gets a list of subscriptions.
     *
     * @return subscriptions.
     */
    override suspend fun getSubscriptions() = coroutineScope {
        try {
            val list = withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeReadTask(AzureThrottlerReadTasks.FetchSubscriptions, Unit)
                        .awaitOne()
            }
            LOG.debug("Received list of subscriptions")

            list.asSequence()
                    .sortedBy { it.displayName }
                    .associateBy(
                            { it.subscriptionId },
                            { it.displayName }
                    )
        } catch (e: Throwable) {
            val message = "Failed to get list of subscriptions: " + AzureUtils.getAuthenticationErrorMessage(e)
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Gets a list of regions.
     *
     * @return regions.
     */
    override suspend fun getRegions() = coroutineScope {
        try {
            val list = withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeReadTask(AzureThrottlerReadTasks.FetchLocations, Unit)
                        .awaitOne()
            }
            LOG.debug("Received list of regions in subscription ${myAzureRequestsThrottler.subscriptionId}")

            list.asSequence()
                    .sortedBy { it.displayName }
                    .associateBy(
                            { it.name },
                            { it.displayName }
                    )
        } catch (e: Throwable) {
            val message = "Failed to get list of regions in subscription ${myAzureRequestsThrottler.subscriptionId}: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Gets a list of networks.
     *
     * @return list of networks.
     */
    override suspend fun getNetworks(region: String) = coroutineScope {
        try {
            val list = withContext(Dispatchers.IO) {
                myAzureRequestsThrottler.executeReadTask(AzureThrottlerReadTasks.FetchNetworks, Unit)
                        .awaitOne()
            }
            LOG.debug("Received list of networks")

            list.asSequence().filter {
                it.regionName.equals(region, ignoreCase = true)
            }.associateBy(
                    { it.id },
                    { it.subnets }
            )
        } catch (e: Throwable) {
            val message = "Failed to get list of networks: " + e.message
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    override suspend fun getServices(region: String): Map<String, Set<String>> = coroutineScope {
        try {
            val result = ConcurrentHashMap<String, Set<String>>()
            withContext(Dispatchers.IO) {
                val services = myAzureRequestsThrottler.executeReadTaskWithTimeout(AzureThrottlerReadTasks.FetchServices, region)
                        .awaitOne()
                for (service in services) {
                    SERVICE_TYPES[service.namespace]?.let {
                        val resourceTypeSet = it.intersect(service.resourceTypes)
                        if (resourceTypeSet.isNotEmpty()) {
                            result[service.namespace] = resourceTypeSet
                        }
                    }
                }
            }
            LOG.debug("Received list of services")

            result
        } catch (e: ThrottlerExecutionTaskException) {
            throw e
        } catch (e: Throwable) {
            val message = "Failed to get list of services: " + e.message
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Sets a server identifier.
     *
     * @param serverId identifier.
     */
    fun setServerId(serverId: String?) {
        myServerId = serverId
    }

    /**
     * Sets a profile identifier.
     *
     * @param profileId identifier.
     */
    fun setProfileId(profileId: String?) {
        myProfileId = profileId
    }

    private fun getResourceGroup(details: AzureCloudImageDetails, instanceName: String): String {
        return when (details.target) {
            AzureCloudDeployTarget.NewGroup -> instanceName
            AzureCloudDeployTarget.SpecificGroup -> details.groupId!!
            AzureCloudDeployTarget.Instance -> {
                RESOURCE_GROUP_PATTERN.find(details.instanceId!!)?.let {
                    val (groupId) = it.destructured
                    return groupId
                }
                throw CloudException("Invalid instance ID ${details.instanceId}")
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(AzureApiConnectorImpl::class.java.name)
        private val RESOURCE_GROUP_PATTERN = Regex("resourceGroups/([^/]+)/providers/")

        private const val CONTAINER_RESOURCE_NAME = "[parameters('containerName')]"
        private const val VM_RESOURCE_NAME = "[parameters('vmName')]"
        private val SERVICE_TYPES = mapOf(
                "Microsoft.ContainerInstance" to listOf("containerGroups"),
                "Microsoft.Compute" to listOf("virtualMachines")
        )
    }
}
