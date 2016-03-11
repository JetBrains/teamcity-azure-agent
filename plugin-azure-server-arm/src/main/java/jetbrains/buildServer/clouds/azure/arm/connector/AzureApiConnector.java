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
import com.microsoft.azure.management.compute.models.*;
import com.microsoft.azure.management.network.NetworkManagementClient;
import com.microsoft.azure.management.network.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.models.*;
import com.microsoft.azure.management.resources.ResourceManagementClient;
import com.microsoft.azure.management.resources.ResourceManagementClientImpl;
import com.microsoft.azure.management.resources.models.ResourceGroup;
import com.microsoft.azure.management.storage.StorageManagementClient;
import com.microsoft.azure.management.storage.StorageManagementClientImpl;
import com.microsoft.azure.management.storage.models.StorageAccount;
import com.microsoft.azure.management.storage.models.StorageAccountKeys;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.blob.*;
import com.microsoft.rest.ServiceResponse;
import com.microsoft.rest.credentials.ServiceClientCredentials;
import jetbrains.buildServer.clouds.CloudException;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImage;
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails;
import jetbrains.buildServer.clouds.azure.arm.AzureCloudInstance;
import jetbrains.buildServer.clouds.azure.connector.ActionIdChecker;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

/**
 * Provides azure arm management capabilities.
 */
public class AzureApiConnector implements CloudApiConnector<AzureCloudImage, AzureCloudInstance>, ActionIdChecker {

    private static final int RESOURCES_NUMBER = 100;
    private final ResourceManagementClient myArmClient;
    private final StorageManagementClient myStorageClient;
    private final ComputeManagementClient myComputeClient;
    private final NetworkManagementClient myNetworkClient;

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

        myNetworkClient = new NetworkManagementClientImpl(credentials);
        myNetworkClient.setSubscriptionId(subscriptionId);
    }

    @Override
    public void ping() throws CloudException {
        getResourceGroups();
    }

    @Override
    public InstanceStatus getInstanceStatus(@NotNull AzureCloudInstance instance) {
        updateVmStatus(instance);
        return instance.getStatus();
    }

    @Override
    public Map<String, AzureInstance> listImageInstances(@NotNull AzureCloudImage image) throws CloudException {
        final AzureCloudImageDetails imageDetails = image.getImageDetails();
        final Map<String, AzureInstance> instances = new HashMap<String, AzureInstance>();
        final ServiceResponse<List<VirtualMachine>> response;

        try {
            response = myComputeClient.getVirtualMachinesOperations().list(imageDetails.getGroupId());
        } catch (Throwable e) {
            throw new CloudException("Failed to get list of virtual machines: " + e.getMessage(), e);
        }

        for (VirtualMachine virtualMachine : response.getBody()) {
            final Map<String, String> tags = virtualMachine.getTags();
            if (tags.containsKey("teamcity")) {
                final AzureInstance instance = new AzureInstance(virtualMachine);
                instances.put(virtualMachine.getName(), instance);
            }
        }

        return instances;
    }

    @Override
    public Collection<TypedCloudErrorInfo> checkImage(@NotNull AzureCloudImage image) {
        return Collections.emptyList();
    }

    @Override
    public Collection<TypedCloudErrorInfo> checkInstance(@NotNull AzureCloudInstance instance) {
        if (instance.getStatus() != InstanceStatus.ERROR){
            return Collections.emptyList();
        }

        return Collections.singletonList(new TypedCloudErrorInfo("error", "Error occurred while VM provisioning"));
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

    @NotNull
    public String createVm(AzureCloudInstance instance, CloudInstanceUserData tag) throws IOException {
        final AzureCloudImageDetails details = instance.getImage().getImageDetails();
        final ServiceResponse<Boolean> existenceResponse;
        final String groupId = details.getGroupId();
        final String storageId = details.getStorageId();
        final String imagePath = details.getImagePath();
        final String osType = details.getOsType();
        final String namePrefix = details.getVmNamePrefix();
        final String vNetName = namePrefix + "-vnet";
        final String subnetName = namePrefix + "-subnet";
        final String securityGroupName = namePrefix + "-sgn";
        final String vmName = instance.getName();
        final String nicName = vmName + "-net";
        final String osDiskName = vmName + "-os";
        final String osDiskVhdName = String.format("https://%s.blob.core.windows.net/vhds/%s-os.vhd", storageId, vmName);
        final String userImageName = String.format("https://%s.blob.core.windows.net/%s", storageId, imagePath);
        final String customData = new String(Base64.getEncoder().encode(tag.serialize().getBytes()));

        // Check resource group
        try {
            existenceResponse = myArmClient.getResourceGroupsOperations().checkExistence(groupId);
        } catch (Throwable e) {
            throw new CloudException("Failed to check group existence: " + e.getMessage(), e);
        }

        if (!existenceResponse.getBody()) {
            throw new CloudException(String.format("Resource group %s does not exist.", groupId));
        }

        // Check storage
        final ServiceResponse<List<StorageAccount>> storagesResponse;
        try {
            storagesResponse = myStorageClient.getStorageAccountsOperations().listByResourceGroup(groupId);
        } catch (Throwable e) {
            throw new CloudException("Failed to get storage information: " + e.getMessage(), e);
        }

        final StorageAccount storage = CollectionsUtil.findFirst(storagesResponse.getBody(), new Filter<StorageAccount>() {
            @Override
            public boolean accept(@NotNull StorageAccount storage) {
                return storage.getName().equalsIgnoreCase(storageId);
            }
        });
        if (storage == null) {
            throw new CloudException(String.format("Storage %s does not exist in resource group %s", storageId, groupId));
        }

        // Create virtual network
        final String location = storage.getLocation();
        final VirtualNetwork vNet = new VirtualNetwork();
        vNet.setLocation(location);
        vNet.setAddressSpace(new AddressSpace());
        vNet.getAddressSpace().setAddressPrefixes(new ArrayList<String>());
        vNet.getAddressSpace().getAddressPrefixes().add("10.0.0.0/16");
        vNet.setSubnets(new ArrayList<Subnet>());
        Subnet subnet = new Subnet();
        subnet.setName(subnetName);
        subnet.setAddressPrefix("10.0.0.0/24");
        vNet.getSubnets().add(subnet);

        try {
            myNetworkClient.getVirtualNetworksOperations().createOrUpdate(groupId, vNetName, vNet);
            subnet = myNetworkClient.getSubnetsOperations().get(groupId, vNetName, subnetName, null).getBody();
        } catch (Throwable e) {
            throw new CloudException("Failed to create virtual network " + vNetName);
        }

        // Create network interface
        NetworkInterface nic = new NetworkInterface();
        nic.setLocation(location);
        nic.setIpConfigurations(new ArrayList<NetworkInterfaceIPConfiguration>());
        NetworkInterfaceIPConfiguration configuration = new NetworkInterfaceIPConfiguration();
        configuration.setName(nicName + "-config");
        configuration.setPrivateIPAllocationMethod("Dynamic");
        configuration.setSubnet(subnet);
        nic.getIpConfigurations().add(configuration);
        NetworkSecurityGroup networkSecurityGroup = new NetworkSecurityGroup();
        networkSecurityGroup.setLocation(location);
        final ServiceResponse<List<Subnet>> subnetsResponse;
        try {
            subnetsResponse = myNetworkClient.getSubnetsOperations().list(groupId, vNetName);
        } catch (Throwable e) {
            throw new CloudException("Failed to get list of sub networks: " + e.getMessage(), e);
        }

        networkSecurityGroup.setSubnets(subnetsResponse.getBody());

        try {
            networkSecurityGroup = myNetworkClient.getNetworkSecurityGroupsOperations().createOrUpdate(groupId, securityGroupName, networkSecurityGroup).getBody();
        } catch (Throwable e) {
            throw new CloudException("Failed to create network security group: " + e.getMessage(), e);
        }

        nic.setNetworkSecurityGroup(networkSecurityGroup);

        try {
            myNetworkClient.getNetworkInterfacesOperations().createOrUpdate(groupId, nicName, nic);
            nic = myNetworkClient.getNetworkInterfacesOperations().get(groupId, nicName, null).getBody();
        } catch (Throwable e) {
            throw new CloudException("Failed to create network interface: " + e.getMessage(), e);
        }

        // Create VM
        final VirtualMachine machine = new VirtualMachine();
        machine.setLocation(location);

        // Set vm os profile
        machine.setOsProfile(new OSProfile());
        machine.getOsProfile().setComputerName(vmName);
        machine.getOsProfile().setAdminUsername(details.getUsername());
        machine.getOsProfile().setAdminPassword(details.getPassword());
        machine.getOsProfile().setCustomData(customData);

        // Set vm hardware profile
        machine.setHardwareProfile(new HardwareProfile());
        machine.getHardwareProfile().setVmSize(details.getVmSize());

        // Set vm storage profile
        machine.setStorageProfile(new StorageProfile());
        machine.getStorageProfile().setDataDisks(null);
        machine.getStorageProfile().setOsDisk(new OSDisk());
        machine.getStorageProfile().getOsDisk().setName(osDiskName);
        machine.getStorageProfile().getOsDisk().setOsType(osType);
        machine.getStorageProfile().getOsDisk().setCaching("None");
        machine.getStorageProfile().getOsDisk().setCreateOption("fromImage");
        machine.getStorageProfile().getOsDisk().setImage(new VirtualHardDisk());
        machine.getStorageProfile().getOsDisk().getImage().setUri(userImageName);
        machine.getStorageProfile().getOsDisk().setVhd(new VirtualHardDisk());
        machine.getStorageProfile().getOsDisk().getVhd().setUri(osDiskVhdName);

        // Set vm network interface reference
        machine.setNetworkProfile(new NetworkProfile());
        machine.getNetworkProfile().setNetworkInterfaces(new ArrayList<NetworkInterfaceReference>());
        NetworkInterfaceReference nir = new NetworkInterfaceReference();
        nir.setPrimary(true);
        nir.setId(nic.getId());
        machine.getNetworkProfile().getNetworkInterfaces().add(nir);

        // Set tags
        machine.setTags(new HashMap<String, String>());
        machine.getTags().put("teamcity", tag.getServerAddress());

        final ServiceResponse<VirtualMachine> response;
        try {
            response = myComputeClient.getVirtualMachinesOperations().createOrUpdate(groupId, vmName, machine);
        } catch (Throwable e) {
            throw new CloudException("Failed to create virtual machine: " + e.getMessage(), e);
        }

        return response.getBody().getName();
    }

    public void updateVmStatus(AzureCloudInstance instance) {
        final String groupId = instance.getImage().getImageDetails().getGroupId();
        final ServiceResponse<VirtualMachine> response;
        try {
            response = myComputeClient.getVirtualMachinesOperations().get(groupId, instance.getName(), null);
        } catch (Throwable e) {
            throw new CloudException("Failed to get virtual machine status: " + e.getMessage(), e);
        }

        final AzureInstance azureInstance = new AzureInstance(response.getBody());
        instance.setStatus(azureInstance.getInstanceStatus());
    }

    public void deleteVm(AzureCloudInstance instance) {
        final String groupId = instance.getImage().getImageDetails().getGroupId();
        try {
            myComputeClient.getVirtualMachinesOperations().delete(groupId, instance.getName());
            myNetworkClient.getNetworkInterfacesOperations().delete(groupId, instance.getName() + "-net");
        } catch (Throwable e) {
            throw new CloudException("Failed to delete virtual machine: " + e.getMessage(), e);
        }
    }

    public String getVhdOsType(String group, String storage, String filePath) {
        final int slash = filePath.lastIndexOf("/");
        if (slash < 0){
            throw new CloudException("File path must include container name");
        }

        final ServiceResponse<StorageAccountKeys> keysResponse;
        try {
            keysResponse = myStorageClient.getStorageAccountsOperations().listKeys(group, storage);
        } catch (Exception e) {
            throw new CloudException("Failed to access storage account: " + e.getMessage(), e);
        }

        final StorageCredentialsAccountAndKey credentials = new StorageCredentialsAccountAndKey(storage, keysResponse.getBody().getKey1());
        final CloudStorageAccount storageAccount;
        try {
            storageAccount = new CloudStorageAccount(credentials);
        } catch (Exception e) {
            throw new CloudException("Failed to connect to storage account: "+ e.getMessage(), e);
        }

        final CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
        final String containerName = filePath.substring(0, slash);
        final CloudBlobContainer container;
        try {
            container = blobClient.getContainerReference(containerName);
        } catch (Exception e) {
            throw new CloudException("Failed to access storage container: " + e.getMessage(), e);
        }

        final String blobName = filePath.substring(slash + 1);
        for (ListBlobItem item : container.listBlobs(blobName)) {
            final CloudBlob blob = (CloudBlob) item;
            try {
                blob.downloadAttributes();
            } catch (Exception e) {
                throw new CloudException("Failed to access storage blob: " + e.getMessage(), e);
            }

            final HashMap<String, String> metadata = blob.getMetadata();
            if (!"OSDisk".equals(metadata.get("MicrosoftAzureCompute_ImageType"))){
                return null;
            }

            if (!"Generalized".equals(metadata.get("MicrosoftAzureCompute_OSState"))){
                throw new CloudException("VHD image should be generalized.");
            }

            return metadata.get("MicrosoftAzureCompute_OSType");
        }

        return null;
    }

    public void restartVm(AzureCloudInstance instance) {
        final String groupId = instance.getImage().getImageDetails().getGroupId();
        try {
            myComputeClient.getVirtualMachinesOperations().restart(groupId, instance.getName());
        } catch (Throwable e) {
            throw new CloudException("Failed to restart virtual machine: " + e.getMessage(), e);
        }
    }
}
