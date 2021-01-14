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
