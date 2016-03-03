/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm.connector;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.models.VirtualMachineSize;
import com.microsoft.azure.management.resources.ResourceManagementClient;
import com.microsoft.azure.management.resources.ResourceManagementClientImpl;
import com.microsoft.azure.management.resources.models.ResourceGroup;
import com.microsoft.azure.management.storage.StorageManagementClient;
import com.microsoft.azure.management.storage.StorageManagementClientImpl;
import com.microsoft.azure.management.storage.models.StorageAccount;
import com.microsoft.rest.ServiceResponse;
import com.microsoft.rest.credentials.ServiceClientCredentials;
import jetbrains.buildServer.clouds.CloudException;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImage;
import jetbrains.buildServer.clouds.azure.arm.AzureCloudInstance;
import jetbrains.buildServer.clouds.azure.connector.ActionIdChecker;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Provides azure arm management capabilities.
 */
public class AzureApiConnector implements CloudApiConnector<AzureCloudImage, AzureCloudInstance>, ActionIdChecker {

    private static final int RESOURCES_NUMBER = 100;
    private final ResourceManagementClient myArmClient;
    private final StorageManagementClient myStorageClient;
    private final ComputeManagementClient myComputeClient;

    public AzureApiConnector(@NotNull final String tenantId,
                             @NotNull final String clientId,
                             @NotNull final String secret,
                             @NotNull final String subscriptionId) {
        final ServiceClientCredentials credentials = new ApplicationTokenCredentials(clientId, tenantId, secret, null);

        myArmClient = new ResourceManagementClientImpl(credentials);
        myArmClient.setSubscriptionId(subscriptionId);

        myStorageClient = new StorageManagementClientImpl(credentials);
        myStorageClient.setSubscriptionId(subscriptionId);

        myComputeClient = new ComputeManagementClientImpl(credentials);
        myComputeClient.setSubscriptionId(subscriptionId);
    }

    @Override
    public void ping() throws CloudException {
        getResourceGroups();
    }

    @Override
    public InstanceStatus getInstanceStatus(@NotNull AzureCloudInstance instance) {
        return null;
    }

    @Override
    public Map<String, AzureInstance> listImageInstances(@NotNull AzureCloudImage image) throws CloudException {
        return null;
    }

    @Override
    public Collection<TypedCloudErrorInfo> checkImage(@NotNull AzureCloudImage image) {
        return null;
    }

    @Override
    public Collection<TypedCloudErrorInfo> checkInstance(@NotNull AzureCloudInstance instance) {
        return null;
    }

    @Override
    public boolean isActionFinished(@NotNull String actionId) {
        return false;
    }

    @NotNull
    public List<String> getResourceGroups() throws CloudException {
        final ServiceResponse<List<ResourceGroup>> response;
        try {
            response = myArmClient.getResourceGroupsOperations().list(null, RESOURCES_NUMBER);
        } catch (Throwable e) {
            throw new CloudException("Failed to get list of groups: " + e.getMessage(), e);
        }

        final List<ResourceGroup> resourceGroups = response.getBody();
        final List<String> groups = new ArrayList<String>(resourceGroups.size());
        for (ResourceGroup resourceGroup : resourceGroups) {
            groups.add(resourceGroup.getName());
        }

        return groups;
    }

    @NotNull
    public List<String> getStoragesByGroup(@NotNull final String groupName) {
        final ServiceResponse<List<StorageAccount>> response;
        try {
            response = myStorageClient.getStorageAccountsOperations().listByResourceGroup(groupName);
        } catch (Throwable e) {
            throw new CloudException("Failed to get list of storages: " + e.getMessage(), e);
        }

        final List<StorageAccount> accounts = response.getBody();
        final List<String> storages = new ArrayList<String>(accounts.size());
        for (StorageAccount storageAccount : accounts) {
            storages.add(storageAccount.getName());
        }

        return storages;
    }

    @NotNull
    public List<String> getVmSizesByGroup(String groupName) {
        final ServiceResponse<List<VirtualMachineSize>> response;
        try {
            final ServiceResponse<ResourceGroup> groupResponse = myArmClient.getResourceGroupsOperations().get(groupName);
            final String location = groupResponse.getBody().getLocation();
            response = myComputeClient.getVirtualMachineSizesOperations().list(location);
        } catch (Throwable e) {
            throw new CloudException("Failed to get list of vm sizes: " + e.getMessage(), e);
        }

        final List<VirtualMachineSize> vmSizes = response.getBody();
        final List<String> sizes = new ArrayList<String>(vmSizes.size());
        for (VirtualMachineSize vmSize : vmSizes) {
            sizes.add(vmSize.getName());
        }

        return sizes;
    }
}
