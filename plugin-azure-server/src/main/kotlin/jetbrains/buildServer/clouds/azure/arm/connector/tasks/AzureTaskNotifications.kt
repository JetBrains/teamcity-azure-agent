

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.compute.VirtualMachine
import com.microsoft.azure.management.resources.Dependency
import com.microsoft.azure.management.resources.ProvisioningState
import com.microsoft.azure.management.resources.implementation.ProviderInner
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskEventArgs

class AzureTaskVirtualMachineStatusChangedEventArgs(override val api: AzureApi, val virtualMachine: VirtualMachine) : AzureTaskEventArgs {
}

class AzureTaskDeploymentStatusChangedEventArgs(
    override val api: AzureApi,
    val deploymentId: String,
    val deploymentName: String,
    val provisioningState: ProvisioningState,
    val providers: List<ProviderInner>,
    val dependencies: List<Dependency>,
    val taskContext: AzureTaskContext,
    val isDeleting: Boolean = false
) : AzureTaskEventArgs

class AzureTaskVirtualMachineRemoved(
    override val api: AzureApi,
    val taskContext: AzureTaskContext,
    val resourceId: String,
) : AzureTaskEventArgs

class AzureTaskVirtualMachineCreated(
    override val api: AzureApi,
    val taskContext: AzureTaskContext,
    val resourceId: String,
) : AzureTaskEventArgs
