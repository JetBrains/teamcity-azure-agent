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

import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.compute.VirtualMachine
import com.microsoft.azure.management.resources.Deployment
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskEventArgs
import kotlin.reflect.KClass

class AzureTaskVirtualMachineStatusChangedEventArgs(override val api: Azure, val virtualMachine: VirtualMachine) : AzureTaskEventArgs {
}

class AzureTaskDeploymentStatusChangedEventArgs(override val api: Azure, val deployment: Deployment, val isDeleting: Boolean = false) : AzureTaskEventArgs {
}

