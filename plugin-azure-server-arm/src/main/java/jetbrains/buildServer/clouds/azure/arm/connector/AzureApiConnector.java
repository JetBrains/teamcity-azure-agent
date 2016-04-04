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
import com.microsoft.azure.management.resources.SubscriptionClient;
import com.microsoft.azure.management.resources.SubscriptionClientImpl;
import com.microsoft.azure.management.resources.models.*;
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
import jetbrains.buildServer.clouds.azure.arm.connector.models.JsonValue;
import jetbrains.buildServer.clouds.azure.arm.connector.models.RawJsonValue;
import jetbrains.buildServer.clouds.azure.connector.AzureApiConnectorBase;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.util.StringUtil;
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
import java.util.concurrent.Callable;

/**
 * Provides azure arm management capabilities.
 */
public class AzureApiConnector extends AzureApiConnectorBase<AzureCloudImage, AzureCloudInstance> {

    private static final Logger LOG = Logger.getInstance(AzureApiConnector.class.getName());
    private static final int TIMEOUT_RETRY_COUNT = 3;
    private static final int RESOURCES_NUMBER = 100;
    private static final String BLOBS_CONTAINER = "vhds";
    private static final String NETWORK_IP_SUFFIX = "-net";
    private static final String PUBLIC_IP_SUFFIX = "-pip";
    private static final String PROVISIONING_STATE = "ProvisioningState/";
    private static final String POWER_STATE = "PowerState/";
    private static final String INSTANCE_VIEW = "InstanceView";
    private static final List<InstanceStatus> PROVISIONING_STATES = Arrays.asList(
            InstanceStatus.SCHEDULED_TO_START,
            InstanceStatus.SCHEDULED_TO_STOP);
    private final ResourceManagementClient myArmClient;
    private final StorageManagementClient myStorageClient;
    private final ComputeManagementClient myComputeClient;
    private final NetworkManagementClient myNetworkClient;
    private final SubscriptionClient mySubscriptionClient;
    private final DefaultDeferredManager myManager;
    private String myServerId = null;
    private String myProfileId = null;

    public AzureApiConnector(@NotNull final String tenantId,
                             @NotNull final String clientId,
                             @NotNull final String secret,
                             @Nullable final String subscriptionId) {
        final ServiceClientCredentials credentials = new ApplicationTokenCredentials(clientId, tenantId, secret, null);

        myArmClient = new ResourceManagementClientImpl(credentials);
        myArmClient.setSubscriptionId(subscriptionId);

        myStorageClient = new StorageManagementClientImpl(credentials);
        myStorageClient.setSubscriptionId(subscriptionId);

        myComputeClient = new ComputeManagementClientImpl(credentials);
        myComputeClient.setSubscriptionId(subscriptionId);

        myNetworkClient = new NetworkManagementClientImpl(credentials);
        myNetworkClient.setSubscriptionId(subscriptionId);

        mySubscriptionClient = new SubscriptionClientImpl(credentials);

        myManager = new DefaultDeferredManager();
    }

    @Override
    public void test() throws CloudException {
        try {
            myArmClient.getResourceGroupsOperations().list(null, RESOURCES_NUMBER);
        } catch (Exception e) {
            throw new CloudException("Failed to get list of groups: " + e.getMessage(), e);
        }
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
                    if (PROVISIONING_STATES.contains(instance.getStatus())) return;
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

        if (promises.size() != 0) {
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
        final String groupId = details.getGroupId();

        AzureUtils.retryAsync(new Callable<Promise<List<VirtualMachine>, Throwable, Object>>() {
            @Override
            public Promise<List<VirtualMachine>, Throwable, Object> call() throws Exception {
                return getVirtualMachinesAsync(groupId);
            }
        }, TIMEOUT_RETRY_COUNT).fail(new FailCallback<Throwable>() {
            @Override
            public void onFail(Throwable t) {
                final String message = String.format("Failed to get list of virtual machines in group %s: %s", groupId, t.getMessage());
                final CloudException exception = new CloudException(message, t);
                exceptions.add(exception);
                deferred.reject(exception);
            }
        }).then(new DoneCallback<List<VirtualMachine>>() {
            @Override
            public void onDone(List<VirtualMachine> machines) {
                for (VirtualMachine virtualMachine : machines) {
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

                if (promises.size() == 0) {
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

    private Promise<List<VirtualMachine>, Throwable, Object> getVirtualMachinesAsync(final String groupId) {
        final Deferred<List<VirtualMachine>, Throwable, Object> deferred = new DeferredObject<>();
        myComputeClient.getVirtualMachinesOperations().listAsync(groupId, new ListOperationCallback<VirtualMachine>() {
            @Override
            public void failure(Throwable t) {
                deferred.reject(t);
            }

            @Override
            public void success(ServiceResponse<List<VirtualMachine>> result) {
                deferred.resolve(result.getBody());
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
                    if (!StringUtil.isEmpty(result)) {
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

    private Promise<String, Throwable, Object> getPublicIpAsync(final String groupId, final String name) {
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
    public Promise<List<String>, Throwable, Void> getResourceGroupsAsync() {
        final DeferredObject<List<String>, Throwable, Void> deferred = new DeferredObject<>();
        myArmClient.getResourceGroupsOperations().listAsync(null, RESOURCES_NUMBER, new ListOperationCallback<ResourceGroup>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to get list of groups: " + t.getMessage(), t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<List<ResourceGroup>> result) {
                final List<ResourceGroup> resourceGroups = result.getBody();
                final List<String> groups = new ArrayList<>(resourceGroups.size());
                for (ResourceGroup resourceGroup : resourceGroups) {
                    groups.add(resourceGroup.getName());
                }

                deferred.resolve(groups);
            }
        });

        return deferred.promise();
    }

    /**
     * Gets a list of storage accounts in the resource group.
     *
     * @param groupName is a resource group name.
     * @return list of storages.
     */
    @NotNull
    public Promise<List<String>, Throwable, Object> getStoragesByGroupAsync(@NotNull final String groupName) {
        final DeferredObject<List<String>, Throwable, Object> deferred = new DeferredObject<>();

        myStorageClient.getStorageAccountsOperations().listByResourceGroupAsync(groupName, new ServiceCallback<List<StorageAccount>>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to get list of storages: " + t.getMessage(), t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<List<StorageAccount>> result) {
                final List<StorageAccount> accounts = result.getBody();
                final List<String> storages = new ArrayList<>(accounts.size());
                for (StorageAccount storageAccount : accounts) {
                    storages.add(storageAccount.getName());
                }

                deferred.resolve(storages);
            }
        });

        return deferred.promise();
    }

    /**
     * Gets a list of VM sizes in the resource group.
     *
     * @param groupName is a resource group name.
     * @return list of sizes.
     */
    @NotNull
    public Promise<List<String>, Throwable, Object> getVmSizesByGroupAsync(String groupName) {
        final DeferredObject<List<String>, Throwable, Object> deferred = new DeferredObject<>();
        myArmClient.getResourceGroupsOperations().getAsync(groupName, new ServiceCallback<ResourceGroup>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to get resource group location: " + t.getMessage(), t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<ResourceGroup> result) {
                myComputeClient.getVirtualMachineSizesOperations().listAsync(result.getBody().getLocation(), new ListOperationCallback<VirtualMachineSize>() {
                    @Override
                    public void failure(Throwable t) {
                        final CloudException exception = new CloudException("Failed to get list of vm sizes: " + t.getMessage(), t);
                        deferred.reject(exception);
                    }

                    @Override
                    public void success(ServiceResponse<List<VirtualMachineSize>> result) {
                        final List<VirtualMachineSize> vmSizes = result.getBody();
                        final List<String> sizes = new ArrayList<>(vmSizes.size());
                        for (VirtualMachineSize vmSize : vmSizes) {
                            sizes.add(vmSize.getName());
                        }

                        deferred.resolve(sizes);
                    }
                });
            }
        });

        return deferred.promise();
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
        final boolean publicIp = details.getVmPublicIp();
        final String publicIpName = instance.getName() + PUBLIC_IP_SUFFIX;
        final String networkName = instance.getName() + NETWORK_IP_SUFFIX;
        final String customData = new String(Base64.getEncoder().encode(userData.serialize().getBytes()));
        final String templateName = publicIp ? "/templates/vm-template-pip.json" : "/templates/vm-template.json";
        final String templateValue = AzureUtils.getResourceAsString(templateName);

        final Map<String, JsonValue> params = new HashMap<>();
        params.put("storageAccountName", new JsonValue(details.getStorageId()));
        params.put("vhdImagePath", new JsonValue(details.getImagePath()));
        params.put("vmName", new JsonValue(instance.getName()));
        params.put("vNetName", new JsonValue(details.getNetworkId()));
        params.put("subnetName", new JsonValue(details.getSubnetId()));
        params.put("nicName", new JsonValue(networkName));
        params.put("adminUserName", new JsonValue(details.getUsername()));
        params.put("adminPassword", new JsonValue(details.getPassword()));
        params.put("osType", new JsonValue(details.getOsType()));
        params.put("vmSize", new JsonValue(details.getVmSize()));
        params.put("customData", new JsonValue(customData));
        params.put("serverId", new JsonValue(myServerId));
        params.put("profileId", new JsonValue(userData.getProfileId()));
        params.put("sourceId", new JsonValue(details.getSourceName()));
        if (publicIp){
            params.put("publicIpName", new JsonValue(publicIpName));
        }

        final String parameters = AzureUtils.serializeObject(params);
        final Deployment deployment = new Deployment();
        deployment.setProperties(new DeploymentProperties());
        deployment.getProperties().setMode(DeploymentMode.INCREMENTAL);
        deployment.getProperties().setTemplate(new RawJsonValue(templateValue));
        deployment.getProperties().setParameters(new RawJsonValue(parameters));

        return createDeploymentAsync(groupId, instance.getName(), deployment).then(new DonePipe<Void, Void, Throwable, Object>() {
            @Override
            public Promise<Void, Throwable, Object> pipeDone(Void result) {
                if (publicIp) {
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

    private Promise<Void, Throwable, Object> createDeploymentAsync(String groupId, String deploymentId, Deployment deployment) {
        final DeferredObject<Void, Throwable, Object> deferred = new DeferredObject<>();
        myArmClient.getDeploymentsOperations().createOrUpdateAsync(groupId, deploymentId, deployment, new ServiceCallback<DeploymentExtended>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to create template deployment " + t.getMessage(), t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<DeploymentExtended> result) {
                deferred.resolve(null);
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
    @NotNull
    public Promise<String, Throwable, Void> getVhdOsTypeAsync(@NotNull final String group,
                                                              @NotNull final String storage,
                                                              @NotNull final String filePath) {
        return myManager.when(new Callable<String>() {
            @Override
            public String call() throws Exception {
                final List<CloudBlob> blobs = getBlobs(group, storage, filePath);
                if (blobs.size() == 0) {
                    throw new CloudException("VHD file not found in storage account");
                }
                if (blobs.size() > 1) {
                    return null;
                }

                final CloudBlob blob = blobs.get(0);
                try {
                    if (!filePath.endsWith(blob.getName())) {
                        return null;
                    }

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
        });
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

    /**
     * Gets a list of subscriptions.
     *
     * @return subscriptions.
     */
    public Promise<Map<String, String>, Throwable, Object> getSubscriptionsAsync() {
        final DeferredObject<Map<String, String>, Throwable, Object> deferred = new DeferredObject<>();
        mySubscriptionClient.getSubscriptionsOperations().listAsync(new ListOperationCallback<Subscription>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to get list of subscriptions " + t.getMessage(), t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<List<Subscription>> result) {
                final HashMap<String, String> subscriptions = new HashMap<>();
                for (Subscription subscription : result.getBody()) {
                    subscriptions.put(subscription.getSubscriptionId(), subscription.getDisplayName());
                }

                deferred.resolve(subscriptions);
            }
        });

        return deferred.promise();
    }

    /**
     * Gets a list of networks in the resource group.
     *
     * @param groupName is a resource group name.
     * @return list of networks.
     */
    @NotNull
    public Promise<Map<String, List<String>>, Throwable, Object> getNetworksByGroupAsync(@NotNull final String groupName) {
        final DeferredObject<Map<String, List<String>>, Throwable, Object> deferred = new DeferredObject<>();

        myNetworkClient.getVirtualNetworksOperations().listAsync(groupName, new ListOperationCallback<VirtualNetwork>() {
            @Override
            public void failure(Throwable t) {
                final CloudException exception = new CloudException("Failed to get list of networks: " + t.getMessage(), t);
                deferred.reject(exception);
            }

            @Override
            public void success(ServiceResponse<List<VirtualNetwork>> result) {
                final Map<String, List<String>> networks = new HashMap<>();
                for (VirtualNetwork network : result.getBody()) {
                    final List<String> subNetworks = new ArrayList<>();
                    for (Subnet subnet : network.getSubnets()) {
                        subNetworks.add(subnet.getName());
                    }

                    networks.put(network.getName(), subNetworks);
                }

                deferred.resolve(networks);
            }
        });

        return deferred.promise();
    }
}
