package jetbrains.buildServer.clouds.azure.arm.virtualMachinesEx

import com.fasterxml.jackson.annotation.JsonProperty
import com.microsoft.azure.management.compute.implementation.VirtualMachineInner
import com.microsoft.rest.serializer.JsonFlatten

@JsonFlatten
class VirtualMachineExInner : VirtualMachineInner() {
    @JsonProperty("userData")
    private val userData: String? = null
    public fun userData(): String? = userData
}
