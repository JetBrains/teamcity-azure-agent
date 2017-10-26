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

package jetbrains.buildServer.clouds.azure.arm.connector

import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.AzureEnvironment
import com.microsoft.azure.credentials.ApplicationTokenCredentials
import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.compute.OperatingSystemStateTypes
import com.microsoft.azure.management.compute.VirtualMachine
import com.microsoft.azure.management.resources.DeploymentMode
import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey
import com.microsoft.azure.storage.blob.CloudBlob
import com.microsoft.azure.storage.blob.CloudBlobContainer
import jetbrains.buildServer.clouds.CloudException
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.azure.arm.*
import jetbrains.buildServer.clouds.azure.arm.utils.*
import jetbrains.buildServer.clouds.azure.connector.AzureApiConnectorBase
import jetbrains.buildServer.clouds.azure.utils.AlphaNumericStringComparator
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.serverSide.TeamCityProperties
import kotlinx.coroutines.experimental.*
import org.apache.commons.codec.binary.Base64
import java.net.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import kotlin.collections.HashMap
import kotlin.concurrent.withLock

/**
 * Provides azure arm management capabilities.
 */
class AzureApiConnectorImpl(tenantId: String, clientId: String, secret: String, environment: String?)
    : AzureApiConnectorBase<AzureCloudImage, AzureCloudInstance>(), AzureApiConnector {

    private val LOG = Logger.getInstance(AzureApiConnectorImpl::class.java.name)
    private val RESOURCE_GROUP_PATTERN = Pattern.compile("resourceGroups/(.+)/providers/")
    private val PUBLIC_IP_SUFFIX = "-pip"
    private val PROVISIONING_STATE = "ProvisioningState/"
    private val POWER_STATE = "PowerState/"
    private val NOT_FOUND_ERROR = "Invalid status code 404"
    private val HTTP_PROXY_HOST = "http.proxyHost"
    private val HTTP_PROXY_PORT = "http.proxyPort"
    private val HTTPS_PROXY_HOST = "https.proxyHost"
    private val HTTPS_PROXY_PORT = "https.proxyPort"
    private val HTTP_PROXY_USER = "http.proxyUser"
    private val HTTP_PROXY_PASSWORD = "http.proxyPassword"

    private val myAzure: Azure.Authenticated
    private var mySubscriptionId: String? = null
    private var myServerId: String? = null
    private var myProfileId: String? = null
    private val deploymentLocks = ConcurrentHashMap<String, Lock>()

    init {
        val env = when (environment) {
            "AZURE_CHINA" -> AzureEnvironment.AZURE_CHINA
            "AZURE_GERMANY" -> AzureEnvironment.AZURE_GERMANY
            "AZURE_US_GOVERNMENT" -> AzureEnvironment.AZURE_US_GOVERNMENT
            else -> AzureEnvironment.AZURE
        }

        val credentials = ApplicationTokenCredentials(clientId, tenantId, secret, env)
        myAzure = Azure.configure()
                .configureProxy()
                .authenticate(credentials)
    }

    override fun test() {
        try {
            myAzure.subscriptions().list()
        } catch (e: Exception) {
            val message = "Failed to get list of groups: " + e.message
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    override fun <R : AbstractInstance> fetchInstances(images: Collection<AzureCloudImage>) = runBlocking {
        val imageMap = hashMapOf<AzureCloudImage, Map<String, R>>()

        images.forEach { image ->
            try {
                val result = fetchInstancesAsync(image).await()
                LOG.debug("Received list of image ${image.name} instances")
                @Suppress("UNCHECKED_CAST")
                imageMap.put(image, result as Map<String, R>)
            } catch (e: Throwable) {
                LOG.warn("Failed to receive list of image ${image.name} instances: ${e.message}", e)
                image.updateErrors(TypedCloudErrorInfo.fromException(e))
            }
        }

        imageMap
    }

    private fun fetchInstancesAsync(image: AzureCloudImage) = async(CommonPool) {
        val instances = hashMapOf<String, AzureInstance>()
        val details = image.imageDetails

        val machines: List<VirtualMachine>
        try {
            machines = getVirtualMachinesAsync().await()
        } catch (e: Throwable) {
            val message = "Failed to get list of instances for cloud image ${image.name}: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }

        for (virtualMachine in machines) {
            val name = virtualMachine.name()
            if (!name.startsWith(details.sourceId, true)) {
                LOG.debug("Ignore vm with name " + name)
                continue
            }

            val tags = virtualMachine.tags()
            if (tags == null) {
                LOG.debug("Ignore vm without tags")
                continue
            }

            val serverId = tags[AzureConstants.TAG_SERVER]
            if (!serverId.equals(myServerId, true)) {
                LOG.debug("Ignore vm with invalid server tag " + serverId)
                continue
            }

            val profileId = tags[AzureConstants.TAG_PROFILE]
            if (!profileId.equals(myProfileId, true)) {
                LOG.debug("Ignore vm with invalid profile tag " + profileId)
                continue
            }

            val sourceName = tags[AzureConstants.TAG_SOURCE]
            if (!sourceName.equals(details.sourceId, true)) {
                LOG.debug("Ignore vm with invalid source tag " + sourceName)
                continue
            }

            val instance = AzureInstance(name)
            instance.properties = tags
            instances.put(name, instance)
        }

        val exceptions = arrayListOf<Throwable>()
        instances.values.forEach { instance ->
            try {
                getInstanceDataAsync(instance, details).await()
            } catch (e: Throwable) {
                LOG.debug("Failed to receive vm data: " + e.message, e)
                exceptions.add(e)
            }
        }

        val errors = exceptions.map { TypedCloudErrorInfo.fromException(it) }
                .toTypedArray()

        image.updateErrors(*errors)

        instances
    }

    private fun getVirtualMachinesAsync() = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val list = myAzure.withSubscription(mySubscriptionId)
                    .virtualMachines()
                    .listAsync()
                    .awaitList()
            LOG.debug("Received list of virtual machines")
            list
        } catch (t: Throwable) {
            val message = "Failed to get list of virtual machines: " + t.message
            LOG.debug(message, t)
            throw CloudException(message, t)
        }
    }

    private fun getInstanceDataAsync(instance: AzureInstance, details: AzureCloudImageDetails) = async(CommonPool) {
        val name = instance.name
        val groupId = getResourceGroup(details, name)
        val promises = arrayListOf<Deferred<Unit>>()
        promises += async(CommonPool, CoroutineStart.LAZY) {
            val machine = getVirtualMachineAsync(groupId, name).await() ?: throw CloudException(NOT_FOUND_ERROR)
            LOG.debug("Received virtual machine $name info")

            for (status in machine.instanceView().statuses()) {
                val code = status.code()
                if (code.startsWith(PROVISIONING_STATE)) {
                    instance.setProvisioningState(code.substring(PROVISIONING_STATE.length))
                    val dateTime = status.time()
                    if (dateTime != null) {
                        instance.setStartDate(dateTime.toDate())
                    }
                }

                if (code.startsWith(POWER_STATE)) {
                    instance.setPowerState(code.substring(POWER_STATE.length))
                }
            }
        }

        if (details.vmPublicIp == true && instance.ipAddress == null) {
            val pipName = name + PUBLIC_IP_SUFFIX
            promises += async(CommonPool, CoroutineStart.LAZY) {
                val ip = getPublicIpAsync(groupId, pipName).await()
                LOG.debug("Received public ip $ip for virtual machine $name")

                if (!ip.isNullOrBlank()) {
                    instance.setIpAddress(ip)
                }
            }
        }

        promises.forEach { it.await() }
    }

    private fun getVirtualMachineAsync(groupId: String, name: String) = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val machine: VirtualMachine? = myAzure.withSubscription(mySubscriptionId)
                    .virtualMachines()
                    .getByResourceGroupAsync(groupId, name)
                    .awaitOne()
            machine?.refreshInstanceView()
            LOG.debug("Received virtual machine $name info")
            machine
        } catch (e: Throwable) {
            val message = "Failed to get virtual machine info: " + e.message
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    private fun getPublicIpAsync(groupId: String, name: String) = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val ipAddress = myAzure.withSubscription(mySubscriptionId)
                    .publicIPAddresses()
                    .getByResourceGroupAsync(groupId, name)
                    .awaitOne()
            LOG.debug("Received public ip $ipAddress for $name")
            ipAddress.ipAddress()
        } catch (e: Throwable) {
            val message = "Failed to get public ip address $name info: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    @Suppress("UselessCallOnNotNull")
    override fun checkImage(image: AzureCloudImage) = runBlocking {
        val exceptions = image.handler.checkImageAsync(image).await()
        exceptions.map { TypedCloudErrorInfo.fromException(it) }
                .toTypedArray()
    }

    override fun checkInstance(instance: AzureCloudInstance): Array<TypedCloudErrorInfo> = emptyArray()

    /**
     * Gets a list of resource groups.
     * @return list of images.
     */
    override fun getResourceGroupsAsync() = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val list = myAzure.withSubscription(mySubscriptionId)
                    .resourceGroups()
                    .listAsync()
                    .awaitList()
            LOG.debug("Received list of resource groups")

            list.sortedBy { it.name() }
                    .associateTo(LinkedHashMap<String, String>(), {
                        it.name() to it.regionName()
                    })
        } catch (e: Throwable) {
            val message = "Failed to get list of resource groups: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Gets an image name.
     * @return image name.
     */
    override fun getImageNameAsync(imageId: String) = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val image = myAzure.withSubscription(mySubscriptionId)
                    .virtualMachineCustomImages()
                    .getByIdAsync(imageId)
                    .awaitOne()
            LOG.debug("Received image $imageId")

            image.name()
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
    override fun getImagesAsync(region: String) = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val list = myAzure.withSubscription(mySubscriptionId)
                    .virtualMachineCustomImages()
                    .listAsync()
                    .awaitList()
                    .filter {
                        it.regionName() == region && it.osDiskImage().osState() == OperatingSystemStateTypes.GENERALIZED
                    }
            LOG.debug("Received list of images")

            list.sortedBy { it.name() }
                    .associateTo(LinkedHashMap<String, List<String>>(), {
                        it.id() to listOf(it.name(), it.osDiskImage().osType().toString())
                    })
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
    override fun getVmSizesAsync(region: String) = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val vmSizes = myAzure.withSubscription(mySubscriptionId)
                    .virtualMachines()
                    .sizes()
                    .listByRegionAsync(region)
                    .awaitList()
            LOG.debug("Received list of vm sizes in region " + region)
            val comparator = AlphaNumericStringComparator()
            vmSizes.map { it.name() }.sortedWith(comparator)
        } catch (e: Throwable) {
            val message = "Failed to get list of vm sizes in region $region: ${e.message}"
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
    override fun createVmAsync(instance: AzureCloudInstance, userData: CloudInstanceUserData) = async(CommonPool) {
        val name = instance.name
        val customData: String
        try {
            customData = Base64.encodeBase64String(userData.serialize().toByteArray(charset("UTF-8")))
        } catch (e: Exception) {
            val message = "Failed to encode custom data for instance $name: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }

        instance.properties[AzureConstants.TAG_SERVER] = myServerId!!

        val handler = instance.image.handler
        val builder = handler.prepareBuilderAsync(instance).await()
                .setCustomData(customData)
                .setTags(instance.properties)

        val details = instance.image.imageDetails
        val groupId = when (details.target) {
            AzureCloudDeployTarget.NewGroup -> {
                createResourceGroupAsync(name, details.region!!).await()
                name
            }
            AzureCloudDeployTarget.SpecificGroup -> details.groupId!!
        }

        builder.logDetails()
        val template = builder.toString()
        val parameters = builder.serializeParameters()

        createDeploymentAsync(groupId, name, template, parameters).await()
    }

    private fun createResourceGroupAsync(groupId: String, region: String) = async(CommonPool) {
        try {
            myAzure.withSubscription(mySubscriptionId).resourceGroups().define(groupId)
                    .withRegion(region)
                    .createAsync()
                    .awaitOne()
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

    private fun createDeploymentAsync(groupId: String, deploymentId: String, template: String, params: String) = async(CommonPool) {
        try {
            myAzure.withSubscription(mySubscriptionId).deployments().define(deploymentId)
                    .withExistingResourceGroup(groupId)
                    .withTemplate(template)
                    .withParameters(params)
                    .withMode(DeploymentMode.INCREMENTAL)
                    .createAsync()
                    .awaitOne()
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

    /**
     * Deletes a cloud instance.
     *
     * @param instance is a cloud instance.
     * @return promise.
     */
    override fun deleteVmAsync(instance: AzureCloudInstance) = async(CommonPool) {
        val details = instance.image.imageDetails
        val name = instance.name
        val groupId = getResourceGroup(details, name)

        val virtualMachine = getVirtualMachineAsync(groupId, name).await()

        when (details.target) {
            AzureCloudDeployTarget.NewGroup -> deleteResourceGroupAsync(name).await()
            AzureCloudDeployTarget.SpecificGroup -> deleteDeploymentAsync(groupId, name).await()
        }

        // Remove OS disk
        virtualMachine?.let {
            if (!it.isManagedDiskEnabled) {
                try {
                    val osDisk = URI(it.osUnmanagedDiskVhdUri())
                    deleteBlobsAsync(osDisk, details.region!!).await()
                } catch (e: Throwable) {
                    LOG.infoAndDebugDetails("Failed to delete disk ${it.osUnmanagedDiskVhdUri()} for instance $name", e)
                }
            }
        }
    }

    override fun deleteVmBlobsAsync(instance: AzureCloudInstance) = async(CommonPool) {
        val name = instance.name
        val details = instance.image.imageDetails
        val url = details.imageUrl
        val storageBlobs: URI
        try {
            val imageUrl = URI(url)
            storageBlobs = URI(imageUrl.scheme, imageUrl.host, "/vhds/" + name, null)
        } catch (e: URISyntaxException) {
            val message = "Failed to parse VHD image URL $url for instance $name: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }

        val region = details.region!!
        deleteBlobsAsync(storageBlobs, region).await()
    }

    private suspend fun deleteBlobsAsync(storageBlobs: URI, region: String) = async(CommonPool) {
        val blobs = getBlobsAsync(storageBlobs, region).await()
        for (blob in blobs) {
            try {
                blob.deleteIfExists()
            } catch (e: Exception) {
                val message = "Failed to delete blob ${blob.uri}: ${e.message}"
                LOG.warnAndDebugDetails(message, e)
            }
        }
    }

    private fun deleteResourceGroupAsync(groupId: String) = async(CommonPool) {
        try {
            myAzure.withSubscription(mySubscriptionId)
                    .resourceGroups()
                    .deleteByNameAsync(groupId)
                    .awaitOne()
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

    private fun deleteDeploymentAsync(groupId: String, deploymentId: String) = async(CommonPool) {
        deploymentLocks.getOrPut("$groupId/$deploymentId", { -> ReentrantLock() }).withLock {
            try {
                val subscription = myAzure.withSubscription(mySubscriptionId)
                val deployment = try {
                    subscription.deployments().getByResourceGroupAsync(groupId, deploymentId).awaitOne()
                } catch (e: Exception) {
                    LOG.debug("Deployment $deploymentId in group $groupId was not found", e)
                    return@async
                }

                LOG.debug("Deleting deployment $deploymentId in group $groupId")

                if (deployment.provisioningState() == "Canceled") {
                    LOG.debug("Deployment $deploymentId in group $groupId was canceled")
                }

                // We need to cancel running deployments
                if (deployment.provisioningState() == "Running") {
                    LOG.debug("Canceling running deployment $deploymentId")
                    deployment.cancelAsync().await()
                    do {
                        deployment.refreshAsync().awaitOne()
                    } while (deployment.provisioningState() != "Canceled")
                }

                val operations = deployment.deploymentOperations().listAsync().awaitList()
                if (operations.isNotEmpty()) {
                    operations.sortedByDescending { it.timestamp() }.forEach {
                        runBlocking {
                            it.targetResource()?.let {
                                val resourceId = it.id()
                                val resources = linkedSetOf<String>(resourceId)
                                if (resourceId.contains("Microsoft.Compute/virtualMachines")) {
                                    val virtualMachine = subscription.virtualMachines().getByIdAsync(resourceId).awaitOne()
                                    if (virtualMachine.isManagedDiskEnabled) {
                                        resources.add(virtualMachine.osDiskId())
                                    }
                                }

                                resources.forEach {
                                    try {
                                        LOG.debug("Deleting resource $it")
                                        subscription.genericResources().deleteByIdAsync(it).await()
                                    } catch (e: com.microsoft.azure.CloudException) {
                                        val details = AzureUtils.getExceptionDetails(e)
                                        val message = "Failed to delete resource $it in $deploymentId in group $groupId: $details"
                                        LOG.warnAndDebugDetails(message, e)
                                    } catch (e: Exception) {
                                        LOG.warnAndDebugDetails("Failed to delete resource $it in $deploymentId in group $groupId", e)
                                    }
                                }
                            }
                        }
                    }
                }

                subscription.deployments().deleteByResourceGroupAsync(groupId, deploymentId).await()
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

    /**
     * Restarts an instance.
     *
     * @param instance is a cloud instance.
     * @return promise.
     */
    override fun restartVmAsync(instance: AzureCloudInstance) = async(CommonPool, CoroutineStart.LAZY) {
        val name = instance.name
        val groupId = getResourceGroup(instance.image.imageDetails, name)

        try {
            myAzure.withSubscription(mySubscriptionId)
                    .virtualMachines()
                    .getByResourceGroupAsync(groupId, name)
                    .awaitOne()
                    .restartAsync()
                    .awaitOne()
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

    override fun startVmAsync(instance: AzureCloudInstance) = async(CommonPool, CoroutineStart.LAZY) {
        val name = instance.name
        val groupId = getResourceGroup(instance.image.imageDetails, name)

        try {
            myAzure.withSubscription(mySubscriptionId)
                    .virtualMachines()
                    .getByResourceGroupAsync(groupId, name)
                    .awaitOne()
                    .startAsync()
                    .awaitOne()
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

    override fun stopVmAsync(instance: AzureCloudInstance) = async(CommonPool, CoroutineStart.LAZY) {
        val name = instance.name
        val groupId = getResourceGroup(instance.image.imageDetails, name)

        try {
            myAzure.withSubscription(mySubscriptionId)
                    .virtualMachines()
                    .getByResourceGroupAsync(groupId, name)
                    .awaitOne()
                    .deallocateAsync()
                    .awaitOne()
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
    override fun getVhdOsTypeAsync(imageUrl: String, region: String) = async(CommonPool) {
        val metadata = getVhdMetadataAsync(imageUrl, region).await()
        when (metadata) {
            null -> null
            else -> {
                if ("OSDisk" != metadata["MicrosoftAzureCompute_ImageType"]) {
                    val message = "Found blob $imageUrl with invalid OSDisk metadata"
                    LOG.debug(message)
                    return@async null
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
    override fun getVhdMetadataAsync(imageUrl: String, region: String) = async(CommonPool) {
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
            blobs = getBlobsAsync(uri, region).await()
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
            LOG.debug("Found more than one blobs for url " + imageUrl)
            return@async null
        }

        val blob = blobs[0]
        if (!imageUrl.endsWith(blob.name, true)) {
            val message = "For url $imageUrl found blob with invalid name ${blob.name}"
            LOG.debug(message)
            return@async null
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

    private fun getBlobsAsync(uri: URI, region: String) = async(CommonPool, CoroutineStart.LAZY) {
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

        val account = getStorageAccountAsync(storage, region).await()
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

    private fun getStorageAccountAsync(storage: String, region: String) = async(CommonPool) {
        val accounts = getStorageAccountsAsync().await()
        val account = accounts.firstOrNull({ storage.equals(it.name(), true) })

        if (account == null) {
            val message = "Storage account $storage not found"
            LOG.debug(message)
            throw CloudException(message)
        }

        if (!account.regionName().equals(region, true)) {
            val message = "VHD image should be located in storage account in the $region region"
            LOG.debug(message)
            throw CloudException(message)
        }

        val groupMatcher = RESOURCE_GROUP_PATTERN.matcher(account.id())
        if (!groupMatcher.find()) {
            val message = "Invalid storage account identifier " + account.id()
            LOG.debug(message)
            throw CloudException(message)
        }

        val keys = getStorageAccountKeysAsync(groupMatcher.group(1), storage).await()
        try {
            CloudStorageAccount(StorageCredentialsAccountAndKey(storage, keys[0].value()))
        } catch (e: URISyntaxException) {
            val message = "Invalid storage account $storage credentials: ${e.message}"
            LOG.debug(message)
            throw CloudException(message, e)
        }
    }

    private fun getStorageAccountsAsync() = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val accounts = myAzure.withSubscription(mySubscriptionId)
                    .storageAccounts()
                    .listAsync()
                    .awaitList()
            LOG.debug("Received list of storage accounts")
            accounts
        } catch (e: Throwable) {
            val message = "Failed to get list of storage accounts: " + e.message
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    private fun getStorageAccountKeysAsync(groupName: String, storageName: String) = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val account = myAzure.withSubscription(mySubscriptionId)
                    .storageAccounts()
                    .getByResourceGroupAsync(groupName, storageName)
                    .awaitOne()
            LOG.debug("Received keys for storage account " + storageName)
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
    override fun getSubscriptionsAsync() = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val list = myAzure.subscriptions().listAsync().awaitList()
            LOG.debug("Received list of subscriptions")

            val subscriptions = LinkedHashMap<String, String>()
            Collections.sort(list) { o1, o2 -> o1.displayName().compareTo(o2.displayName()) }

            for (subscription in list) {
                subscriptions.put(subscription.subscriptionId(), subscription.displayName())
            }

            subscriptions
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
    override fun getRegionsAsync() = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val list = myAzure.subscriptions().getByIdAsync(mySubscriptionId).awaitOne().listLocations()
            LOG.debug("Received list of regions in subscription " + mySubscriptionId)

            val regions = LinkedHashMap<String, String>()
            Collections.sort(list) { o1, o2 -> o1.displayName().compareTo(o2.displayName()) }

            for (region in list) {
                regions.put(region.name(), region.displayName())
            }

            regions
        } catch (e: Throwable) {
            val message = "Failed to get list of regions in subscription $mySubscriptionId: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Gets a list of networks.
     *
     * @return list of networks.
     */
    override fun getNetworksAsync(region: String) = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val list = myAzure.withSubscription(mySubscriptionId)
                    .networks()
                    .listAsync()
                    .awaitList()
            LOG.debug("Received list of networks")
            val networks = LinkedHashMap<String, List<String>>()
            for (network in list) {
                if (!network.regionName().equals(region, ignoreCase = true)) continue

                val subNetworks = ArrayList(network.subnets().keys)
                networks.put(network.id(), subNetworks)
            }

            networks
        } catch (e: Throwable) {
            val message = "Failed to get list of networks: " + e.message
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

    /**
     * Sets subscription identifier for ARM clients.
     *
     * @param subscriptionId is a an identifier.
     */
    fun setSubscriptionId(subscriptionId: String) {
        mySubscriptionId = subscriptionId
    }

    /**
     * Configures http proxy settings.
     */
    private fun Azure.Configurable.configureProxy(): Azure.Configurable {
        val builder = StringBuilder()

        // Set HTTP proxy
        val httpProxyHost = TeamCityProperties.getProperty(HTTP_PROXY_HOST)
        val httpProxyPort = TeamCityProperties.getInteger(HTTP_PROXY_PORT, 80)
        if (httpProxyHost.isNotBlank()) {
            this.withProxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(httpProxyHost, httpProxyPort)))
            builder.append("$httpProxyHost:$httpProxyPort")
        }

        // Set HTTPS proxy
        val httpsProxyHost = TeamCityProperties.getProperty(HTTPS_PROXY_HOST)
        val httpsProxyPort = TeamCityProperties.getInteger(HTTPS_PROXY_PORT, 443)
        if (httpsProxyHost.isNotBlank()) {
            this.withProxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(httpsProxyHost, httpsProxyPort)))
            builder.setLength(0)
            builder.append("$httpsProxyHost:$httpsProxyPort")
        }

        // Set proxy authentication
        val httpProxyUser = TeamCityProperties.getProperty(HTTP_PROXY_USER)
        val httpProxyPassword = TeamCityProperties.getProperty(HTTP_PROXY_PASSWORD)
        if (httpProxyUser.isNotBlank()) {
            val authenticator = CredentialsAuthenticator(httpProxyUser, httpProxyPassword)
            this.withProxyAuthenticator(authenticator)
            builder.insert(0, "$httpProxyUser@")
        }

        if (builder.isNotEmpty()) {
            LOG.debug("Using proxy server $builder for connection")
        }

        return this
    }

    private fun getResourceGroup(details: AzureCloudImageDetails, instanceName: String): String {
        return when (details.target) {
            AzureCloudDeployTarget.NewGroup -> instanceName
            AzureCloudDeployTarget.SpecificGroup -> details.groupId!!
        }
    }
}
