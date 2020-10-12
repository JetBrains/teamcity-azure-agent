/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

class AzureThrottlerReadTasks {
    enum class Values {
        FetchResourceGroups,
        FetchVirtualMachines,
        FetchInstances,
        FetchCustomImages,
        FetchStorageAccounts,
        FetchVirtualMachineSizes,
        FetchSubscriptions,
        FetchLocations,
        FetchNetworks,
        FetchServices,
    }

    companion object {
        val FetchResourceGroups = AzureTaskDescriptorImpl(Values.FetchResourceGroups, { FetchResourceGroupsMapTaskImpl() })
        val FetchVirtualMachines = AzureTaskDescriptorImpl(Values.FetchVirtualMachines, { FetchVirtualMachinesTaskImpl() })
        val FetchInstances = AzureTaskDescriptorImpl(Values.FetchInstances, { notifications -> FetchInstancesTaskImpl(notifications) })
        val FetchCustomImages = AzureTaskDescriptorImpl(Values.FetchCustomImages, { FetchCustomImagesTaskImpl() })
        val FetchStorageAccounts = AzureTaskDescriptorImpl(Values.FetchStorageAccounts, { FetchStorageAccountsTaskImpl() })
        val FetchVirtualMachineSizes = AzureTaskDescriptorImpl(Values.FetchVirtualMachineSizes, { FetchVirtualMachineSizesTaskImpl() })
        val FetchSubscriptions = AzureTaskDescriptorImpl(Values.FetchSubscriptions, { FetchSubscriptionsTaskImpl() })
        val FetchLocations = AzureTaskDescriptorImpl(Values.FetchLocations, { FetchLocationsTaskImpl() })
        val FetchNetworks = AzureTaskDescriptorImpl(Values.FetchNetworks, { FetchNetworksTaskImpl() })
        val FetchServices = AzureTaskDescriptorImpl(Values.FetchServices, { FetchServicesTaskImpl() })
    }
}
