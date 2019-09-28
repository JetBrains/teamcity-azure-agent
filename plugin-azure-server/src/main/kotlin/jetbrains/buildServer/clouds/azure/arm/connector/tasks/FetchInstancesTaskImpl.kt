package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.compute.VirtualMachineInstanceView
import com.microsoft.azure.management.network.PublicIPAddress
import com.microsoft.azure.management.resources.fluentcore.arm.models.Resource
import jetbrains.buildServer.clouds.azure.arm.AzureCloudDeployTarget
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils.getResourceGroup
import jetbrains.buildServer.clouds.azure.arm.utils.isVmInstance
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import rx.Observable
import rx.Single
import rx.internal.util.SubscriptionList
import java.util.*

data class FetchInstancesTaskInstanceDescriptor (
        val imageId: String,
        val name: String,
        val tags: Map<String, String>,
        val publicIpAddress: String?,
        val provisioningState: String?,
        val startDate: Date?,
        val powerState: String?,
        val error: TypedCloudErrorInfo?
        )

data class FetchInstancesTaskParameter(val serverId: String?, val profileId: String?, val images: List<FetchInstancesTaskImageDescriptor>)
data class FetchInstancesTaskImageDescriptor(val imageId: String, val imageDetails: AzureCloudImageDetails)

class FetchInstancesTaskImpl : AzureThrottlerCacheableTaskBaseImpl<FetchInstancesTaskParameter, List<FetchInstancesTaskInstanceDescriptor>>() {
    override fun create(api: Azure, parameter: FetchInstancesTaskParameter): Single<List<FetchInstancesTaskInstanceDescriptor>> {
        val ipAddresses = if (parameter.images.any { it.imageDetails.vmPublicIp == true } ) {
            api
                    .publicIPAddresses()
                    .listAsync()
                    .toList()
                    .takeLast(1)
                    .onErrorReturn {
                        val message = "Failed to get list of public ip addresses: " + it.message
                        LOG.debug(message, it)
                        emptyList()
                    }
                    .replay(1)
        } else {
            Observable.just<List<PublicIPAddress>>(emptyList()).publish()
        }

        val machineInstances = if (parameter.images.any { it.imageDetails.isVmInstance() }) {
            api
                    .virtualMachines()
                    .listAsync()
                    .map { vm -> vm to parameter.images.find { !shouldIgnoreResource(vm, it.imageDetails, parameter.serverId, parameter.profileId) } }
                    .filter { (_, imageDescriptor) -> imageDescriptor != null }
                    .flatMap { (vm, imageDescriptor) ->
                        val tags = vm.tags()
                        val name = vm.name()
                        vm.refreshInstanceViewAsync()
                                .map { getInstanceState(it, tags, name) }
                                .onErrorReturn {
                                    LOG.debug("Failed to get status of virtual machine '$name': $it.message", it)
                                    InstanceViewState(null, null, null, it)
                                }
                                .withLatestFrom(ipAddresses) { instanceView, ipAddresses ->
                            val publicIp = getPublicIPAddressOrDefault(imageDescriptor!!.imageDetails, name, ipAddresses)
                            val instance = FetchInstancesTaskInstanceDescriptor(
                                    imageDescriptor.imageId,
                                    name,
                                    tags,
                                    publicIp,
                                    instanceView.provisioningState,
                                    instanceView.startDate,
                                    instanceView.powerState,
                                    if (instanceView.error != null) TypedCloudErrorInfo.fromException(instanceView.error) else null)
                            instance
                        }}
        } else {
            Observable.empty()
        }

        val containerInstances = if (parameter.images.any { !it.imageDetails.isVmInstance() }) {
            api
                    .containerGroups()
                    .listAsync()
                    .map { containerGroup -> containerGroup to parameter.images.find { !shouldIgnoreResource(containerGroup, it.imageDetails, parameter.serverId, parameter.profileId) } }
                    .filter { (_, imageDescriptor) -> imageDescriptor != null }
                    .map { (containerGroup, imageDescriptor) ->
                        val state = containerGroup.containers()[containerGroup.name()]?.instanceView()?.currentState()
                        val startDate = state?.startTime()?.toDate()
                        val powerState = state?.state()
                        val instance = FetchInstancesTaskInstanceDescriptor(
                                imageDescriptor!!.imageId,
                                containerGroup.name(),
                                containerGroup.tags(),
                                null,
                                null,
                                startDate,
                                powerState,
                                null)
                        instance
                    }
        } else {
            Observable.empty()
        }

        val subscriptionList = SubscriptionList()
        subscriptionList.add(ipAddresses.connect())

        return machineInstances
                .mergeWith(containerInstances)
                .toList()
                .takeLast(1)
                .doAfterTerminate{ subscriptionList.clear() }
                .toSingle()
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

    private fun shouldIgnoreResource(resource: Resource, details: AzureCloudImageDetails, serverId: String?, profileId: String?): Boolean {
        val isVm = details.isVmInstance()
        val name = resource.name()
        val tags = resource.tags()
        val id = resource.id()
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

        val resourceServerId = tags[AzureConstants.TAG_SERVER]
        if (!resourceServerId.equals(serverId, true)) {
            LOG.debug("Ignore $resourceTypeLogName with invalid server tag $resourceServerId")
            return true
        }

        val resourceProfileId = tags[AzureConstants.TAG_PROFILE]
        if (!resourceProfileId.equals(profileId, true)) {
            LOG.debug("Ignore $resourceTypeLogName with invalid profile tag $resourceProfileId")
            return true
        }

        val sourceName = tags[AzureConstants.TAG_SOURCE]
        if (!sourceName.equals(details.sourceId, true)) {
            LOG.debug("Ignore $resourceTypeLogName with invalid source tag $sourceName")
            return true
        }

        return false
    }

    private fun getPublicIPAddressOrDefault(details: AzureCloudImageDetails, name: String, ipAddresses: List<PublicIPAddress>): String? {
        if (details.vmPublicIp != true) return null

        val groupId = getResourceGroup(details, name)
        val pipName = name + PUBLIC_IP_SUFFIX
        val ips = ipAddresses.filter { it.resourceGroupName() == groupId && it.name() == pipName }

        if (ips.isNotEmpty()) {
            for (ip in ips) {
                LOG.debug("Received public ip ${ip.ipAddress()} ($ip) for virtual machine $name, pip $pipName")
            }

            val ip = ips.first().ipAddress()
            if (!ip.isNullOrBlank()) {
                return ip
            }
        } else {
            LOG.debug("No public ip received for virtual machine $name")
        }
        return null
    }

    data class InstanceViewState (val provisioningState: String?, val startDate: Date?, val powerState: String?, val error: Throwable?)

    companion object {
        private val LOG = Logger.getInstance(FetchInstancesTaskImpl::class.java.name)
        private const val PUBLIC_IP_SUFFIX = "-pip"
        private const val PROVISIONING_STATE = "ProvisioningState/"
        private const val POWER_STATE = "PowerState/"
    }
}
