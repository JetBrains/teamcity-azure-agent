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
import com.microsoft.azure.credentials.MSICredentials
import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.compute.OperatingSystemStateTypes
import com.microsoft.azure.management.compute.VirtualMachine
import com.microsoft.azure.management.containerinstance.ContainerGroup
import com.microsoft.azure.management.resources.Deployment
import com.microsoft.azure.management.resources.DeploymentMode
import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey
import com.microsoft.azure.storage.blob.CloudBlob
import com.microsoft.azure.storage.blob.CloudBlobContainer
import jetbrains.buildServer.clouds.CloudException
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.azure.AzureCompress
import jetbrains.buildServer.clouds.azure.AzureProperties
import jetbrains.buildServer.clouds.azure.arm.*
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import jetbrains.buildServer.clouds.azure.arm.utils.awaitList
import jetbrains.buildServer.clouds.azure.arm.utils.awaitOne
import jetbrains.buildServer.clouds.azure.connector.AzureApiConnectorBase
import jetbrains.buildServer.clouds.azure.utils.AlphaNumericStringComparator
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.version.ServerVersionHolder
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.sync.Mutex
import kotlinx.coroutines.experimental.sync.withLock
import org.apache.commons.codec.binary.Base64
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashMap

/**
 * Provides azure arm management capabilities.
 */
class AzureApiConnectorImpl(params: Map<String, String>)
    : AzureApiConnectorBase<AzureCloudImage, AzureCloudInstance>(), AzureApiConnector {

    private val myAzure: Azure.Authenticated
    private var mySubscriptionId: String? = null
    private var myServerId: String? = null
    private var myProfileId: String? = null
    private val deploymentLocks = ConcurrentHashMap<String, Mutex>()

    init {
        params[AzureConstants.SUBSCRIPTION_ID]?.let {
            mySubscriptionId = it
        }

        val environment = params[AzureConstants.ENVIRONMENT]
        val env = when (environment) {
            "AZURE_CHINA" -> AzureEnvironment.AZURE_CHINA
            "AZURE_GERMANY" -> AzureEnvironment.AZURE_GERMANY
            "AZURE_US_GOVERNMENT" -> AzureEnvironment.AZURE_US_GOVERNMENT
            else -> AzureEnvironment.AZURE
        }

        val credentialsType = params[AzureConstants.CREDENTIALS_TYPE]
        val credentials = if (credentialsType == AzureConstants.CREDENTIALS_MSI) {
            MSICredentials(env)
        } else {
            val tenantId = params[AzureConstants.TENANT_ID]
            val clientId = params[AzureConstants.CLIENT_ID]
            val clientSecret = params[AzureConstants.CLIENT_SECRET]
            ApplicationTokenCredentials(clientId, tenantId, clientSecret, env)
        }

        myAzure = Azure.configure()
                .configureProxy()
                .withUserAgent("TeamCity Server ${ServerVersionHolder.getVersion().displayVersion}")
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

        val machines = if (images.any { it.imageDetails.type != AzureCloudImageType.Container }) {
            getVirtualMachinesAsync().await()
        } else {
            emptyList()
        }

        val containers = if (images.any { it.imageDetails.type == AzureCloudImageType.Container }) {
            getContainerGroupsAsync().await()
        } else {
            emptyList()
        }

        images.forEach { image ->
            try {
                val result = if (image.imageDetails.type != AzureCloudImageType.Container) {
                    findVmInstancesAsync(image, machines).await()
                } else {
                    findContainerInstancesAsync(image, containers).await()
                }
                LOG.debug("Received list of image ${image.name} instances")
                @Suppress("UNCHECKED_CAST")
                imageMap[image] = result as Map<String, R>
            } catch (e: Throwable) {
                LOG.warn("Failed to receive list of image ${image.name} instances: ${e.message}", e)
                image.updateErrors(TypedCloudErrorInfo.fromException(e))
            }
        }

        imageMap
    }

    private fun findVmInstancesAsync(image: AzureCloudImage, machines: List<VirtualMachine>) = async(CommonPool) {
        val instances = hashMapOf<String, AzureInstance>()
        val details = image.imageDetails

        for (virtualMachine in machines) {
            val name = virtualMachine.name()
            val tags = virtualMachine.tags()

            if (details.target == AzureCloudDeployTarget.Instance) {
                if (virtualMachine.id() != details.instanceId!!) {
                    LOG.debug("Ignore vm with invalid id " + virtualMachine.id())
                    continue
                }
            } else {
                if (!name.startsWith(details.sourceId, true)) {
                    LOG.debug("Ignore vm with name $name")
                    continue
                }

                val serverId = tags[AzureConstants.TAG_SERVER]
                if (!serverId.equals(myServerId, true)) {
                    LOG.debug("Ignore vm with invalid server tag $serverId")
                    continue
                }

                val profileId = tags[AzureConstants.TAG_PROFILE]
                if (!profileId.equals(myProfileId, true)) {
                    LOG.debug("Ignore vm with invalid profile tag $profileId")
                    continue
                }

                val sourceName = tags[AzureConstants.TAG_SOURCE]
                if (!sourceName.equals(details.sourceId, true)) {
                    LOG.debug("Ignore vm with invalid source tag $sourceName")
                    continue
                }
            }

            val instance = AzureInstance(name)
            instance.properties = tags
            instances[name] = instance
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

        val errors = exceptions.map { TypedCloudErrorInfo.fromException(it) }.toTypedArray()
        image.updateErrors(*errors)

        instances
    }

    private fun findContainerInstancesAsync(image: AzureCloudImage, containers: List<ContainerGroup>) = async(CommonPool) {
        val instances = hashMapOf<String, AzureInstance>()
        val details = image.imageDetails

        for (container in containers) {
            val name = container.name()
            val tags = container.tags()

            if (!name.startsWith(details.sourceId, true)) {
                LOG.debug("Ignore container with name $name")
                continue
            }

            val serverId = tags[AzureConstants.TAG_SERVER]
            if (!serverId.equals(myServerId, true)) {
                LOG.debug("Ignore container with invalid server tag $serverId")
                continue
            }

            val profileId = tags[AzureConstants.TAG_PROFILE]
            if (!profileId.equals(myProfileId, true)) {
                LOG.debug("Ignore container with invalid profile tag $profileId")
                continue
            }

            val sourceName = tags[AzureConstants.TAG_SOURCE]
            if (!sourceName.equals(details.sourceId, true)) {
                LOG.debug("Ignore container with invalid source tag $sourceName")
                continue
            }

            val instance = AzureInstance(name)
            instance.properties = tags
            container.containers()[name]?.let {
                it.instanceView()?.currentState()?.let {
                    instance.setPowerState(it.state())
                    it.startTime()?.let {
                        instance.setStartDate(it.toDate())
                    }
                }
            }

            instances[name] = instance
        }

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

    private fun getContainerGroupsAsync() = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val list = myAzure.withSubscription(mySubscriptionId)
                    .containerGroups()
                    .listAsync()
                    .awaitList()
            LOG.debug("Received list of container groups")
            list
        } catch (t: Throwable) {
            val message = "Failed to get list of container groups: " + t.message
            LOG.debug(message, t)
            throw CloudException(message, t)
        }
    }

    private fun getInstanceDataAsync(instance: AzureInstance, details: AzureCloudImageDetails) = async(CommonPool) {
        val name = instance.name
        val groupId = getResourceGroup(details, name)
        val promises = arrayListOf<Deferred<Any?>>()

        promises += async(CommonPool, CoroutineStart.LAZY) {
            getVirtualMachineAsync(groupId, name).await()?.let {
                LOG.debug("Received virtual machine $name info")

                for (status in it.instanceView().statuses()) {
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

                if (it.tags().containsKey(AzureConstants.TAG_INVESTIGATION)) {
                    LOG.debug("Virtual machine $name is marked by ${AzureConstants.TAG_INVESTIGATION} tag")
                    instance.setProvisioningState("Investigation")
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
        image.handler?.let {
            return@runBlocking it.checkImageAsync(image).await()
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
     * Gets a list of vm instances.
     * @return list of instances.
     */
    override fun getInstancesAsync() = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val list = myAzure.withSubscription(mySubscriptionId)
                    .virtualMachines()
                    .listAsync()
                    .awaitList()
            LOG.debug("Received list of vm instances")

            list.map {
                it.id() to "${it.resourceGroupName()}/${it.name()}".toLowerCase()
            }.sortedBy {
                it.second
            }.associateTo(LinkedHashMap<String, String>(), {
                it.first to it.second
            })
        } catch (e: Throwable) {
            val message = "Failed to get list of instances: ${e.message}"
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
                        it.regionName().equals(region, ignoreCase = true) && it.osDiskImage().osState() == OperatingSystemStateTypes.GENERALIZED
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
            LOG.debug("Received list of vm sizes in region $region")
            val comparator = AlphaNumericStringComparator()
            vmSizes.map { it.name() }.sortedWith(comparator)
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
    override fun getStorageAccountsAsync(region: String) = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val storageAccounts = myAzure.withSubscription(mySubscriptionId)
                    .storageAccounts()
                    .listAsync()
                    .awaitList()
                    .filter { region.equals(it.regionName(), true) }
            LOG.debug("Received list of storage accounts in region $region")
            val comparator = AlphaNumericStringComparator()
            storageAccounts.map { it.name() }.sortedWith(comparator)
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
    override fun createInstanceAsync(instance: AzureCloudInstance, userData: CloudInstanceUserData): Deferred<Unit> {
        instance.properties[AzureConstants.TAG_SERVER] = myServerId!!
        return if (instance.image.imageDetails.type == AzureCloudImageType.Container) {
            createContainerAsync(instance, userData)
        } else {
            createVmAsync(instance, userData)
        }
    }


    private fun createVmAsync(instance: AzureCloudInstance, userData: CloudInstanceUserData) = async(CommonPool) {
        val name = instance.name
        val customData = encodeCustomData(userData, name)

        val handler = instance.image.handler
        val builder = handler!!.prepareBuilderAsync(instance).await()
                .setCustomData(customData)
                .setTags(VM_RESOURCE_NAME, instance.properties)

        val details = instance.image.imageDetails
        val groupId = when (details.target) {
            AzureCloudDeployTarget.NewGroup -> {
                createResourceGroupAsync(name, details.region!!).await()
                name
            }
            AzureCloudDeployTarget.SpecificGroup -> details.groupId!!
            else -> throw CloudException("Creating virtual machine $name is prohibited")
        }

        builder.logDetails()
        val template = builder.toString()
        val parameters = builder.serializeParameters()

        createDeploymentAsync(groupId, name, template, parameters).await()
    }

    private fun createContainerAsync(instance: AzureCloudInstance, userData: CloudInstanceUserData) = async(CommonPool) {
        val name = instance.name

        val handler = instance.image.handler
        val builder = handler!!.prepareBuilderAsync(instance).await()

        val details = instance.image.imageDetails
        val groupId = when (details.target) {
            AzureCloudDeployTarget.NewGroup -> {
                createResourceGroupAsync(details.sourceId, details.region!!).await()
                details.sourceId
            }
            AzureCloudDeployTarget.SpecificGroup -> details.groupId!!
            else -> throw CloudException("Creating container $name is prohibited")
        }

        if ("Linux" == details.osType && details.storageAccount != null) {
            addContainerCustomDataAsync(instance, userData, builder).await()
        } else {
            addContainerEnvironment(instance, userData, builder)
        }

        builder.setParameterValue(AzureConstants.TEAMCITY_URL, userData.serverAddress)
                .setTags(CONTAINER_RESOURCE_NAME, instance.properties)
                .logDetails()

        val template = builder.toString()
        val parameters = builder.serializeParameters()

        createDeploymentAsync(groupId, name, template, parameters).await()
    }

    private fun addContainerCustomDataAsync(instance: AzureCloudInstance, userData: CloudInstanceUserData, builder: ArmTemplateBuilder) = async(CommonPool) {
        val name = instance.name
        val imageDetails = instance.image.imageDetails

        val customData = encodeCustomData(userData, name)
        val fileContent = AzureUtils.getResourceAsString("/templates/ovf-env.xml")
                .format(customData)
                .toByteArray(Charsets.UTF_8)

        // Set storage account credentials
        val account = getStorageAccountAsync(imageDetails.storageAccount!!, imageDetails.region!!).await()
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
        deploymentLocks.getOrPut("$groupId/$deploymentId", { Mutex() }).withLock {
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
    }

    /**
     * Deletes a cloud instance.
     *
     * @param instance is a cloud instance.
     * @return promise.
     */
    override fun deleteInstanceAsync(instance: AzureCloudInstance) =
            if (instance.image.imageDetails.type == AzureCloudImageType.Container) {
                deleteContainerAsync(instance)
            } else {
                deleteVmAsync(instance)
            }

    private fun deleteVmAsync(instance: AzureCloudInstance) = async(CommonPool) {
        val details = instance.image.imageDetails
        val name = instance.name
        val groupId = getResourceGroup(details, name)

        val virtualMachine = getVirtualMachineAsync(groupId, name).await()

        when (details.target) {
            AzureCloudDeployTarget.NewGroup -> deleteResourceGroupAsync(name).await()
            AzureCloudDeployTarget.SpecificGroup -> deleteDeploymentAsync(groupId, name).await()
            AzureCloudDeployTarget.Instance -> throw CloudException("Deleting virtual machine $name is prohibited")
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

    private fun deleteContainerAsync(instance: AzureCloudInstance) = async(CommonPool) {
        val details = instance.image.imageDetails
        val name = instance.name
        val groupId = getResourceGroup(details, details.sourceId)

        when (details.target) {
            AzureCloudDeployTarget.NewGroup -> deleteResourceGroupAsync(name).await()
            AzureCloudDeployTarget.SpecificGroup -> deleteDeploymentAsync(groupId, name).await()
            AzureCloudDeployTarget.Instance -> throw CloudException("Deleting container instance is not supported")
        }
    }

    override fun deleteVmBlobsAsync(instance: AzureCloudInstance) = async(CommonPool) {
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
        deploymentLocks.getOrPut("$groupId/$deploymentId", { Mutex() }).withLock {
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

                deleteDeploymentResourcesAsync(subscription, deployment).await()

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

    private suspend fun deleteDeploymentResourcesAsync(subscription: Azure, deployment: Deployment) = async(CommonPool) {
        val operations = deployment.deploymentOperations().listAsync().awaitList()
        if (!operations.isNotEmpty()) {
            return@async
        }

        operations.sortedByDescending { it.timestamp() }.map {
            async(CommonPool, start = CoroutineStart.LAZY) {
                it.targetResource()?.let {
                    val resourceId = it.id()
                    val resources = linkedSetOf<String>(resourceId)
                    if (it.resourceType() == "Microsoft.Compute/virtualMachines") {
                        subscription.virtualMachines().getByIdAsync(resourceId).awaitOne()?.let {
                            if (it.isManagedDiskEnabled) {
                                resources.add(it.osDiskId())
                            }
                        }
                    }

                    resources.forEach {
                        try {
                            LOG.debug("Deleting resource $it")
                            subscription.genericResources().deleteByIdAsync(it).await()
                        } catch (e: com.microsoft.azure.CloudException) {
                            val details = AzureUtils.getExceptionDetails(e)
                            val message = "Failed to delete resource $it in ${deployment.name()} in group ${deployment.resourceGroupName()}: $details"
                            LOG.warnAndDebugDetails(message, e)
                        } catch (e: Exception) {
                            LOG.warnAndDebugDetails("Failed to delete resource $it in ${deployment.name()} in group ${deployment.resourceGroupName()}", e)
                        }
                    }
                }
            }
        }.forEach { it.await() }
    }

    /**
     * Restarts an instance.
     *
     * @param instance is a cloud instance.
     * @return promise.
     */
    override fun restartInstanceAsync(instance: AzureCloudInstance) = async(CommonPool, CoroutineStart.LAZY) {
        if (instance.image.imageDetails.type == AzureCloudImageType.Container) {
            throw CloudException("Restarting container instances is not supported")
        } else {
            restartVmAsync(instance).await()
        }
    }

    private fun restartVmAsync(instance: AzureCloudInstance) = async(CommonPool, CoroutineStart.LAZY) {
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

    /**
     * Starts an instance.
     *
     * @param instance is a cloud instance.
     * @return promise.
     */
    override fun startInstanceAsync(instance: AzureCloudInstance) = async(CommonPool, CoroutineStart.LAZY) {
        if (instance.image.imageDetails.type == AzureCloudImageType.Container) {
            throw CloudException("Starting container instances is not supported")
        } else {
            startVmAsync(instance).await()
        }
    }

    private fun startVmAsync(instance: AzureCloudInstance) = async(CommonPool, CoroutineStart.LAZY) {
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

    /**
     * Stops an instance.
     *
     * @param instance is a cloud instance.
     * @return promise.
     */
    override fun stopInstanceAsync(instance: AzureCloudInstance) =
            if (instance.image.imageDetails.type == AzureCloudImageType.Container) {
                deleteContainerAsync(instance)
            } else {
                stopVmAsync(instance)
            }


    private fun stopVmAsync(instance: AzureCloudInstance) = async(CommonPool, CoroutineStart.LAZY) {
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
            LOG.debug("Found more than one blobs for url $imageUrl")
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

        val result = RESOURCE_GROUP_PATTERN.find(account.id())
        if (result == null) {
            val message = "Invalid storage account identifier " + account.id()
            LOG.debug(message)
            throw CloudException(message)
        }

        val (groupId) = result.destructured
        val keys = getStorageAccountKeysAsync(groupId, storage).await()
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
    override fun getSubscriptionsAsync() = async(CommonPool, CoroutineStart.LAZY) {
        try {
            val list = myAzure.subscriptions().listAsync().awaitList()
            LOG.debug("Received list of subscriptions")

            val subscriptions = LinkedHashMap<String, String>()
            Collections.sort(list) { o1, o2 -> o1.displayName().compareTo(o2.displayName()) }

            for (subscription in list) {
                subscriptions[subscription.subscriptionId()] = subscription.displayName()
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
            LOG.debug("Received list of regions in subscription $mySubscriptionId")

            val regions = LinkedHashMap<String, String>()
            list.sortWith(Comparator { o1, o2 -> o1.displayName().compareTo(o2.displayName()) })

            for (region in list) {
                regions[region.name()] = region.displayName()
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
                networks[network.id()] = subNetworks
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
        private const val PUBLIC_IP_SUFFIX = "-pip"
        private const val PROVISIONING_STATE = "ProvisioningState/"
        private const val POWER_STATE = "PowerState/"
        private const val HTTP_PROXY_HOST = "http.proxyHost"
        private const val HTTP_PROXY_PORT = "http.proxyPort"
        private const val HTTPS_PROXY_HOST = "https.proxyHost"
        private const val HTTPS_PROXY_PORT = "https.proxyPort"
        private const val HTTP_PROXY_USER = "http.proxyUser"
        private const val HTTP_PROXY_PASSWORD = "http.proxyPassword"

        private const val CONTAINER_RESOURCE_NAME = "[parameters('containerName')]"
        private const val VM_RESOURCE_NAME = "[parameters('vmName')]"
    }
}
