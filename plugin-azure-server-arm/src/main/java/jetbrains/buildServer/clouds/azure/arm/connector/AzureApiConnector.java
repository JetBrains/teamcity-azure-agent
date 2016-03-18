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

import com.intellij.openapi.diagnostic.Logger;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.compute.ComputeManagementClient;
import com.microsoft.azure.management.compute.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.VirtualMachinesOperations;
import com.microsoft.azure.management.compute.models.*;
import com.microsoft.azure.management.network.NetworkManagementClient;
import com.microsoft.azure.management.network.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.NetworkSecurityGroupsOperations;
import com.microsoft.azure.management.network.PublicIPAddressesOperations;
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
import jetbrains.buildServer.clouds.azure.arm.AzureConstants;
import jetbrains.buildServer.clouds.azure.connector.ActionIdChecker;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * Provides azure arm management capabilities.
 */
public class AzureApiConnector implements CloudApiConnector<AzureCloudImage, AzureCloudInstance>, ActionIdChecker {

    private static final Logger LOG = Logger.getInstance(AzureApiConnector.class.getName());
    private static final int RESOURCES_NUMBER = 100;
    private static final String BLOBS_CONTAINER = "vhds";
    private static final String NETWORK_IP_SUFFIX = "-net";
    private static final String PUBLIC_IP_SUFFIX = "-pip";
    private final ResourceManagementClient myArmClient;
    private final StorageManagementClient myStorageClient;
    private final ComputeManagementClient myComputeClient;
    private final NetworkManagementClient myNetworkClient;
    private String myServerUid = null;

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
        final AzureCloudImageDetails details = image.getImageDetails();
        final Map<String, AzureInstance> instances = new HashMap<>();
        final ServiceResponse<List<VirtualMachine>> vmsResponse;
        final VirtualMachinesOperations operations = myComputeClient.getVirtualMachinesOperations();

        try {
            vmsResponse = operations.list(details.getGroupId());
        } catch (Throwable e) {
            throw new CloudException("Failed to get list of virtual machines: " + e.getMessage(), e);
        }

        for (VirtualMachine virtualMachine : vmsResponse.getBody()) {
            final String name = virtualMachine.getName();
            if (!name.startsWith(details.getVmNamePrefix())) continue;

            final Map<String, String> tags = virtualMachine.getTags();
            if (tags == null) continue;

            final String serverUid = tags.get(AzureConstants.TAG_SERVER);
            if (!StringUtil.areEqual(serverUid, myServerUid)) continue;

            final String sourceName = tags.get(AzureConstants.TAG_SOURCE);
            if (!StringUtil.areEqual(sourceName, details.getSourceName())) continue;

            try {
                final ServiceResponse<VirtualMachine> response = operations.get(details.getGroupId(), name, "InstanceView");
                virtualMachine = response.getBody();
            } catch (Exception e) {
                LOG.warnAndDebugDetails("Failed to receive virtual machine info", e);
                continue;
            }

            final AzureInstance instance = new AzureInstance(virtualMachine, image, this);
            instances.put(name, instance);
        }

        return instances;
    }

    @Override
    public Collection<TypedCloudErrorInfo> checkImage(@NotNull AzureCloudImage image) {
        return Collections.emptyList();
    }

    @Override
    public Collection<TypedCloudErrorInfo> checkInstance(@NotNull AzureCloudInstance instance) {
        if (instance.getStatus() != InstanceStatus.ERROR) {
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
        final List<String> groups = new ArrayList<>(resourceGroups.size());
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
        final List<String> storages = new ArrayList<>(accounts.size());
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
        final List<String> sizes = new ArrayList<>(vmSizes.size());
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
        final String nicName = vmName + NETWORK_IP_SUFFIX;
        final String publicIpName = vmName + PUBLIC_IP_SUFFIX;
        final String osDiskName = vmName + "-os";
        final String osDiskVhdName = String.format("https://%s.blob.core.windows.net/" + BLOBS_CONTAINER + "/%s-os.vhd", storageId, vmName);
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

        // Create public IP
        PublicIPAddress publicIPAddress = new PublicIPAddress();
        publicIPAddress.setLocation(location);
        publicIPAddress.setPublicIPAllocationMethod("Dynamic");
        publicIPAddress.setDnsSettings(new PublicIPAddressDnsSettings());
        publicIPAddress.getDnsSettings().setDomainNameLabel(publicIpName);

        try {
            myNetworkClient.getPublicIPAddressesOperations().createOrUpdate(groupId, publicIpName, publicIPAddress);
            publicIPAddress = myNetworkClient.getPublicIPAddressesOperations().get(groupId, publicIpName, null).getBody();
        } catch (Exception e) {
            throw new CloudException("Failed to create public IP address " + e.getMessage(), e);
        }

        // Create network interface
        NetworkInterface nic = new NetworkInterface();
        nic.setLocation(location);
        nic.setIpConfigurations(new ArrayList<NetworkInterfaceIPConfiguration>());
        NetworkInterfaceIPConfiguration configuration = new NetworkInterfaceIPConfiguration();
        configuration.setName(nicName + "-config");
        configuration.setPrivateIPAllocationMethod("Dynamic");
        configuration.setSubnet(subnet);
        configuration.setPublicIPAddress(publicIPAddress);
        nic.getIpConfigurations().add(configuration);

        NetworkSecurityGroup networkSecurityGroup = new NetworkSecurityGroup();
        networkSecurityGroup.setLocation(location);
        networkSecurityGroup.setSecurityRules(new ArrayList<SecurityRule>());

        if ("Windows".equalsIgnoreCase(details.getOsType())) {
            final SecurityRule rdpRule = new SecurityRule();
            rdpRule.setName("default-allow-rdp");
            rdpRule.setDirection("Inbound");
            rdpRule.setPriority(999);
            rdpRule.setProtocol("TCP");
            rdpRule.setSourceAddressPrefix("*");
            rdpRule.setSourcePortRange("*");
            rdpRule.setDestinationAddressPrefix("*");
            rdpRule.setDestinationPortRange("3389");
            rdpRule.setAccess("Allow");
            networkSecurityGroup.getSecurityRules().add(rdpRule);
        } else {
            final SecurityRule sshRule = new SecurityRule();
            sshRule.setName("default-allow-ssh");
            sshRule.setDirection("Inbound");
            sshRule.setPriority(1000);
            sshRule.setProtocol("TCP");
            sshRule.setSourceAddressPrefix("*");
            sshRule.setSourcePortRange("*");
            sshRule.setDestinationAddressPrefix("*");
            sshRule.setDestinationPortRange("22");
            sshRule.setAccess("Allow");
            networkSecurityGroup.getSecurityRules().add(sshRule);
        }

        final ServiceResponse<List<Subnet>> subnetsResponse;
        try {
            subnetsResponse = myNetworkClient.getSubnetsOperations().list(groupId, vNetName);
        } catch (Throwable e) {
            throw new CloudException("Failed to get list of sub networks: " + e.getMessage(), e);
        }

        networkSecurityGroup.setSubnets(subnetsResponse.getBody());

        try {
            final NetworkSecurityGroupsOperations operations = myNetworkClient.getNetworkSecurityGroupsOperations();
            networkSecurityGroup = operations.createOrUpdate(groupId, securityGroupName, networkSecurityGroup).getBody();
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
        machine.getTags().put(AzureConstants.TAG_SERVER, myServerUid);
        machine.getTags().put(AzureConstants.TAG_SOURCE, details.getSourceName());

        final ServiceResponse<VirtualMachine> response;
        try {
            response = myComputeClient.getVirtualMachinesOperations().createOrUpdate(groupId, vmName, machine);
        } catch (Throwable e) {
            throw new CloudException("Failed to create virtual machine: " + e.getMessage(), e);
        }

        return response.getBody().getName();
    }

    public void updateVmStatus(AzureCloudInstance instance) {
        final AzureCloudImage image = instance.getImage();
        final String groupId = image.getImageDetails().getGroupId();
        final ServiceResponse<VirtualMachine> response;

        try {
            final VirtualMachinesOperations operations = myComputeClient.getVirtualMachinesOperations();
            response = operations.get(groupId, instance.getName(), "InstanceView");
        } catch (Throwable e) {
            throw new CloudException("Failed to get virtual machine status: " + e.getMessage(), e);
        }

        final AzureInstance azureInstance = new AzureInstance(response.getBody(), image, this);
        instance.setStatus(azureInstance.getInstanceStatus());
    }

    public void deleteVm(@NotNull final AzureCloudInstance instance) {
        final AzureCloudImageDetails details = instance.getImage().getImageDetails();
        final String groupId = details.getGroupId();
        final String storageId = details.getStorageId();
        final String filePrefix = String.format("%s/%s", BLOBS_CONTAINER, instance.getName());

        try {
            myComputeClient.getVirtualMachinesOperations().delete(groupId, instance.getName());
        } catch (Throwable e) {
            LOG.warnAndDebugDetails("Failed to delete virtual machine", e);
        }

        try {
            myNetworkClient.getNetworkInterfacesOperations().delete(groupId, instance.getName() + NETWORK_IP_SUFFIX);
        } catch (Throwable e) {
            LOG.warnAndDebugDetails("Failed to delete network interface", e);
        }

        try {
            myNetworkClient.getPublicIPAddressesOperations().delete(groupId, instance.getName() + PUBLIC_IP_SUFFIX);
        } catch (Throwable e) {
            LOG.warnAndDebugDetails("Failed to delete public ip address", e);
        }

        for (CloudBlob blob : getBlobs(groupId, storageId, filePrefix)) {
            try {
                blob.deleteIfExists();
            } catch (Exception e) {
                LOG.warnAndDebugDetails("Failed to delete blob", e);
            }
        }
    }

    public void restartVm(@NotNull final AzureCloudInstance instance) {
        final String groupId = instance.getImage().getImageDetails().getGroupId();
        try {
            myComputeClient.getVirtualMachinesOperations().restart(groupId, instance.getName());
        } catch (Throwable e) {
            throw new CloudException("Failed to restart virtual machine: " + e.getMessage(), e);
        }
    }

    @Nullable
    public String getVhdOsType(@NotNull final String group,
                               @NotNull final String storage,
                               @NotNull final String filePath) {
        final List<CloudBlob> blobs = getBlobs(group, storage, filePath);
        if (blobs.size() == 0) {
            throw new CloudException("VHD file not found in storage account");
        }

        for (CloudBlob blob : blobs) {
            try {
                blob.downloadAttributes();
            } catch (Exception e) {
                throw new CloudException("Failed to access storage blob: " + e.getMessage(), e);
            }

            final HashMap<String, String> metadata = blob.getMetadata();
            if (!"OSDisk".equals(metadata.get("MicrosoftAzureCompute_ImageType"))) {
                return null;
            }

            if (!"Generalized".equals(metadata.get("MicrosoftAzureCompute_OSState"))) {
                throw new CloudException("VHD image should be generalized.");
            }

            return metadata.get("MicrosoftAzureCompute_OSType");
        }

        return null;
    }

    private List<CloudBlob> getBlobs(final String group, final String storage, final String filesPrefix) {
        final int slash = filesPrefix.indexOf("/");
        if (slash <= 0) {
            throw new CloudException("File path must include container name");
        }

        final ServiceResponse<StorageAccountKeys> keysResponse;
        try {
            keysResponse = myStorageClient.getStorageAccountsOperations().listKeys(group, storage);
        } catch (Exception e) {
            throw new CloudException("Failed to access storage account: " + e.getMessage(), e);
        }

        final String storageKey = keysResponse.getBody().getKey1();
        final StorageCredentialsAccountAndKey credentials = new StorageCredentialsAccountAndKey(storage, storageKey);
        final CloudStorageAccount storageAccount;
        try {
            storageAccount = new CloudStorageAccount(credentials);
        } catch (Exception e) {
            throw new CloudException("Failed to connect to storage account: " + e.getMessage(), e);
        }

        final CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
        final String containerName = filesPrefix.substring(0, slash);
        final CloudBlobContainer container;
        try {
            container = blobClient.getContainerReference(containerName);
        } catch (Exception e) {
            throw new CloudException("Failed to access storage container: " + e.getMessage(), e);
        }

        final String blobName = filesPrefix.substring(slash + 1);
        final List<CloudBlob> blobs = new ArrayList<>();
        try {
            for (ListBlobItem item : container.listBlobs(blobName)) {
                blobs.add((CloudBlob) item);
            }
        } catch (Exception e) {
            throw new CloudException("Failed to list container blobs: " + e.getMessage(), e);
        }

        return blobs;
    }

    @Nullable
    String getIpAddress(String groupId, String machineName) {
        final String nicName = machineName + PUBLIC_IP_SUFFIX;

        try {
            final PublicIPAddressesOperations operations = myNetworkClient.getPublicIPAddressesOperations();
            final ServiceResponse<PublicIPAddress> response = operations.get(groupId, nicName, null);
            return response.getBody().getIpAddress();
        } catch (Exception e) {
            LOG.warnAndDebugDetails("Failed to get public ip address", e);
        }

        return null;
    }

    public void setServerUid(@Nullable final String serverUid) {
        myServerUid = serverUid;
    }
}
