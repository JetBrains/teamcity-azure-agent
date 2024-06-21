package jetbrains.buildServer.clouds.azure.arm.connector.tasks

class AzureThrottlerActionTasks {
    enum class Values {
        CreateDeployment,
        CreateResourceGroup,
        DeleteResourceGroup,
        StopVirtualMachine,
        StartVirtualMachine,
        RestartVirtualMachine,
        DeleteDeployment
    }

    companion object {
        val CreateDeployment = AzureTaskDescriptorImpl(Values.CreateDeployment, { notifications -> CreateDeploymentTaskImpl(notifications) })
        val CreateResourceGroup = AzureTaskDescriptorImpl(Values.CreateResourceGroup, { CreateResourceGroupTaskImpl() })
        val DeleteResourceGroup = AzureTaskDescriptorImpl(Values.DeleteResourceGroup, { DeleteResourceGroupTaskImpl() })
        val StopVirtualMachine = AzureTaskDescriptorImpl(Values.StopVirtualMachine, { notifications -> StopVirtualMachineTaskImpl(notifications) })
        val StartVirtualMachine = AzureTaskDescriptorImpl(Values.StartVirtualMachine, { notifications -> StartVirtualMachineTaskImpl(notifications) })
        val RestartVirtualMachine = AzureTaskDescriptorImpl(Values.RestartVirtualMachine, { notifications -> RestartVirtualMachineTaskImpl(notifications) })
        val DeleteDeployment = AzureTaskDescriptorImpl(Values.DeleteDeployment, { notifications -> DeleteDeploymentTaskImpl(notifications) })
    }
}
