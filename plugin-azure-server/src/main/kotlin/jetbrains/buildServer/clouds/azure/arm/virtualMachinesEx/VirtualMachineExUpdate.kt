package jetbrains.buildServer.clouds.azure.arm.virtualMachinesEx

import com.fasterxml.jackson.annotation.JsonProperty
import com.microsoft.azure.management.compute.VirtualMachineUpdate
import com.microsoft.rest.serializer.JsonFlatten

@JsonFlatten
class VirtualMachineExUpdate : VirtualMachineUpdate() {
    @JsonProperty("properties.userData")
    private val userData: String? = null
    public fun userData(): String? = userData
}
