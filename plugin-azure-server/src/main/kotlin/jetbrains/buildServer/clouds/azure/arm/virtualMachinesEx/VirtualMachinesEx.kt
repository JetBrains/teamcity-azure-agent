package jetbrains.buildServer.clouds.azure.arm.virtualMachinesEx

import com.microsoft.azure.management.compute.implementation.ComputeManager
import com.microsoft.azure.management.resources.fluentcore.arm.collection.SupportsGettingByResourceGroup
import com.microsoft.azure.management.resources.fluentcore.arm.models.HasManager
import com.microsoft.azure.management.resources.fluentcore.model.HasInner

class VirtualMachinesEx(
    private val inner: VirtualMachinesExInner
) : HasInner<VirtualMachinesExInner> {
    override fun inner(): VirtualMachinesExInner = inner
}
