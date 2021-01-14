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

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.google.common.cache.CacheBuilder
import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.compute.VirtualMachine
import com.microsoft.azure.management.compute.VirtualMachineInstanceView
import com.microsoft.azure.management.containerinstance.ContainerGroup
import com.microsoft.azure.management.network.PublicIPAddress
import com.microsoft.azure.management.resources.fluentcore.arm.ResourceUtils
import jetbrains.buildServer.clouds.azure.arm.AzureCloudDeployTarget
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskNotifications
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTask
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_THROTTLER_TASK_THROTTLE_TIMEOUT_SEC
import jetbrains.buildServer.clouds.azure.arm.throttler.register
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.util.StringUtil
import rx.Observable
import rx.Single
import java.time.Clock
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class FetchInstancesTaskInstanceDescriptor (
        val imageId: String,
        val id: String,
        val name: String,
        val tags: Map<String, String>,
        val publicIpAddress: String?,
        val provisioningState: String?,
        val startDate: Date?,
        val powerState: String?,
        val error: TypedCloudErrorInfo?
        )

data class FetchInstancesTaskParameter(val serverId: String?, val profileId: String?, val images: Array<FetchInstancesTaskImageDescriptor>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FetchInstancesTaskParameter

        if (serverId != other.serverId) return false
        if (profileId != other.profileId) return false
        if (!images.contentDeepEquals(other.images)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = serverId?.hashCode() ?: 0
        result = 31 * result + (profileId?.hashCode() ?: 0)
        result = 31 * result + images.contentDeepHashCode()
        return result
    }
}
data class FetchInstancesTaskImageDescriptor(val imageId: String, val imageDetails: FetchInstancesTaskCloudImageDetails)
data class FetchInstancesTaskCloudImageDetails(val vmPublicIp: Boolean?, val instanceId: String?, val sourceId: String, val target: AzureCloudDeployTarget, val isVmInstance: Boolean, val resourceGroup: String)

class FetchInstancesTaskImpl(private val myNotifications: AzureTaskNotifications) : AzureThrottlerCacheableTask<Azure, FetchInstancesTaskParameter, List<FetchInstancesTaskInstanceDescriptor>> {
    private val myTimeoutInSeconds = AtomicLong(60)
    private val myIpAddresses = AtomicReference<Array<IPAddressDescriptor>>(emptyArray())
    private val myInstancesCache = createCache()
    private val myLastUpdatedDate = AtomicReference<LocalDateTime>(LocalDateTime.MIN)

    init {
        myNotifications.register<AzureTaskVirtualMachineStatusChangedEventArgs> {
            updateVirtualMachine(it.api, it.virtualMachine)
        }
        myNotifications.register<AzureTaskDeploymentStatusChangedEventArgs> { args ->
            val dependency = args.deployment.dependencies().firstOrNull { it.resourceType() == VIRTUAL_MACHINES_RESOURCE_TYPE }
            if (dependency != null) {
                if (args.deployment.provisioningState() == PROVISIONING_STATE_SUCCEEDED) {
                    updateVirtualMachine(args.api, dependency.id())
                }
                return@register
            }

            val containerProvider = args.deployment.providers().firstOrNull { it.namespace() == CONTAINER_INSTANCE_NAMESPACE }
            if (containerProvider != null) {
                val id = args.deployment.id()
                val name = args.deployment.name()

                val containerId = ResourceUtils.constructResourceId(
                        ResourceUtils.subscriptionFromResourceId(id),
                        ResourceUtils.groupFromResourceId(id),
                        containerProvider.namespace(),
                        CONTAINER_GROUPS_RESOURCE_TYPE,
                        name,
                        "")
                updateContainer(args.api, containerId)
            }
        }
    }

    override fun create(api: Azure, parameter: FetchInstancesTaskParameter): Single<List<FetchInstancesTaskInstanceDescriptor>> {
        if (StringUtil.isEmptyOrSpaces(api.subscriptionId())) {
            LOG.debug("FetchInstancesTask returns empty list. Subscription is empty")
            return Single
                    .just(emptyList<FetchInstancesTaskInstanceDescriptor>());
        }

        val ipAddresses =
                fetchIPAddresses(api)

        val machineInstances =
            api
                    .virtualMachines()
                    .listAsync()
                    .flatMap { fetchVirtualMachine(it) }

        val containerInstances =
            api
                    .containerGroups()
                    .listAsync()
                    .map { fetchContainer(it) }

        return machineInstances
                .mergeWith(containerInstances)
                .toList()
                .takeLast(1)
                .withLatestFrom(ipAddresses) { instances, ipList ->
                    myLastUpdatedDate.set(LocalDateTime.now(Clock.systemUTC()))
                    myIpAddresses.set(ipList.toTypedArray())

                    val keysInCache = myInstancesCache.asMap().keys.toSet()
                    val newInstances = instances.associateBy { it.id.toLowerCase() }
                    myInstancesCache.putAll(newInstances)
                    myInstancesCache.invalidateAll(keysInCache.minus(newInstances.keys))

                    Unit
                }
                .map { getFilteredResources(parameter) }
                .toSingle()
    }

    private fun fetchIPAddresses(api: Azure): Observable<MutableList<IPAddressDescriptor>> {
        return api
                .publicIPAddresses()
                .listAsync()
                .filter { !it.ipAddress().isNullOrBlank() }
                .map { IPAddressDescriptor(it.name(), it.resourceGroupName(), it.ipAddress(), getAssignedNetworkInterfaceId(it)) }
                .toList()
                .takeLast(1)
                .doOnNext {
                    LOG.debug("Received list of ip addresses")
                }
                .onErrorReturn {
                    val message = "Failed to get list of public ip addresses: " + it.message
                    LOG.debug(message, it)
                    emptyList()
                }
    }

    private fun updateContainer(api: Azure, containerId: String) {
        api.containerGroups()
                .getByIdAsync(containerId)
                .map { it?.let { fetchContainer(it) } }
                .withLatestFrom(fetchIPAddresses(api)) {
                    instance, ipList ->
                    myIpAddresses.set(ipList.toTypedArray())
                    if (instance != null) {
                        myInstancesCache.put(instance.id.toLowerCase(), instance)
                    } else {
                        myInstancesCache.invalidate(containerId.toLowerCase())
                    }
                }
                .take(1)
                .subscribe()
    }

    private fun fetchContainer(containerGroup: ContainerGroup): InstanceDescriptor {
        val state = containerGroup.containers()[containerGroup.name()]?.instanceView()?.currentState()
        val startDate = state?.startTime()?.toDate()
        val powerState = state?.state()
        val instance = InstanceDescriptor(
                containerGroup.id(),
                containerGroup.name(),
                containerGroup.tags(),
                null,
                startDate,
                powerState,
                null,
                null)
        return instance
    }


    private fun fetchVirtualMachine(vm: VirtualMachine): Observable<InstanceDescriptor> {
        val tags = vm.tags()
        val id = vm.id()
        val name = vm.name()
        LOG.debug("Reading state of virtual machine '$name'")

        return vm.refreshInstanceViewAsync()
                .map { getInstanceState(it, tags, name) }
                .onErrorReturn {
                    LOG.debug("Failed to get status of virtual machine '$name': $it.message", it)
                    InstanceViewState(null, null, null, it)
                }
                .map { instanceView ->
                    val instance = InstanceDescriptor(
                            id,
                            name,
                            tags,
                            instanceView.provisioningState,
                            instanceView.startDate,
                            instanceView.powerState,
                            if (instanceView.error != null) TypedCloudErrorInfo.fromException(instanceView.error) else null,
                            vm.primaryNetworkInterfaceId())
                    instance
                }
    }

    private fun updateVirtualMachine(api: Azure, virtualMachine: VirtualMachine) {
        fetchVirtualMachine(virtualMachine)
                .withLatestFrom(fetchIPAddresses(api)) { instance, ipList ->
                    myIpAddresses.set(ipList.toTypedArray())
                    myInstancesCache.put(instance.id.toLowerCase(), instance)
                }
                .take(1)
                .subscribe()
    }

    private fun updateVirtualMachine(api: Azure, virtualMachineId: String) {
        api.virtualMachines()
                .getByIdAsync(virtualMachineId)
                .flatMap { if (it != null) fetchVirtualMachine(it) else Observable.just(null) }
                .withLatestFrom(fetchIPAddresses(api)) {
                    instance, ipList ->
                    myIpAddresses.set(ipList.toTypedArray())
                    if (instance != null) {
                        myInstancesCache.put(instance.id.toLowerCase(), instance)
                    } else {
                        myInstancesCache.invalidate(virtualMachineId.toLowerCase())
                    }
                }
                .take(1)
                .subscribe()
    }

    private fun getAssignedNetworkInterfaceId(publicIpAddress: PublicIPAddress?): String? {
        if (publicIpAddress == null || !publicIpAddress.hasAssignedNetworkInterface()) return null
        val refId: String = publicIpAddress.inner().ipConfiguration().id()
        val parentId = ResourceUtils.parentResourceIdFromResourceId(refId)
        return parentId
    }


    override fun getFromCache(parameter: FetchInstancesTaskParameter): List<FetchInstancesTaskInstanceDescriptor>? {
        return getFilteredResources(parameter)
    }

    override fun needCacheUpdate(parameter: FetchInstancesTaskParameter): Boolean {
        val lastUpdatedDateTime = myLastUpdatedDate.get()
        return lastUpdatedDateTime.plusSeconds(myTimeoutInSeconds.get()) < LocalDateTime.now(Clock.systemUTC())
    }

    override fun checkThrottleTime(parameter: FetchInstancesTaskParameter): Boolean {
        val lastUpdatedDateTime = myLastUpdatedDate.get()
        return lastUpdatedDateTime.plusSeconds(getTaskThrottleTime()) < LocalDateTime.now(Clock.systemUTC())
    }

    override fun areParametersEqual(parameter: FetchInstancesTaskParameter, other: FetchInstancesTaskParameter): Boolean {
        return parameter.serverId == other.serverId
    }

    private fun getTaskThrottleTime(): Long {
        return TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_THROTTLER_TASK_THROTTLE_TIMEOUT_SEC, 10)
    }

    private fun getFilteredResources(filter: FetchInstancesTaskParameter): List<FetchInstancesTaskInstanceDescriptor> {
        return myInstancesCache.asMap().values
                .map { it to filter.images.find { image -> !isNotInstanceOfImage(it, image, filter.serverId, filter.profileId) } }
                .filter { (_, image) -> image != null }
                .map { (instance, image) ->
                    FetchInstancesTaskInstanceDescriptor(
                        image!!.imageId,
                        instance.id,
                        instance.name,
                        instance.tags,
                        getPublicIPAddressOrDefault(image.imageDetails, instance),
                        instance.provisioningState,
                        instance.startDate,
                        instance.powerState,
                        instance.error
                ) }
                .toList()
    }

    private fun isNotInstanceOfImage(instance: InstanceDescriptor, image: FetchInstancesTaskImageDescriptor, serverId: String?, profileId: String?): Boolean {
        val details = image.imageDetails
        val isVm = details.isVmInstance
        val name = instance.name
        val tags = instance.tags
        val id = instance.id
        val resourceTypeLogName = if (isVm) "vm" else "container"

        if (isVm && details.target == AzureCloudDeployTarget.Instance) {
            if (id != details.instanceId!!) {
                LOG.debug("Ignore vm with invalid id " + id)
                return true
            }
            return false
        }

        if (!name.startsWith(details.sourceId, true)) {
            LOG.debug("Ignore $resourceTypeLogName with name $name")
            return true
        }

        val sourceName = tags[AzureConstants.TAG_SOURCE]
        if (!sourceName.equals(details.sourceId, true)) {
            LOG.debug("Ignore $resourceTypeLogName with invalid source tag $sourceName")
            return true
        }

        val resourceServerId = tags[AzureConstants.TAG_SERVER]
        if (!resourceServerId.equals(serverId, true)) {
            LOG.debug("Ignore resource with invalid server tag $resourceServerId")
            return true
        }

        val resourceProfileId = tags[AzureConstants.TAG_PROFILE]
        if (!resourceProfileId.equals(profileId, true)) {
            LOG.debug("Ignore resource with invalid profile tag $resourceProfileId")
            return true
        }

        return false
    }

    override fun invalidateCache() {
        myInstancesCache.invalidateAll()
        myIpAddresses.set(emptyArray())
    }

    private fun getInstanceState(instanceView: VirtualMachineInstanceView, tags: Map<String, String>, name: String): InstanceViewState {
        var provisioningState: String? = null
        var startDate: Date? = null
        var powerState: String? = null

        for (status in instanceView.statuses()) {
            val code = status.code()
            if (code.startsWith(PROVISIONING_STATE)) {
                provisioningState = code.substring(PROVISIONING_STATE.length)
                val dateTime = status.time()
                if (dateTime != null) {
                    startDate = dateTime.toDate()
                }
            }

            if (code.startsWith(POWER_STATE)) {
                powerState = code.substring(POWER_STATE.length)
            }
        }

        if (tags.containsKey(AzureConstants.TAG_INVESTIGATION)) {
            LOG.debug("Virtual machine $name is marked by ${AzureConstants.TAG_INVESTIGATION} tag")
            provisioningState = "Investigation"
        }
        return InstanceViewState(provisioningState, startDate, powerState, null)
    }

    override fun setCacheTimeout(timeoutInSeconds: Long) {
        myTimeoutInSeconds.set(timeoutInSeconds)
    }

    private fun getPublicIPAddressOrDefault(details: FetchInstancesTaskCloudImageDetails, instance: InstanceDescriptor): String? {
        var ipAddresses = myIpAddresses.get()
        if (ipAddresses.isEmpty()) return null

        val name = instance.name
        val groupId = if (details.target == AzureCloudDeployTarget.NewGroup) name else details.resourceGroup
        val pipName = name + PUBLIC_IP_SUFFIX
        val ips = ipAddresses.filter {
            (it.resourceGroupName == groupId && it.name == pipName)
            || (instance.primaryNetworkInterfaceId != null && it.assignedNetworkInterfaceId == instance.primaryNetworkInterfaceId)
        }

        if (ips.isNotEmpty()) {
            for (ip in ips) {
                LOG.debug("Received public ip ${ip.ipAddress} ($ip) for virtual machine $name, pip $pipName")
            }

            val ip = ips.first().ipAddress
            if (!ip.isNullOrBlank()) {
                return ip
            }
        } else {
            LOG.debug("No public ip received for virtual machine $name")
        }
        return null
    }

    private fun createCache() = CacheBuilder.newBuilder().expireAfterWrite(32 * 60, TimeUnit.SECONDS).build<String, InstanceDescriptor>()

    data class InstanceViewState (val provisioningState: String?, val startDate: Date?, val powerState: String?, val error: Throwable?)

    data class CacheKey (val server: String?)

    data class CacheValue(val instances: List<InstanceDescriptor>, val ipAddresses: List<IPAddressDescriptor>, val lastUpdatedDateTime: LocalDateTime)

    data class InstanceDescriptor (
            val id: String,
            val name: String,
            val tags: Map<String, String>,
            val provisioningState: String?,
            val startDate: Date?,
            val powerState: String?,
            val error: TypedCloudErrorInfo?,
            val primaryNetworkInterfaceId: String?
    )

    data class IPAddressDescriptor (
            val name: String,
            val resourceGroupName: String,
            val ipAddress: String,
            val assignedNetworkInterfaceId: String?
    )

    companion object {
        private val LOG = Logger.getInstance(FetchInstancesTaskImpl::class.java.name)
        private const val PUBLIC_IP_SUFFIX = "-pip"
        private const val PROVISIONING_STATE = "ProvisioningState/"
        private const val PROVISIONING_STATE_SUCCEEDED = "Succeeded"
        private const val POWER_STATE = "PowerState/"
        private const val CACHE_KEY = "key"
        private const val VIRTUAL_MACHINES_RESOURCE_TYPE = "Microsoft.Compute/virtualMachines"
        private const val CONTAINER_INSTANCE_NAMESPACE = "Microsoft.ContainerInstance"
        private const val CONTAINER_GROUPS_RESOURCE_TYPE = "containerGroups"
    }
}
