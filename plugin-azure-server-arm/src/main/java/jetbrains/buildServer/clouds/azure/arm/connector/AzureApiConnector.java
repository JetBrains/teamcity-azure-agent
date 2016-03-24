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
import com.microsoft.azure.ListOperationCallback;
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
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.blob.*;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceResponse;
import com.microsoft.rest.credentials.ServiceClientCredentials;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudException;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImage;
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails;
import jetbrains.buildServer.clouds.azure.arm.AzureCloudInstance;
import jetbrains.buildServer.clouds.azure.arm.AzureConstants;
import jetbrains.buildServer.clouds.azure.connector.AzureApiConnectorBase;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import org.jdeferred.*;
import org.jdeferred.impl.DefaultDeferredManager;
import org.jdeferred.impl.DeferredObject;
import org.jdeferred.multiple.MasterProgress;
import org.jdeferred.multiple.MultipleResults;
import org.jdeferred.multiple.OneReject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joda.time.DateTime;

import java.util.*;

/**
 * Provides azure arm management capabilities.
 */
public class AzureApiConnector extends AzureApiConnectorBase<AzureCloudImage, AzureCloudInstance> {

    private static final Logger LOG = Logger.getInstance(AzureApiConnector.class.getName());
    private static final int RESOURCES_NUMBER = 100;
    private static final String BLOBS_CONTAINER = "vhds";
    private static final String NETWORK_IP_SUFFIX = "-net";
    private static final String PUBLIC_IP_SUFFIX = "-pip";
    private static final String PROVISIONING_STATE = "ProvisioningState/";
    private static final String POWER_STATE = "PowerState/";
    private static final String INSTANCE_VIEW = "InstanceView";
    private final ResourceManagementClient myArmClient;
    private final StorageManagementClient myStorageClient;
    private final ComputeManagementClient myComputeClient;
    private final NetworkManagementClient myNetworkClient;
    private final DefaultDeferredManager myManager;
    private String myServerId = null;
    private String myProfileId = null;

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

        myManager = new DefaultDeferredManager();
    }

    @Override
    public void test() throws CloudException {
        getResourceGroups();
    }

    @Nullable
    @Override
    public InstanceStatus getInstanceStatusIfExists(@NotNull final AzureCloudInstance instance) {
        final AzureInstance azureInstance = new AzureInstance(instance.getName());
        final AzureCloudImageDetails details = instance.getImage().getImageDetails();

        try {
            myManager.when(getInstanceDataAsync(azureInstance, details)).fail(new FailCallback<Throwable>() {
                @Override
                public void onFail(Throwable result) {
                    instance.setStatus(InstanceStatus.ERROR);
                    instance.updateErrors(TypedCloudErrorInfo.fromException(result));
                }
            }).waitSafely();
            return azureInstance.getInstanceStatus();
        } catch (InterruptedException e) {
            final CloudException exception = new CloudException("Failed to get virtual machine info " + e.getMessage(), e);
            instance.updateErrors(TypedCloudErrorInfo.fromException(exception));
        }

        return null;
    }

    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public <R extends AbstractInstance> Map<AzureCloudImage, Map<String, R>> fetchInstances(@NotNull Collection<AzureCloudImage> images) throws CheckedCloudException {
        final Map<AzureCloudImage, Map<String, R>> imageMap = new HashMap<>();
        final List<Promise<Void, Throwable, Object>> promises = new ArrayList<>();

        for (final AzureCloudImage image : images) {
            final Promise<Void, Throwable, Object> promise = fetchInstancesAsync(image).fail(new FailCallback<Throwable>() {
                @Override
                public void onFail(Throwable result) {
                    image.updateErrors(TypedCloudErrorInfo.fromException(result));
                }
            }).then(new DonePipe<Map<String, AbstractInstance>, Void, Throwable, Object>() {
                @Override
                public Promise<Void, Throwable, Object> pipeDone(Map<String, AbstractInstance> result) {
                    imageMap.put(image, (Map<String, R>) result);
                    return new DeferredObject<Void, Throwable, Object>().resolve(null);
                }
            });

            promises.add(promise);
        }

        if (promises.size() != 0){
            try {
                myManager.when(promises.toArray(new Promise[]{})).waitSafely();
            } catch (InterruptedException e) {
                throw new CloudException("Failed to get list of images " + e.getMessage(), e);
            }
        }

        return imageMap;
    }

    @SuppressWarnings("unchecked")
    private <R extends AbstractInstance> Promise<Map<String, R>, Throwable, Object> fetchInstancesAsync(final AzureCloudImage image) {
        final DeferredObject<Map<String, R>, Throwable, Object> deferred = new DeferredObject<>();
        final List<Promise<Void, Throwable, Object>> promises = new ArrayList<>();
        final Map<String, R> instances = new HashMap<>();
        final List<Throwable> exceptions = new ArrayList<>();
        final AzureCloudImageDetails details = image.getImageDetails();

        myComputeClient.getVirtualMachinesOperations().listAsync(details.getGroupId(), new ListOperationCallback<VirtualMachine>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to get list of virtual machines: " + t.getMessage(), t);
                exceptions.add(exception);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<List<VirtualMachine>> result) {
                for (VirtualMachine virtualMachine : result.getBody()) {
                    final String name = virtualMachine.getName();
                    if (!name.startsWith(details.getVmNamePrefix())) continue;

                    final Map<String, String> tags = virtualMachine.getTags();
                    if (tags == null) continue;

                    final String serverId = tags.get(AzureConstants.TAG_SERVER);
                    if (!StringUtil.areEqual(serverId, myServerId)) continue;

                    final String profileId = tags.get(AzureConstants.TAG_PROFILE);
                    if (!StringUtil.areEqual(profileId, myProfileId)) continue;

                    final String sourceName = tags.get(AzureConstants.TAG_SOURCE);
                    if (!StringUtil.areEqual(sourceName, details.getSourceName())) continue;

                    final AzureInstance instance = new AzureInstance(name);
                    final Promise<Void, Throwable, Object> promise = getInstanceDataAsync(instance, details);
                    promise.fail(new FailCallback<Throwable>() {
                        @Override
                        public void onFail(Throwable result) {
                            exceptions.add(result);
                        }
                    });

                    promises.add(promise);
                    instances.put(name, (R) instance);
                }

                if (promises.size() == 0){
                    deferred.resolve(instances);
                } else {
                    myManager.when(promises.toArray(new Promise[]{})).always(new AlwaysCallback<MultipleResults, OneReject>() {
                        @Override
                        public void onAlways(Promise.State state, MultipleResults resolved, OneReject rejected) {
                            final TypedCloudErrorInfo[] errors = new TypedCloudErrorInfo[exceptions.size()];
                            for (int i = 0; i < exceptions.size(); i++) {
                                errors[i] = TypedCloudErrorInfo.fromException(exceptions.get(i));
                            }

                            image.updateErrors(errors);
                            deferred.resolve(instances);
                        }
                    });
                }
            }
        });

        return deferred.promise();
    }

    private Promise<Void, Throwable, Object> getInstanceDataAsync(final AzureInstance instance, final AzureCloudImageDetails details) {
        final String groupId = details.getGroupId();
        final String name = instance.getName();
        final DeferredObject<Void, Throwable, Object> instanceViewPromise = new DeferredObject<>();

        myComputeClient.getVirtualMachinesOperations().getAsync(groupId, name, INSTANCE_VIEW, new ServiceCallback<VirtualMachine>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to get virtual machine info " + t.getMessage(), t);
                instanceViewPromise.reject(exception);
            }

            @Override
            public void success(ServiceResponse<VirtualMachine> result) {
                for (InstanceViewStatus status : result.getBody().getInstanceView().getStatuses()) {
                    final String code = status.getCode();
                    if (code.startsWith(PROVISIONING_STATE)) {
                        instance.setProvisioningState(code.substring(PROVISIONING_STATE.length()));
                        final DateTime dateTime = status.getTime();
                        if (dateTime != null) {
                            instance.setStartDate(dateTime.toDate());
                        }
                    }
                    if (code.startsWith(POWER_STATE)) {
                        instance.setPowerState(code.substring(POWER_STATE.length()));
                    }
                }

                instanceViewPromise.resolve(null);
            }
        });

        final Promise<Void, Throwable, Object> publicIpPromise;
        if (details.getVmPublicIp()) {
            publicIpPromise = getPublicIpAsync(groupId, name + PUBLIC_IP_SUFFIX).then(new DonePipe<String, Void, Throwable, Object>() {
                @Override
                public Promise<Void, Throwable, Object> pipeDone(String result) {
                    if (!StringUtil.isEmpty(result)){
                        instance.setIpAddress(result);
                    }

                    return new DeferredObject<Void, Throwable, Object>().resolve(null);
                }
            });
        } else {
            publicIpPromise = new DeferredObject<Void, Throwable, Object>().resolve(null);
        }

        return myManager.when(instanceViewPromise, publicIpPromise).then(new DonePipe<MultipleResults, Void, Throwable, Object>() {
            @Override
            public Promise<Void, Throwable, Object> pipeDone(MultipleResults result) {
                return new DeferredObject<Void, Throwable, Object>().resolve(null);
            }
        }, new FailPipe<OneReject, Void, Throwable, Object>() {
            @Override
            public Promise<Void, Throwable, Object> pipeFail(OneReject result) {
                return new DeferredObject<Void, Throwable, Object>().reject((Throwable) result.getReject());
            }
        });
    }

    private Promise<String, Throwable, Object> getPublicIpAsync(final String groupId, final String name){
        final DeferredObject<String, Throwable, Object> deferred = new DeferredObject<>();
        myNetworkClient.getPublicIPAddressesOperations().getAsync(groupId, name, null, new ServiceCallback<PublicIPAddress>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to get public ip address info " + t.getMessage(), t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<PublicIPAddress> result) {
                deferred.resolve(result.getBody().getIpAddress());
            }
        });

        return deferred.promise();
    }

    @NotNull
    @Override
    public TypedCloudErrorInfo[] checkImage(@NotNull AzureCloudImage image) {
        final CloudErrorInfo error = image.getErrorInfo();
        if (error == null) {
            return new TypedCloudErrorInfo[]{};
        }

        return new TypedCloudErrorInfo[]{new TypedCloudErrorInfo("error", error.getMessage(), error.getDetailedMessage())};
    }

    @NotNull
    @Override
    public TypedCloudErrorInfo[] checkInstance(@NotNull AzureCloudInstance instance) {
        final CloudErrorInfo error = instance.getErrorInfo();
        if (error == null) {
            return new TypedCloudErrorInfo[]{};
        }

        return new TypedCloudErrorInfo[]{new TypedCloudErrorInfo("error", error.getMessage(), error.getDetailedMessage())};
    }

    /**
     * Gets a list of available resource groups.
     *
     * @return list of resource groups.
     */
    @NotNull
    public List<String> getResourceGroups() {
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

    /**
     * Gets a list of storage accounts in the resource group.
     *
     * @param groupName is a resource group name.
     * @return list of storages.
     */
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

    /**
     * Gets a list of VM sizes in the resource group.
     *
     * @param groupName is a resource group name.
     * @return list of sizes.
     */
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

    /**
     * Creates a new cloud instance.
     *
     * @param instance is a cloud instance.
     * @param userData is a custom data.
     * @return promise.
     */
    @NotNull
    public Promise<Void, Throwable, Object> createVmAsync(final AzureCloudInstance instance, final CloudInstanceUserData userData) {
        final AzureCloudImageDetails details = instance.getImage().getImageDetails();
        final String groupId = details.getGroupId();
        final String storageId = details.getStorageId();
        final String imagePath = details.getImagePath();
        final String osType = details.getOsType();
        final boolean publicIp = details.getVmPublicIp();
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
        final String customData = new String(Base64.getEncoder().encode(userData.serialize().getBytes()));

        // Check storage account
        final Promise<VirtualMachine, Throwable, Object> machinePromise = getStoragesByGroupAsync(groupId).then(new DonePipe<List<StorageAccount>, VirtualMachine, Throwable, Object>() {
            @Override
            public Promise<VirtualMachine, Throwable, Object> pipeDone(List<StorageAccount> storages) {
                final StorageAccount storage = CollectionsUtil.findFirst(storages, new Filter<StorageAccount>() {
                    @Override
                    public boolean accept(@NotNull StorageAccount storage) {
                        return storage.getName().equalsIgnoreCase(storageId);
                    }
                });
                if (storage == null) {
                    final CloudException exception = new CloudException(String.format("Storage %s does not exist in resource group %s", storageId, groupId));
                    return new DeferredObject<VirtualMachine, Throwable, Object>().reject(exception);
                }

                final String location = storage.getLocation();

                // Create virtual network
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

                final Promise<Subnet, Throwable, Object> subnetPromise = createVirtualNetworkAsync(groupId, vNetName, subnetName, vNet);
                final Promise<PublicIPAddress, Throwable, Object> publicIPAddressPromise;

                // Create public IP
                if (publicIp) {
                    final PublicIPAddress publicIPAddress = new PublicIPAddress();
                    publicIPAddress.setLocation(location);
                    publicIPAddress.setPublicIPAllocationMethod("Dynamic");
                    publicIPAddress.setDnsSettings(new PublicIPAddressDnsSettings());
                    publicIPAddress.getDnsSettings().setDomainNameLabel(publicIpName);

                    publicIPAddressPromise = createPublicIpAsync(groupId, publicIpName, publicIPAddress);
                } else {
                    publicIPAddressPromise = new DeferredObject<PublicIPAddress, Throwable, Object>().resolve(null);
                }

                return myManager.when(subnetPromise, publicIPAddressPromise).then(new DonePipe<MultipleResults, NetworkInterface, Throwable, Object>() {
                    @Override
                    public Promise<NetworkInterface, Throwable, Object> pipeDone(MultipleResults result) {
                        final Subnet subnet = (Subnet) result.get(0).getResult();
                        final PublicIPAddress publicIPAddress = (PublicIPAddress) result.get(1).getResult();

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

                        if (publicIp) {
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
                        }

                        return createNetworkAsync(groupId, vNetName, securityGroupName, networkSecurityGroup, nicName, nic);
                    }
                }).then(new DonePipe<NetworkInterface, VirtualMachine, Throwable, Object>() {
                    @Override
                    public Promise<VirtualMachine, Throwable, Object> pipeDone(NetworkInterface nic) {
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
                        machine.getTags().put(AzureConstants.TAG_SERVER, myServerId);
                        machine.getTags().put(AzureConstants.TAG_PROFILE, userData.getProfileId());
                        machine.getTags().put(AzureConstants.TAG_SOURCE, details.getSourceName());

                        return createVirtualMachineAsync(groupId, vmName, machine);
                    }
                });
            }
        });

        return myManager.when(machinePromise).then(new DonePipe<VirtualMachine, Void, Throwable, Object>() {
            @Override
            public Promise<Void, Throwable, Object> pipeDone(VirtualMachine result) {
                if (publicIp){
                    return getPublicIpAsync(groupId, publicIpName).then(new DonePipe<String, Void, Throwable, Object>() {
                        @Override
                        public Promise<Void, Throwable, Object> pipeDone(String result) {
                            instance.setNetworkIdentify(result);
                            return new DeferredObject<Void, Throwable, Object>().resolve(null);
                        }
                    });
                }

                return new DeferredObject<Void, Throwable, Object>().resolve(null);
            }
        });
    }

    private Promise<List<StorageAccount>, Throwable, Object> getStoragesByGroupAsync(String groupId) {
        final Deferred<List<StorageAccount>, Throwable, Object> deferred = new DeferredObject<>();
        myStorageClient.getStorageAccountsOperations().listByResourceGroupAsync(groupId, new ServiceCallback<List<StorageAccount>>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to get list of storages " + t.getMessage(), t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<List<StorageAccount>> result) {
                deferred.resolve(result.getBody());
            }
        });

        return deferred.promise();
    }

    private Promise<Subnet, Throwable, Object> createVirtualNetworkAsync(final String groupId,
                                                                         final String vNetName,
                                                                         final String subnetName,
                                                                         final VirtualNetwork vNet) {
        final Deferred<Subnet, Throwable, Object> deferred = new DeferredObject<>();
        myNetworkClient.getVirtualNetworksOperations().createOrUpdateAsync(groupId, vNetName, vNet, new ServiceCallback<VirtualNetwork>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to create virtual network " + t.getMessage(), t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<VirtualNetwork> result) {
                myNetworkClient.getSubnetsOperations().getAsync(groupId, vNetName, subnetName, null, new ServiceCallback<Subnet>() {
                    @Override
                    public void failure(Throwable t) {
                        final CloudException exception = new CloudException("Failed to get subnet " + t.getMessage(), t);
                        deferred.reject(exception);
                    }

                    @Override
                    public void success(ServiceResponse<Subnet> result) {
                        deferred.resolve(result.getBody());
                    }
                });
            }
        });

        return deferred.promise();
    }

    private Promise<NetworkInterface, Throwable, Object> createNetworkAsync(final String groupId,
                                                                            final String vNetName,
                                                                            final String securityGroupName,
                                                                            final NetworkSecurityGroup securityGroup,
                                                                            final String nicName,
                                                                            final NetworkInterface nic) {
        final Deferred<NetworkInterface, Throwable, Object> deferred = new DeferredObject<>();
        myNetworkClient.getSubnetsOperations().listAsync(groupId, vNetName, new ListOperationCallback<Subnet>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to get list of sub networks: " + t.getMessage(), t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<List<Subnet>> result) {
                securityGroup.setSubnets(result.getBody());
                myNetworkClient.getNetworkSecurityGroupsOperations().createOrUpdateAsync(groupId, securityGroupName, securityGroup, new ServiceCallback<NetworkSecurityGroup>() {
                    @Override
                    public void failure(Throwable t) {
                        final CloudException exception = new CloudException("Failed to create network security group: " + t.getMessage(), t);
                        deferred.reject(exception);
                    }

                    @Override
                    public void success(ServiceResponse<NetworkSecurityGroup> result) {
                        nic.setNetworkSecurityGroup(result.getBody());
                        myNetworkClient.getNetworkInterfacesOperations().createOrUpdateAsync(groupId, nicName, nic, new ServiceCallback<NetworkInterface>() {
                            @Override
                            public void failure(Throwable t) {
                                final CloudException exception = new CloudException("Failed to create network interface: " + t.getMessage(), t);
                                deferred.reject(exception);
                            }

                            @Override
                            public void success(ServiceResponse<NetworkInterface> result) {
                                deferred.resolve(result.getBody());
                            }
                        });

                    }
                });
            }
        });

        return deferred.promise();
    }

    private Promise<VirtualMachine, Throwable, Object> createVirtualMachineAsync(final String groupId,
                                                                                 final String vmName,
                                                                                 final VirtualMachine machine) {
        final Deferred<VirtualMachine, Throwable, Object> deferred = new DeferredObject<>();
        myComputeClient.getVirtualMachinesOperations().createOrUpdateAsync(groupId, vmName, machine, new ServiceCallback<VirtualMachine>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to create virtual machine: " + t.getMessage(), t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<VirtualMachine> result) {
                deferred.resolve(result.getBody());
            }
        });

        return deferred.promise();
    }

    private Promise<PublicIPAddress, Throwable, Object> createPublicIpAsync(final String groupId,
                                                                            final String publicIpName,
                                                                            final PublicIPAddress publicIPAddress) {
        final Deferred<PublicIPAddress, Throwable, Object> deferred = new DeferredObject<>();
        myNetworkClient.getPublicIPAddressesOperations().createOrUpdateAsync(groupId, publicIpName, publicIPAddress, new ServiceCallback<PublicIPAddress>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to create public ip address " + t.getMessage(), t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<PublicIPAddress> result) {
                deferred.resolve(result.getBody());
            }
        });

        return deferred.promise();
    }

    /**
     * Deletes a cloud instance.
     *
     * @param instance is a cloud instance.
     * @return promise.
     */
    public Promise<Void, Throwable, Object> deleteVmAsync(@NotNull final AzureCloudInstance instance) {
        final AzureCloudImageDetails details = instance.getImage().getImageDetails();
        final String groupId = details.getGroupId();
        final String storageId = details.getStorageId();
        final String filePrefix = String.format("%s/%s", BLOBS_CONTAINER, instance.getName());

        return deleteVirtualMachineAsync(groupId, instance.getName()).then(new DonePipe<Void, MultipleResults, OneReject, MasterProgress>() {
            @Override
            public Promise<MultipleResults, OneReject, MasterProgress> pipeDone(Void result) {
                final Promise<Void, Throwable, Void> deleteBlobsPromise = myManager.when(new Runnable() {
                    @Override
                    public void run() {
                        for (CloudBlob blob : getBlobs(groupId, storageId, filePrefix)) {
                            try {
                                blob.deleteIfExists();
                            } catch (Exception e) {
                                LOG.warnAndDebugDetails("Failed to delete blob", e);
                            }
                        }
                    }
                });

                final Promise<Void, Throwable, Object> networkPromise = deleteNetworkAsync(groupId, instance.getName() + NETWORK_IP_SUFFIX).then(new DonePipe<Void, Void, Throwable, Object>() {
                    @Override
                    public Promise<Void, Throwable, Object> pipeDone(Void result) {
                        if (StringUtil.isEmpty(instance.getNetworkIdentity())) {
                            return new DeferredObject<Void, Throwable, Object>().resolve(null);
                        } else {
                            return deletePublicIpAsync(groupId, instance.getName() + PUBLIC_IP_SUFFIX);
                        }
                    }
                });

                return myManager.when(networkPromise, deleteBlobsPromise);
            }
        }).then(new DonePipe<MultipleResults, Void, Throwable, Object>() {
            @Override
            public Promise<Void, Throwable, Object> pipeDone(MultipleResults result) {
                return new DeferredObject<Void, Throwable, Object>().resolve(null);
            }
        });
    }

    private Promise<Void, Throwable, Object> deletePublicIpAsync(String groupId, String name) {
        final DeferredObject<Void, Throwable, Object> deferred = new DeferredObject<>();
        myNetworkClient.getPublicIPAddressesOperations().deleteAsync(groupId, name, new ServiceCallback<Void>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to delete public ip address " + t.getMessage(), t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<Void> result) {
                deferred.resolve(result.getBody());
            }
        });

        return deferred.promise();
    }

    private Promise<Void, Throwable, Object> deleteVirtualMachineAsync(String groupId, String name) {
        final DeferredObject<Void, Throwable, Object> deferred = new DeferredObject<>();
        myComputeClient.getVirtualMachinesOperations().deleteAsync(groupId, name, new ServiceCallback<Void>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to delete virtual machine " + t.getMessage(), t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<Void> result) {
                deferred.resolve(result.getBody());
            }
        });

        return deferred.promise();
    }

    private Promise<Void, Throwable, Object> deleteNetworkAsync(String groupId, String name) {
        final DeferredObject<Void, Throwable, Object> deferred = new DeferredObject<>();
        myNetworkClient.getNetworkInterfacesOperations().deleteAsync(groupId, name, new ServiceCallback<Void>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to delete network interface", t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<Void> result) {
                deferred.resolve(result.getBody());
            }
        });

        return deferred.promise();
    }

    /**
     * Restarts an instance.
     *
     * @param instance is a cloud instance.
     * @return promise.
     */
    @NotNull
    public Promise<Void, Throwable, Object> restartVmAsync(@NotNull final AzureCloudInstance instance) {
        final DeferredObject<Void, Throwable, Object> deferred = new DeferredObject<>();
        final String groupId = instance.getImage().getImageDetails().getGroupId();
        myComputeClient.getVirtualMachinesOperations().restartAsync(groupId, instance.getName(), new ServiceCallback<Void>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to restart virtual machine " + t.getMessage(), t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<Void> result) {
                deferred.resolve(result.getBody());
            }
        });

        return deferred.promise();
    }

    /**
     * Gets an OS type of VHD image.
     *
     * @param group    is a resource group name.
     * @param storage  is a storage name.
     * @param filePath is a file path.
     * @return OS type (Linux, Windows).
     */
    @Nullable
    public String getVhdOsType(@NotNull final String group,
                               @NotNull final String storage,
                               @NotNull final String filePath) {
        final List<CloudBlob> blobs = getBlobs(group, storage, filePath);
        if (blobs.size() == 0) {
            throw new CloudException("VHD file not found in storage account");
        }
        if (blobs.size() > 1) {
            return null;
        }

        final CloudBlob blob = blobs.get(0);
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

    private List<CloudBlob> getBlobs(final String group, final String storage, final String filesPrefix) {
        final int slash = filesPrefix.indexOf("/");
        if (slash <= 0) {
            throw new CloudException("File path must include container name");
        }

        final String storageKey;
        try {
            storageKey = myStorageClient.getStorageAccountsOperations().listKeys(group, storage).getBody().getKey1();
        } catch (Exception e) {
            throw new CloudException("Failed to access storage account: " + e.getMessage(), e);
        }

        final StorageCredentialsAccountAndKey credentials = new StorageCredentialsAccountAndKey(storage, storageKey);
        final String containerName = filesPrefix.substring(0, slash);
        final CloudBlobContainer container;
        try {
            container = new CloudStorageAccount(credentials).createCloudBlobClient().getContainerReference(containerName);
        } catch (Exception e) {
            throw new CloudException("Failed to connect to storage account: " + e.getMessage(), e);
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

    /**
     * Sets a server identifier.
     *
     * @param serverId identifier.
     */
    public void setServerId(@Nullable final String serverId) {
        myServerId = serverId;
    }

    /**
     * Sets a profile identifier.
     *
     * @param profileId identifier.
     */
    public void setProfileId(@Nullable final String profileId) {
        myProfileId = profileId;
    }
}
