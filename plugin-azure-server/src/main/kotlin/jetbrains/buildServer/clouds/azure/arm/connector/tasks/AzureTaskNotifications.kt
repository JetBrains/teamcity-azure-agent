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

import com.microsoft.azure.management.compute.VirtualMachine
import com.microsoft.azure.management.resources.Dependency
import com.microsoft.azure.management.resources.Deployment
import com.microsoft.azure.management.resources.Providers
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
