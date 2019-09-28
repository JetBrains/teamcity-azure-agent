package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

data class FetchVirtualMachinesTaskVirtualMachineDescriptor(
        val id: String,
        val name: String,
        val groupName: String,
        val isManagedDiskEnabled: Boolean,
        val osUnmanagedDiskVhdUri: String?)

class FetchVirtualMachinesTaskImpl : AzureThrottlerCacheableTaskBaseImpl<Unit, List<FetchVirtualMachinesTaskVirtualMachineDescriptor>>() {
    override fun create(api: Azure, parameter: Unit): Single<List<FetchVirtualMachinesTaskVirtualMachineDescriptor>> {
        return api
                .virtualMachines()
                .listAsync()
                .map {
                    FetchVirtualMachinesTaskVirtualMachineDescriptor(
                            it.id(),
                            it.name(),
                            it.resourceGroupName(),
                            it.isManagedDiskEnabled,
                            it.osUnmanagedDiskVhdUri()
                    )
                }
                .toList()
                .last()
                .toSingle()
    }
}
