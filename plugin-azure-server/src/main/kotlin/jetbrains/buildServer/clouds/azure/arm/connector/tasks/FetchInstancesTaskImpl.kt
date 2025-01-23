package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.google.common.cache.CacheBuilder
import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.management.compute.VirtualMachine
import com.microsoft.azure.management.compute.implementation.VirtualMachineInner
import com.microsoft.azure.management.compute.implementation.VirtualMachineInstanceViewInner
import com.microsoft.azure.management.containerinstance.ContainerGroup
import com.microsoft.azure.management.network.PublicIPAddress
import com.microsoft.azure.management.resources.ProvisioningState
import com.microsoft.azure.management.resources.fluentcore.arm.ResourceUtils
import com.microsoft.azure.management.resources.fluentcore.arm.models.HasId
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.resourceGraph.QueryRequest
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskNotifications
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTask
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_TASKS_FETCHINSTANCES_FULLSTATEAPI_DISABLE
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_TASKS_FETCHINSTANCES_RESOURCEGRAPH_DISABLE
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_THROTTLER_TASK_THROTTLE_TIMEOUT_SEC
import jetbrains.buildServer.clouds.azure.arm.throttler.register
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
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

class FetchInstancesTaskImpl(
    notifications: AzureTaskNotifications
) : AzureThrottlerCacheableTask<AzureApi, FetchInstancesTaskParameter, List<FetchInstancesTaskInstanceDescriptor>> {
    private val myTimeoutInSeconds = AtomicLong(60)
    private val myIpAddresses = AtomicReference<Array<IPAddressDescriptor>>(emptyArray())
    private val myInstancesCache = createCache()
    private val myNotifiedInstances = ConcurrentHashMap.newKeySet<String>()
    private val myLastUpdatedDate = AtomicReference<LocalDateTime>(LocalDateTime.MIN)

    init {
        notifications.register<AzureTaskVirtualMachineStatusChangedEventArgs> { args ->
            updateVirtualMachine(args.api, args.virtualMachine)
        }

        notifications.register<AzureTaskDeploymentStatusChangedEventArgs> { args ->
            updateDeployment(args)
        }
        notifications.register<AzureTaskVirtualMachineRemoved> {
            updateVirtualMachine(it.api, it.taskContext, it.resourceId, true)
        }
        notifications.register<AzureTaskVirtualMachineCreated> {
            updateVirtualMachine(it.api, it.taskContext, it.resourceId, false)
        }
    }

    override fun create(api: AzureApi, taskContext: AzureTaskContext, parameter: FetchInstancesTaskParameter): Single<List<FetchInstancesTaskInstanceDescriptor>> {
        if (StringUtil.isEmptyOrSpaces(api.subscriptionId())) {
            LOG.debug("FetchInstancesTask returns empty list. Subscription is empty")
            return Single
                    .just(emptyList<FetchInstancesTaskInstanceDescriptor>());
        }

        LOG.debug("Start fetching instances. COrellationId: ${taskContext.corellationId}")

        val taskSource = if (TeamCityProperties.getBoolean(TEAMCITY_CLOUDS_AZURE_TASKS_FETCHINSTANCES_RESOURCEGRAPH_DISABLE))
            createTask(api, taskContext, parameter)
        else
            createResourceGraphTask(api, taskContext, parameter)

        return taskSource
            .map { getFilteredResources(parameter) }
    }

    private fun createResourceGraphTask(api: AzureApi, taskContext: AzureTaskContext, parameter: FetchInstancesTaskParameter): Single<Unit> {
        val query = QueryRequest(
            FETCH_INSTANCES_SCRIPT
                .replace("@@TeamCityServer", parameter.serverId)
        )
        return api
            .resourceGraph()
            .resources()
            .poolResourcesAsync(query)
            .flatMapIterable { table ->
                table.rows.map {
                    val resourceId = it.getStringValue("resourceId", isRequired = true)!!
                    val resourceName = it.getStringValue("resourceName", isRequired = true)!!
                    var provisioningState = it.getStringValue("provisioningState", isRequired = true)
                    val startDate = it.getDateTimeValue("startDate", isRequired = false)?.toDate()
                    val powerState = it.getStringValue("powerStateCode", isRequired = false)
                    val resourceTags = it.getMapValue("resourceTags", isRequired = false) ?: emptyMap()
                    val publicIpAddress = it.getStringValue("publicIpAddress", isRequired = false)
                    val networkInterfaceId = it.getStringValue("nicId", isRequired = false)

                    if (resourceTags.containsKey(AzureConstants.TAG_INVESTIGATION)) {
                        LOG.debug("Virtual machine $resourceName is marked by ${AzureConstants.TAG_INVESTIGATION} tag")
                        provisioningState = "Investigation"
                    }

                    InstanceDescriptor(
                        resourceId,
                        resourceName,
                        resourceTags,
                        provisioningState,
                        startDate,
                        powerState,
                        null,
                        networkInterfaceId,
                        publicIpAddress,
                        getCurrentUTC()
                    )
                }
            }
            .toList()
            .map { instances ->
                LOG.debug("Fetched ${instances.count()} VMs and containers. CorellationId:${taskContext.corellationId}")
                updateCache(myIpAddresses.get().toList(), instances)
            }
            .toSingle()
    }

    private fun createTask(api: AzureApi, taskContext: AzureTaskContext, parameter: FetchInstancesTaskParameter): Single<Unit> {
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
                .flatMap { vm ->
                    machineInstancesImpl.flatMap { vmStatusesMap ->
                        fetchVirtualMachineWithCache(vm, vmStatusesMap[vm.id()])
                    }
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
                updateCache(ipList, instances)
            }
            .toSingle()
    }

    private fun updateCache(ipList: List<IPAddressDescriptor>, instances: List<InstanceDescriptor>) {
        myLastUpdatedDate.set(getCurrentUTC())
        myIpAddresses.set(ipList.toTypedArray())

        val newInstances = instances.associateBy { it.id.lowercase() }
        myInstancesCache.putAll(newInstances)

        val keysInCache = myInstancesCache.asMap().keys.toSet()
        val notifiedInstances = HashSet(myNotifiedInstances)
        myInstancesCache.invalidateAll(keysInCache.minus(newInstances.keys.plus(notifiedInstances)))
        myNotifiedInstances.removeAll(notifiedInstances)
    }

    private fun getLiveInstanceFromSnapshot(resource: HasId?): InstanceDescriptor? {
        if (resource?.id() == null) return null

        val instance = myInstancesCache.getIfPresent(resource.id().lowercase())
        if (instance == null) return null

        if (needUpdate(instance.lastUpdatedDate)) return null
        return instance
    }

    private fun fetchIPAddresses(api: AzureApi): Observable<MutableList<IPAddressDescriptor>> {
        return api
                .publicIPAddresses()
                .listAsync()
                .filter { !it.ipAddress().isNullOrBlank() }
                .map { IPAddressDescriptor(it.name(), it.resourceGroupName(), it.ipAddress(), getAssignedNetworkInterfaceId(it)) }
                .toList()
                .takeLast(1)
                .onErrorReturn {
                    val message = "Failed to get list of public ip addresses: " + it.message
                    LOG.debug(message, it)
                    emptyList()
                }
    }

    private fun updateContainer(api: AzureApi, containerId: String, isDeleting: Boolean) : Observable<Unit> {
        if (isDeleting) {
            myNotifiedInstances.remove(containerId.lowercase())
            myInstancesCache.invalidate(containerId.lowercase())
            return Observable.just(Unit)
        }
        return api.containerGroups()
                .getByIdAsync(containerId)
                .map { containerGroup: ContainerGroup? -> containerGroup?.let { fetchContainer(it) } }
                .flatMap { instance: InstanceDescriptor? ->
                    fetchIPAddresses(api).map { ipList ->
                        myIpAddresses.set(ipList.toTypedArray())
                        if (instance != null) {
                            myNotifiedInstances.add(instance.id.lowercase())
                            myInstancesCache.put(instance.id.lowercase(), instance)
                        } else {
                            myNotifiedInstances.remove(containerId.lowercase())
                            myInstancesCache.invalidate(containerId.lowercase())
                        }
                    }
                }
                .takeLast(1)
                .defaultIfEmpty(Unit)
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
                null,
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
                            null,
                            getCurrentUTC())
                    instance
                }
    }

    private fun updateDeployment(args: AzureTaskDeploymentStatusChangedEventArgs): Observable<Unit> {
        val dependency = args.dependencies.firstOrNull { it.resourceType() == VIRTUAL_MACHINES_RESOURCE_TYPE }
        if (dependency != null) {
            val virtualMachineId = dependency.id()
            val result = if (args.isDeleting || args.provisioningState == ProvisioningState.SUCCEEDED)
                updateVirtualMachine(args.api, args.taskContext, virtualMachineId, args.isDeleting)
            else
                Observable.just(Unit)

            return result
                .doOnNext {
                    val instance = myInstancesCache.getIfPresent(virtualMachineId.lowercase())
                    args.instance = instance?.let(::createTaskInstanceDescriptor)
                }
        }

        val containerProvider = args.providers.firstOrNull { it.namespace() == CONTAINER_INSTANCE_NAMESPACE }
        if (containerProvider != null) {
            val containerId = ResourceUtils.constructResourceId(
                ResourceUtils.subscriptionFromResourceId(args.deploymentId),
                ResourceUtils.groupFromResourceId(args.deploymentId),
                containerProvider.namespace(),
                CONTAINER_GROUPS_RESOURCE_TYPE,
                args.deploymentName,
                "")

            return updateContainer(args.api, containerId, args.isDeleting)
                .doOnNext {
                    val instance = myInstancesCache.getIfPresent(containerId.lowercase())
                    args.instance = instance?.let(::createTaskInstanceDescriptor)
                }
        }
        return Observable.just(Unit)
    }

    private fun updateVirtualMachine(api: AzureApi, virtualMachine: VirtualMachine) : Observable<Unit> =
        fetchVirtualMachine(virtualMachine)
            .flatMap { instance ->
                fetchIPAddresses(api).map { ipList ->
                    myIpAddresses.set(ipList.toTypedArray())
                    myInstancesCache.put(instance.id.lowercase(), instance)
                }
            }
            .takeLast(1)
            .defaultIfEmpty(Unit)

    private fun updateVirtualMachine(api: AzureApi, taskContext: AzureTaskContext, virtualMachineId: String, isDeleting: Boolean) : Observable<Unit> {
        if (isDeleting) {
            myNotifiedInstances.remove(virtualMachineId.lowercase())
            myInstancesCache.invalidate(virtualMachineId.lowercase())
            return Observable.just(Unit)
        }
        return taskContext
            .getDeferralSequence()
            .flatMap {
                api.virtualMachines()
                    .getByIdAsync(virtualMachineId)
                    .flatMap { vm: VirtualMachine? -> if (vm != null) fetchVirtualMachine(vm) else Observable.just(null) }
                    .flatMap { instance ->
                        taskContext
                            .getDeferralSequence()
                            .flatMap { fetchIPAddresses(api) }
                            .map { ipList ->
                                myIpAddresses.set(ipList.toTypedArray())
                                if (instance != null) {
                                    myNotifiedInstances.add(instance.id.lowercase())
                                    myInstancesCache.put(instance.id.lowercase(), instance)
                                } else {
                                    myNotifiedInstances.remove(virtualMachineId.lowercase())
                                    myInstancesCache.invalidate(virtualMachineId.lowercase())
                                }
                            }
                    }
                    .take(1)
                    .defaultIfEmpty(Unit)
            }
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
                .map(::createTaskInstanceDescriptor)
                .toList()
    }

    private fun createTaskInstanceDescriptor(instance: InstanceDescriptor) =
        FetchInstancesTaskInstanceDescriptor(
            instance.id,
            instance.name,
            instance.tags,
            getPublicIPAddressOrDefault(instance),
            instance.provisioningState,
            instance.startDate,
            instance.powerState,
            instance.error
        )

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
        instance.publicIpAddress?.let { return it }

        val ipAddresses = myIpAddresses.get()
        if (ipAddresses.isEmpty()) return null

        val ips = ipAddresses.filter {
            instance.primaryNetworkInterfaceId != null && it.assignedNetworkInterfaceId == instance.primaryNetworkInterfaceId
        }

        if (ips.isNotEmpty()) {
            val ip = ips.first().ipAddress
            if (!ip.isNullOrBlank()) {
                return ip
            }
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
            val publicIpAddress: String?,
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
        private const val PROVISIONING_STATE = "ProvisioningState/"
        private const val POWER_STATE = "PowerState/"
        private const val VIRTUAL_MACHINES_RESOURCE_TYPE = "Microsoft.Compute/virtualMachines"
        private const val CONTAINER_INSTANCE_NAMESPACE = "Microsoft.ContainerInstance"
        private const val CONTAINER_GROUPS_RESOURCE_TYPE = "containerGroups"

        private val FETCH_INSTANCES_SCRIPT = AzureUtils.getResourceAsString("/queries/fetch_instances.kusto")
    }
}
