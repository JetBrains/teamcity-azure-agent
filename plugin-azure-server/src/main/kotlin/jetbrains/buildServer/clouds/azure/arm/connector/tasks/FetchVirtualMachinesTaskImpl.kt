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

import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.compute.OperatingSystemTypes
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

data class FetchVirtualMachinesTaskVirtualMachineDescriptor(
        val id: String,
        val name: String,
        val groupName: String,
        val isManagedDiskEnabled: Boolean,
        val osUnmanagedDiskVhdUri: String?,
        val osType: String?)

class FetchVirtualMachinesTaskImpl : AzureThrottlerCacheableTaskBaseImpl<Unit, List<FetchVirtualMachinesTaskVirtualMachineDescriptor>>() {
    override fun createQuery(api: Azure, parameter: Unit): Single<List<FetchVirtualMachinesTaskVirtualMachineDescriptor>> {
        return api
                .virtualMachines()
                .listAsync()
                .toList()
                .last()
                .map { list ->
                    val result = mutableListOf<FetchVirtualMachinesTaskVirtualMachineDescriptor>()
                    for(vm in list) {
                        try {
                            result.add(
                                    FetchVirtualMachinesTaskVirtualMachineDescriptor(
                                            vm.id(),
                                            vm.name(),
                                            vm.resourceGroupName(),
                                            vm.isManagedDiskEnabled,
                                            if (vm.isManagedDiskEnabled) null else vm.inner().storageProfile().osDisk()?.vhd()?.uri(),
                                            when (vm.osType()) {
                                                OperatingSystemTypes.LINUX -> "Linux"
                                                OperatingSystemTypes.WINDOWS -> "Windows"
                                                else -> vm.osType()?.name
                                            }
                                    )
                            )
                        } catch (exception: Throwable) {
                            LOG.warnAndDebugDetails("Could not read VirtualMachine. Id=${vm.id()}, Name=${vm.name()}, ResourceGroup=${vm.resourceGroupName()}", exception)
                        }
                    }
                    result.toList()
                }
                .toSingle()
    }

    companion object {
        private val LOG = Logger.getInstance(FetchVirtualMachinesTaskImpl::class.java.name)
    }
}
