/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.microsoft.azure.*
import com.microsoft.azure.credentials.ApplicationTokenCredentials
import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.compute.VirtualMachine
import com.microsoft.azure.management.resources.DeploymentMode
import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey
import com.microsoft.azure.storage.blob.CloudBlob
import com.microsoft.azure.storage.blob.CloudBlobContainer
import jetbrains.buildServer.clouds.CloudException
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.InstanceStatus
import jetbrains.buildServer.clouds.azure.arm.*
import jetbrains.buildServer.clouds.azure.arm.connector.models.JsonValue
import jetbrains.buildServer.clouds.azure.arm.utils.*
import jetbrains.buildServer.clouds.azure.connector.AzureApiConnectorBase
import jetbrains.buildServer.clouds.azure.utils.AlphaNumericStringComparator
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.serverSide.TeamCityProperties
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.future.await
import kotlinx.coroutines.experimental.future.toCompletableFuture
import okhttp3.OkHttpClient
import org.apache.commons.codec.binary.Base64
import retrofit2.Retrofit
import java.net.*
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

/**
 * Provides azure arm management capabilities.
 */
class AzureApiConnectorImpl(tenantId: String,
                            clientId: String,
                            secret: String) : AzureApiConnectorBase<AzureCloudImage, AzureCloudInstance>(), AzureApiConnector {
    private val LOG = Logger.getInstance(AzureApiConnectorImpl::class.java.name)
    private val FAILED_TO_GET_INSTANCE_STATUS_FORMAT = "Failed to get instance %s status: %s"
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
    private val PROVISIONING_STATES = Arrays.asList(
            InstanceStatus.SCHEDULED_TO_START,
            InstanceStatus.SCHEDULED_TO_STOP)

    private val myAzure: Azure.Authenticated
    private var mySubscriptionId: String? = null
    private var myServerId: String? = null
    private var myProfileId: String? = null
    private var myLocation: String? = null

    init {
        val credentials = ApplicationTokenCredentials(clientId, tenantId, secret, AzureEnvironment.AZURE)
        val httpClientBuilder = OkHttpClient.Builder()
        credentials.applyCredentialsFilter(httpClientBuilder)

        val retrofitBuilder = Retrofit.Builder()
        configureProxy(httpClientBuilder)

        val client = RestClient.Builder(httpClientBuilder, retrofitBuilder)
                .withDefaultBaseUrl(credentials.environment).build()
        myAzure = Azure.authenticate(client, credentials.domain)
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

    override fun getInstanceStatusIfExists(instance: AzureCloudInstance): InstanceStatus? {
        val azureInstance = AzureInstance(instance.name)
        val details = instance.image.imageDetails

        val promise = async(CommonPool) {
            try {
                getInstanceDataAsync(azureInstance, details).await()
                val status = azureInstance.instanceStatus
                LOG.debug("Instance ${instance.name} status is $status")
                instance.status = status
                instance.updateErrors()
                status
            } catch (e: Throwable) {
                val cause = e.cause
                val message = String.format(FAILED_TO_GET_INSTANCE_STATUS_FORMAT, instance.name, e.message)
                LOG.debug(message, e)
                if (!(cause != null && NOT_FOUND_ERROR == cause.message || PROVISIONING_STATES.contains(instance.status))) {
                    instance.status = InstanceStatus.ERROR
                    instance.updateErrors(TypedCloudErrorInfo.fromException(e))
                }
                throw e
            }
        }

        try {
            return promise.toCompletableFuture().get()
        } catch (e: Throwable) {
            val message = String.format(FAILED_TO_GET_INSTANCE_STATUS_FORMAT, instance.name, e)
            LOG.debug(message, e)
            instance.updateErrors(TypedCloudErrorInfo.fromException(CloudException(message, e)))
            return null
        }
    }

    override fun <R : AbstractInstance> fetchInstances(images: Collection<AzureCloudImage>): Map<AzureCloudImage, Map<String, R>> {
        val imageMap = hashMapOf<AzureCloudImage, Map<String, R>>()
        val promises = images.map {
            image: AzureCloudImage ->
            async(CommonPool) {
                try {
                    val result = fetchInstancesAsync(image).await()
                    LOG.debug("Received list of image ${image.name} instances")
                    image.updateErrors()
                    @Suppress("UNCHECKED_CAST")
                    imageMap.put(image, result as Map<String, R>)
                } catch (e: Throwable) {
                    LOG.warn("Failed to receive list of image ${image.name} instances: ${e.message}", e)
                    image.updateErrors(TypedCloudErrorInfo.fromException(e))
                }
            }.toCompletableFuture()
        }

        if (promises.isNotEmpty()) {
            try {
                CompletableFuture.allOf(*promises.toTypedArray()).get()
            } catch (e: Throwable) {
                val message = "Failed to get list of images: " + e.message
                LOG.debug(message, e)
                throw CloudException(message, e)
            }
        }

        return imageMap
    }

    private fun fetchInstancesAsync(image: AzureCloudImage) = async(CommonPool) {
        val instances = hashMapOf<String, AzureInstance>()
        val details = image.imageDetails

        val machines: PagedList<VirtualMachine>
        try {
            machines = getVirtualMachinesAsync().await()
        } catch (e: Throwable) {
            val message = "Failed to get list of instances for cloud image ${image.name}: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }

        for (virtualMachine in machines) {
            val name = virtualMachine.name()
            if (!name.startsWith(details.vmNamePrefix, true)) {
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
            if (!sourceName.equals(details.sourceName, true)) {
                LOG.debug("Ignore vm with invalid source tag " + sourceName)
                continue
            }

            val instance = AzureInstance(name)
            instances.put(name, instance)
        }

        if (instances.size > 0) {
            val promises = instances.map { (_, instance) ->
                getInstanceDataAsync(instance, details).toCompletableFuture() }
            val exceptions = arrayListOf<Throwable>()

            try {
                CompletableFuture.allOf(*promises.toTypedArray()).await()
            } catch (e: Throwable) {
                exceptions.add(e)
            }

            for (promise in promises) {
                try {
                    promise.get()
                } catch (e: Throwable) {
                    LOG.debug("Failed to receive vm data: " + e.message, e)
                    exceptions.add(e)
                }
            }

            val errors = exceptions.map { TypedCloudErrorInfo.fromException(it) }
                    .toTypedArray()
            image.updateErrors(*errors)
        }

        instances
    }

    private fun getVirtualMachinesAsync() = async(CommonPool, false) {
        try {
            val list = myAzure.withSubscription(mySubscriptionId).virtualMachines().list()
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
        val instanceViewPromise = async(CommonPool, false) {
            val machine = getVirtualMachineAsync(name, name).await()
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
        }.toCompletableFuture()

        val publicIpPromise: CompletableFuture<Unit>
        if (details.vmPublicIp && instance.ipAddress == null) {
            val pipName = name + PUBLIC_IP_SUFFIX
            publicIpPromise = async(CommonPool, false) {
                val ip = getPublicIpAsync(name, pipName).await()
                LOG.debug("Received public ip $ip for virtual machine $name")

                if (!ip.isNotBlank()) {
                    instance.setIpAddress(ip)
                }
            }.toCompletableFuture()
        } else {
            publicIpPromise = CompletableFuture.completedFuture(Unit)
        }

        CompletableFuture.allOf(instanceViewPromise, publicIpPromise).await()
    }

    private fun getVirtualMachineAsync(groupId: String, name: String) = async(CommonPool, false) {
        try {
            val machine = myAzure.withSubscription(mySubscriptionId).virtualMachines().getByGroup(groupId, name)
            machine.refreshInstanceView()
            LOG.debug("Received virtual machine $name info")
            machine
        } catch (e: Throwable) {
            val message = "Failed to get virtual machine info: " + e.message
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    private fun getPublicIpAsync(groupId: String, name: String) = async(CommonPool, false) {
        try {
            val ipAddress = myAzure.withSubscription(mySubscriptionId).publicIpAddresses().getByGroup(groupId, name)
            LOG.debug("Received public ip $ipAddress for $name")
            ipAddress.ipAddress()
        } catch (e: Throwable) {
            val message = "Failed to get public ip address $name info: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    override fun checkImage(image: AzureCloudImage): Array<TypedCloudErrorInfo> {
        val exceptions = ArrayList<Throwable>()
        val imageUrl = image.imageDetails.imageUrl

        try {
            getVhdOsTypeAsync(imageUrl).toCompletableFuture().get()
        } catch (e: Throwable) {
            LOG.debug("Failed to get os type for vhd " + imageUrl, e)
            exceptions.add(e)
        }

        return exceptions.map { TypedCloudErrorInfo.fromException(it) }
                .toTypedArray()
    }

    override fun checkInstance(instance: AzureCloudInstance): Array<TypedCloudErrorInfo> {
        return emptyArray()
    }

    /**
     * Gets a list of VM sizes.

     * @return list of sizes.
     */
    override fun getVmSizesAsync() = async(CommonPool, false) {
        try {
            val vmSizes = myAzure.withSubscription(mySubscriptionId).virtualMachines().sizes().listByRegion(myLocation)
            LOG.debug("Received list of vm sizes in location " + myLocation!!)
            val comparator = AlphaNumericStringComparator()
            vmSizes.map { it.name() }.sortedWith(comparator)
        } catch (e: Throwable) {
            val message = "Failed to get list of vm sizes in location $myLocation: ${e.message}"
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

        val details = instance.image.imageDetails

        createResourceGroupAsync(name, myLocation!!).await()
        LOG.debug("Created resource group $name in location $myLocation")

        val publicIp = details.vmPublicIp
        val templateName = if (publicIp) "/templates/vm-template-pip.json" else "/templates/vm-template.json"
        val templateValue = AzureUtils.getResourceAsString(templateName)

        val params = HashMap<String, JsonValue>()
        params.put("imageUrl", JsonValue(details.imageUrl))
        params.put("vmName", JsonValue(name))
        params.put("networkId", JsonValue(details.networkId))
        params.put("subnetName", JsonValue(details.subnetId))
        params.put("adminUserName", JsonValue(details.username))
        params.put("adminPassword", JsonValue(details.password!!))
        params.put("osType", JsonValue(details.osType))
        params.put("vmSize", JsonValue(details.vmSize))
        params.put("customData", JsonValue(customData))
        params.put("serverId", JsonValue(myServerId!!))
        params.put("profileId", JsonValue(userData.profileId))
        params.put("sourceId", JsonValue(details.sourceName))

        val deploymentParameters = "Deployment parameters:"
        params.entries.joinToString("\n") {
            (key, value) ->
            val parameter = if (key == "adminPassword") "*****" else value.value
            " - '$key' = '$parameter'"
        }

        LOG.debug(deploymentParameters)

        val parameters = AzureUtils.serializeObject(params)
        LOG.debug("Deployment template: \n" + templateValue)

        createDeploymentAsync(name, name, templateValue, parameters).await()
    }

    private fun createResourceGroupAsync(groupId: String, location: String) = async(CommonPool) {
        try {
            myAzure.withSubscription(mySubscriptionId).resourceGroups().define(groupId)
                    .withRegion(location)
                    .aCreate().await()
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
                    .aCreate().await()
        } catch (e: com.microsoft.azure.CloudException) {
            val details = e.body.details.joinToString("\n")
            val message = "Failed to create deployment in resource group $groupId: ${e.message}\n$details"
            LOG.debug(message, e)
            throw CloudException(message, e)
        } catch (e: Throwable) {
            val message = "Failed to create deployment in resource group $groupId: ${e.message}"
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
        val name = instance.name
        deleteResourceGroupAsync(instance.name).await()

        val url = instance.image.imageDetails.imageUrl
        val storageBlobs: URI
        try {
            val imageUrl = URI(url)
            storageBlobs = URI(imageUrl.scheme, imageUrl.host, "/vhds/" + name, null)
        } catch (e: URISyntaxException) {
            val message = "Failed to parse VHD image URL $url for instance $name: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }

        val blobs = getBlobsAsync(storageBlobs).await()
        for (blob in blobs) {
            try {
                blob.deleteIfExists()
            } catch (e: Exception) {
                val message = "Failed to delete blob ${blob.uri} for instance $name: ${e.message}"
                LOG.warnAndDebugDetails(message, e)
            }
        }
    }

    private fun deleteResourceGroupAsync(groupId: String) = async(CommonPool) {
        try {
            myAzure.withSubscription(mySubscriptionId).resourceGroups()
                    .aDelete(groupId).await()
            LOG.debug("Resource group $groupId has been successfully deleted")
        } catch (e: Throwable) {
            val message = "Failed to delete resource group $groupId: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Restarts an instance.
     *
     * @param instance is a cloud instance.
     * @return promise.
     */
    override fun restartVmAsync(instance: AzureCloudInstance) = async(CommonPool, false) {
        val name = instance.name
        try {
            myAzure.withSubscription(mySubscriptionId).virtualMachines().getByGroup(name, name).restart()
            LOG.debug("Virtual machine $name has been successfully restarted")
        } catch (e: Throwable) {
            val message = "Failed to restart virtual machine $name: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    override fun startVmAsync(instance: AzureCloudInstance) = async(CommonPool, false) {
        val name = instance.name
        try {
            myAzure.withSubscription(mySubscriptionId).virtualMachines().getByGroup(name, name).start()
            LOG.debug("Virtual machine $name has been successfully started")
        } catch (e: Throwable) {
            val message = "Failed to start virtual machine $name: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    override fun stopVmAsync(instance: AzureCloudInstance) = async(CommonPool, false) {
        val name = instance.name
        try {
            myAzure.withSubscription(mySubscriptionId).virtualMachines().getByGroup(name, name).deallocate()
            LOG.debug("Virtual machine $name has been successfully stopped")
        } catch (e: Throwable) {
            val message = "Failed to stop virtual machine $name: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Gets an OS type of VHD image.

     * @param imageUrl is image URL.
     * *
     * @return OS type (Linux, Windows).
     */
    override fun getVhdOsTypeAsync(imageUrl: String) = async(CommonPool) {
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
            blobs = getBlobsAsync(uri).await()
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

        val metadata = blob.metadata
        if ("OSDisk" != metadata["MicrosoftAzureCompute_ImageType"]) {
            val message = "Found blob ${blob.uri} with invalid OSDisk metadata"
            LOG.debug(message)
            return@async null
        }

        if ("Generalized" != metadata["MicrosoftAzureCompute_OSState"]) {
            LOG.debug("Found blob ${blob.uri} with invalid Generalized metadata")
            throw CloudException("VHD image should be generalized.")
        }

        metadata["MicrosoftAzureCompute_OSType"]
    }

    private fun getBlobsAsync(uri: URI) = async(CommonPool, false) {
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

        val account = getStorageAccountAsync(storage).await()
        val containerName = filesPrefix.substring(1, slash)
        val container: CloudBlobContainer

        try {
            container = account.createCloudBlobClient().getContainerReference(containerName)
        } catch (e: Throwable) {
            val message = "Failed to connect to storage account $storage: ${e.message}"
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

    private fun getStorageAccountAsync(storage: String) = async(CommonPool) {
        val accounts = getStorageAccountsAsync().await()
        val account = accounts.firstOrNull({ storage.equals(it.name(), true) })

        if (account == null) {
            val message = "Storage account $storage not found"
            LOG.debug(message)
            throw CloudException(message)
        }

        if (!account.regionName().equals(myLocation!!, true)) {
            val message = "VHD image should be located in storage account in the $myLocation region"
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

    private fun getStorageAccountsAsync() = async(CommonPool, false) {
        try {
            val accounts = myAzure.withSubscription(mySubscriptionId).storageAccounts().list()
            LOG.debug("Received list of storage accounts")
            accounts
        } catch (e: Throwable) {
            val message = "Failed to get list of storage accounts: " + e.message
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    private fun getStorageAccountKeysAsync(groupName: String, storageName: String) = async(CommonPool, false) {
        try {
            val account = myAzure.withSubscription(mySubscriptionId).storageAccounts().getByGroup(groupName, storageName)
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
    override fun getSubscriptionsAsync() = async(CommonPool, false) {
        try {
            val list = myAzure.subscriptions().list()
            LOG.debug("Received list of subscriptions")

            val subscriptions = LinkedHashMap<String, String>()
            Collections.sort(list) { o1, o2 -> o1.displayName().compareTo(o2.displayName()) }

            for (subscription in list) {
                subscriptions.put(subscription.subscriptionId(), subscription.displayName())
            }

            subscriptions
        } catch (e: Throwable) {
            val message = "Failed to get list of subscriptions " + e.message
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Gets a list of locations.
     *
     * @return locations.
     */
    override fun getLocationsAsync(subscription: String) = async(CommonPool, false) {
        try {
            val list = myAzure.subscriptions().getByName(mySubscriptionId).listLocations()
            LOG.debug("Received list of locations in subscription " + subscription)

            val locations = LinkedHashMap<String, String>()
            Collections.sort(list) { o1, o2 -> o1.displayName().compareTo(o2.displayName()) }

            for (location in list) {
                locations.put(location.name(), location.displayName())
            }

            locations
        } catch (e: Throwable) {
            val message = "Failed to get list of locations in subscription $subscription: ${e.message}"
            LOG.debug(message, e)
            throw CloudException(message, e)
        }
    }

    /**
     * Gets a list of networks.
     *
     * @return list of networks.
     */
    override fun getNetworksAsync() = async(CommonPool, false) {
        try {
            val list = myAzure.withSubscription(mySubscriptionId).networks().list()
            LOG.debug("Received list of networks")

            val networks = LinkedHashMap<String, List<String>>()
            for (network in list) {
                if (!network.regionName().equals(myLocation!!, ignoreCase = true)) continue

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
     * Sets a target location for resources.
     *
     * @param location is a location.
     */
    fun setLocation(location: String) {
        myLocation = location
    }

    /**
     * Configures http proxy settings.
     */
    private fun configureProxy(builder: OkHttpClient.Builder) {
        // Set HTTP proxy
        val httpProxyHost = TeamCityProperties.getProperty(HTTP_PROXY_HOST)
        val httpProxyPort = TeamCityProperties.getInteger(HTTP_PROXY_PORT, 80)
        if (httpProxyHost.isNotBlank()) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(httpProxyHost, httpProxyPort)))
        }

        // Set HTTPS proxy
        val httpsProxyHost = TeamCityProperties.getProperty(HTTPS_PROXY_HOST)
        val httpsProxyPort = TeamCityProperties.getInteger(HTTPS_PROXY_PORT, 443)
        if (httpsProxyHost.isNotBlank()) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(httpsProxyHost, httpsProxyPort)))
        }

        // Set proxy authentication
        val httpProxyUser = TeamCityProperties.getProperty(HTTP_PROXY_USER)
        val httpProxyPassword = TeamCityProperties.getProperty(HTTP_PROXY_PASSWORD)
        if (httpProxyUser.isNotBlank()) {
            builder.proxyAuthenticator(CredentialsAuthenticator(httpProxyUser, httpProxyPassword))
        }
    }
}
