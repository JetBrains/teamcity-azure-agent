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
        val CreateDeployment = AzureTaskDescriptorImpl(Values.CreateDeployment, { CreateDeploymentTaskImpl() })
        val CreateResourceGroup = AzureTaskDescriptorImpl(Values.CreateResourceGroup, { CreateResourceGroupTaskImpl() })
        val DeleteResourceGroup = AzureTaskDescriptorImpl(Values.DeleteResourceGroup, { DeleteResourceGroupTaskImpl() })
        val StopVirtualMachine = AzureTaskDescriptorImpl(Values.StopVirtualMachine, { StopVirtualMachineTaskImpl() })
        val StartVirtualMachine = AzureTaskDescriptorImpl(Values.StartVirtualMachine, { StartVirtualMachineTaskImpl() })
        val RestartVirtualMachine = AzureTaskDescriptorImpl(Values.RestartVirtualMachine, { RestartVirtualMachineTaskImpl() })
        val DeleteDeployment = AzureTaskDescriptorImpl(Values.DeleteDeployment, { DeleteDeploymentTaskImpl() })
    }
}
