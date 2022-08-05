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
import com.microsoft.azure.management.compute.implementation.*
import com.microsoft.azure.management.containerinstance.ContainerGroup
import com.microsoft.azure.management.network.PublicIPAddress
import com.microsoft.azure.management.resources.fluentcore.arm.ResourceUtils
import com.microsoft.azure.management.resources.fluentcore.arm.models.HasId
import jetbrains.buildServer.clouds.azure.arm.AzureCloudDeployTarget
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.throttler.*
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.util.StringUtil
import rx.Observable
import rx.Single
import java.time.Clock
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.HashSet

data class FetchInstancesTaskInstanceDescriptor (
        val id: String,
        val name: String,
        val tags: Map<String, String>,
        val publicIpAddress: String?,
        val provisioningState: String?,
        val startDate: Date?,
        val powerState: String?,
        val error: TypedCloudErrorInfo?
        )

data class FetchInstancesTaskParameter(val serverId: String)

class FetchInstancesTaskImpl(private val myNotifications: AzureTaskNotifications) : AzureThrottlerCacheableTask<Azure, FetchInstancesTaskParameter, List<FetchInstancesTaskInstanceDescriptor>> {
    private val myTimeoutInSeconds = AtomicLong(60)
    private val myIpAddresses = AtomicReference<Array<IPAddressDescriptor>>(emptyArray())
    private val myInstancesCache = createCache()
    private val myNotifiedInstances = ConcurrentHashMap.newKeySet<String>()
    private val myLastUpdatedDate = AtomicReference<LocalDateTime>(LocalDateTime.MIN)

    init {
        myNotifications.register<AzureTaskVirtualMachineStatusChangedEventArgs> {
            updateVirtualMachine(it.api, it.virtualMachine)
        }
        myNotifications.register<AzureTaskDeploymentStatusChangedEventArgs> { args ->
            val dependency = args.deployment.dependencies().firstOrNull { it.resourceType() == VIRTUAL_MACHINES_RESOURCE_TYPE }
            if (dependency != null) {
                if (args.isDeleting) {
                    updateVirtualMachine(args.api, dependency.id(), args.isDeleting)
                } else {
                    if (args.deployment.provisioningState() == PROVISIONING_STATE_SUCCEEDED) {
                        updateVirtualMachine(args.api, dependency.id(), args.isDeleting)
                    }
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

                updateContainer(args.api, containerId, args.isDeleting)
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

        val machineInstancesImpl =
                if (TeamCityProperties.getBoolean(TEAMCITY_CLOUDS_AZURE_TASKS_FETCHINSTANCES_FULLSTATEAPI_DISABLE))
                    Observable.just(emptyMap())
                else api.virtualMachines().inner()
                    .listAsync("true")
                    .flatMap { x -> Observable.from(x.items()) }
                    .toMap { vm -> vm.id() }

        val machineInstances =
            api
                    .virtualMachines()
                    .listAsync()
                    .materialize()
                    .filter {
                        if (it.isOnError) LOG.warnAndDebugDetails("Could not read VM state:", it.throwable)
                        !it.isOnError
                    }
                    .dematerialize<VirtualMachine>()
                    .withLatestFrom(machineInstancesImpl) { vm, vmStatusesMap ->
                        vm to vmStatusesMap[vm.id()]
                    }
                    .flatMap { (vm, vmStatus) ->
                        fetchVirtualMachineWithCache(vm, vmStatus)
                    }

        val containerInstances =
            api
                    .containerGroups()
                    .listAsync()
                    .materialize()
                    .filter {
                        if (it.isOnError) LOG.warnAndDebugDetails("Could not read container state:", it.throwable)
                        !it.isOnError
                    }
                    .dematerialize<ContainerGroup>()
                    .map { getLiveInstanceFromSnapshot(it) ?: fetchContainer(it) }

        return machineInstances
                .mergeWith(containerInstances)
                .toList()
                .takeLast(1)
                .withLatestFrom(ipAddresses) { instances, ipList ->
                    myLastUpdatedDate.set(getCurrentUTC())
                    myIpAddresses.set(ipList.toTypedArray())

                    val newInstances = instances.associateBy { it.id.toLowerCase() }
                    myInstancesCache.putAll(newInstances)

                    val keysInCache = myInstancesCache.asMap().keys.toSet()
                    val notifiedInstances = HashSet(myNotifiedInstances)
                    myInstancesCache.invalidateAll(keysInCache.minus(newInstances.keys.plus(notifiedInstances)))
                    myNotifiedInstances.removeAll(notifiedInstances)

                    Unit
                }
                .map { getFilteredResources(parameter) }
                .toSingle()
    }

    private fun getLiveInstanceFromSnapshot(resource: HasId?): InstanceDescriptor? {
        if (resource?.id() == null) return null

        val instance = myInstancesCache.getIfPresent(resource.id().toLowerCase())
        if (instance == null) return null

        if (needUpdate(instance.lastUpdatedDate)) return null
        return instance
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

    private fun updateContainer(api: Azure, containerId: String, isDeleting: Boolean) {
        if (isDeleting) {
            myNotifiedInstances.remove(containerId.toLowerCase())
            myInstancesCache.invalidate(containerId.toLowerCase())
            return
        }
        api.containerGroups()
                .getByIdAsync(containerId)
                .map { it?.let { fetchContainer(it) } }
                .withLatestFrom(fetchIPAddresses(api)) {
                    instance, ipList ->
                    myIpAddresses.set(ipList.toTypedArray())
                    if (instance != null) {
                        myNotifiedInstances.add(instance.id.toLowerCase())
                        myInstancesCache.put(instance.id.toLowerCase(), instance)
                    } else {
                        myNotifiedInstances.remove(containerId.toLowerCase())
                        myInstancesCache.invalidate(containerId.toLowerCase())
                    }
                }
                .take(1)
                .toCompletable()
                .await()
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
                null,
                getCurrentUTC())
        return instance
    }

    private fun fetchVirtualMachineWithCache(vm: VirtualMachine, vmStatus: VirtualMachineInner?): Observable<InstanceDescriptor> {
        if (vmStatus?.instanceView() == null) {
            val cachedInstance = getLiveInstanceFromSnapshot(vm)
            if (cachedInstance != null)
                return Observable.just(cachedInstance)
            else
                return fetchVirtualMachine(vm)
        }
        return fetchVirtualMachine(vm, vmStatus.instanceView())
    }

    private fun fetchVirtualMachine(vm: VirtualMachine, instanceView: VirtualMachineInstanceViewInner): Observable<InstanceDescriptor> {
        val tags = vm.tags()
        val name = vm.name()
        val instanceState = getInstanceState(instanceView, tags, name)

        LOG.debug("Reading state of virtual machine '$name' using full state API")

        return Observable.just(InstanceDescriptor(
                vm.id(),
                vm.name(),
                vm.tags(),
                instanceState.provisioningState,
                instanceState.startDate,
                instanceState.powerState,
                null,
                vm.primaryNetworkInterfaceId(),
                getCurrentUTC()
        ))
    }

    private fun fetchVirtualMachine(vm: VirtualMachine): Observable<InstanceDescriptor> {
        val tags = vm.tags()
        val id = vm.id()
        val name = vm.name()
        LOG.debug("Reading state of virtual machine '$name'")

        return vm.refreshInstanceViewAsync()
                .map { getInstanceState(it.inner(), tags, name) }
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
                            vm.primaryNetworkInterfaceId(),
                            getCurrentUTC())
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
                .toCompletable()
                .await()
    }

    private fun updateVirtualMachine(api: Azure, virtualMachineId: String, isDeleting: Boolean) {
        if (isDeleting) {
            myNotifiedInstances.remove(virtualMachineId.toLowerCase())
            myInstancesCache.invalidate(virtualMachineId.toLowerCase())
            return
        }
        api.virtualMachines()
                .getByIdAsync(virtualMachineId)
                .flatMap { if (it != null) fetchVirtualMachine(it) else Observable.just(null) }
                .withLatestFrom(fetchIPAddresses(api)) {
                    instance, ipList ->
                    myIpAddresses.set(ipList.toTypedArray())
                    if (instance != null) {
                        myNotifiedInstances.add(instance.id.toLowerCase())
                        myInstancesCache.put(instance.id.toLowerCase(), instance)
                    } else {
                        myNotifiedInstances.remove(virtualMachineId.toLowerCase())
                        myInstancesCache.invalidate(virtualMachineId.toLowerCase())
                    }
                }
                .take(1)
                .toCompletable()
                .await()
    }

    private fun getAssignedNetworkInterfaceId(publicIpAddress: PublicIPAddress?): String? {
        if (publicIpAddress == null || !publicIpAddress.hasAssignedNetworkInterface()) return null
        val refId: String = publicIpAddress.inner().ipConfiguration().id()
        val parentId = ResourceUtils.parentResourceIdFromResourceId(refId)
        return parentId
    }


    override fun getFromCache(parameter: FetchInstancesTaskParameter): List<FetchInstancesTaskInstanceDescriptor>? {
        return if (needUpdate(myLastUpdatedDate.get())) null else getFilteredResources(parameter)
    }

    override fun needCacheUpdate(parameter: FetchInstancesTaskParameter): Boolean {
        return needUpdate(myLastUpdatedDate.get())
    }

    override fun checkThrottleTime(parameter: FetchInstancesTaskParameter): Boolean {
        val lastUpdatedDateTime = myLastUpdatedDate.get()
        return lastUpdatedDateTime.plusSeconds(getTaskThrottleTime()) < getCurrentUTC()
    }

    override fun areParametersEqual(parameter: FetchInstancesTaskParameter, other: FetchInstancesTaskParameter): Boolean {
        return parameter == other
    }

    private fun getTaskThrottleTime(): Long {
        return TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_THROTTLER_TASK_THROTTLE_TIMEOUT_SEC, 10)
    }

    private fun getFilteredResources(filter: FetchInstancesTaskParameter): List<FetchInstancesTaskInstanceDescriptor> {
        return myInstancesCache.asMap().values
                .filter {
                    val resourceServerId = it.tags[AzureConstants.TAG_SERVER]
                    resourceServerId.isNullOrEmpty() || filter.serverId.equals(resourceServerId, true).also {
                        if (!it) LOG.debug("Ignore resource with invalid server tag $resourceServerId")
                    }
                }
                .map { instance ->
                    FetchInstancesTaskInstanceDescriptor(
                        instance.id,
                        instance.name,
                        instance.tags,
                        getPublicIPAddressOrDefault(instance),
                        instance.provisioningState,
                        instance.startDate,
                        instance.powerState,
                        instance.error
                ) }
                .toList()
    }

    override fun invalidateCache() {
        myInstancesCache.invalidateAll()
        myIpAddresses.set(emptyArray())
        myNotifiedInstances.clear()
    }

    private fun getInstanceState(instanceView: VirtualMachineInstanceViewInner, tags: Map<String, String>, name: String): InstanceViewState {
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

    private fun getPublicIPAddressOrDefault(instance: InstanceDescriptor): String? {
        var ipAddresses = myIpAddresses.get()
        if (ipAddresses.isEmpty()) return null

        val name = instance.name
        val ips = ipAddresses.filter {
            instance.primaryNetworkInterfaceId != null && it.assignedNetworkInterfaceId == instance.primaryNetworkInterfaceId
        }

        if (ips.isNotEmpty()) {
            for (ip in ips) {
                LOG.debug("Received public ip ${ip.ipAddress} for virtual machine $name, assignedNetworkInterfaceId ${ip.assignedNetworkInterfaceId}")
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

    private fun getCurrentUTC() = LocalDateTime.now(Clock.systemUTC())

    private fun needUpdate(lastUpdatedDate: LocalDateTime) : Boolean {
        return lastUpdatedDate.plusSeconds(myTimeoutInSeconds.get()) < getCurrentUTC()
    }

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
            val primaryNetworkInterfaceId: String?,
            val lastUpdatedDate: LocalDateTime
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
