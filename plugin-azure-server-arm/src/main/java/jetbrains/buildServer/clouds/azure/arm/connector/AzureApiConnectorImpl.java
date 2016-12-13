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
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.RestClient;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.*;
import com.microsoft.azure.management.network.*;
import com.microsoft.azure.management.resources.*;
import com.microsoft.azure.management.storage.*;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.blob.*;
import com.microsoft.rest.ServiceCallback;
import jetbrains.buildServer.clouds.CloudException;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.azure.arm.*;
import jetbrains.buildServer.clouds.azure.arm.connector.models.JsonValue;
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils;
import jetbrains.buildServer.clouds.azure.connector.AzureApiConnectorBase;
import jetbrains.buildServer.clouds.azure.utils.AlphaNumericStringComparator;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.filters.Filter;
import okhttp3.OkHttpClient;
import org.apache.commons.codec.binary.Base64;
import org.jdeferred.*;
import org.jdeferred.impl.DefaultDeferredManager;
import org.jdeferred.impl.DeferredObject;
import org.jdeferred.multiple.MultipleResults;
import org.jdeferred.multiple.OneReject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joda.time.DateTime;
import retrofit2.Retrofit;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides azure arm management capabilities.
 */
public class AzureApiConnectorImpl extends AzureApiConnectorBase<AzureCloudImage, AzureCloudInstance> implements AzureApiConnector {

    private static final Logger LOG = Logger.getInstance(AzureApiConnectorImpl.class.getName());
    private static final String FAILED_TO_GET_INSTANCE_STATUS_FORMAT = "Failed to get instance %s status: %s";
    private static final Pattern RESOURCE_GROUP_PATTERN = Pattern.compile("resourceGroups/(.+)/providers/");
    private static final String PUBLIC_IP_SUFFIX = "-pip";
    private static final String PROVISIONING_STATE = "ProvisioningState/";
    private static final String POWER_STATE = "PowerState/";
    private static final String NOT_FOUND_ERROR = "Invalid status code 404";
    private static final String HTTP_PROXY_HOST = "http.proxyHost";
    private static final String HTTP_PROXY_PORT = "http.proxyPort";
    private static final String HTTPS_PROXY_HOST = "https.proxyHost";
    private static final String HTTPS_PROXY_PORT = "https.proxyPort";
    private static final String HTTP_PROXY_USER = "http.proxyUser";
    private static final String HTTP_PROXY_PASSWORD = "http.proxyPassword";
    private static final List<InstanceStatus> PROVISIONING_STATES = Arrays.asList(
            InstanceStatus.SCHEDULED_TO_START,
            InstanceStatus.SCHEDULED_TO_STOP);
    private final DefaultDeferredManager myManager;
    private final Azure.Authenticated myAzure;
    private String mySubscriptionId = null;
    private String myServerId = null;
    private String myProfileId = null;
    private String myLocation = null;

    public AzureApiConnectorImpl(@NotNull final String tenantId,
                                 @NotNull final String clientId,
                                 @NotNull final String secret) {
        final ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(clientId, tenantId, secret, AzureEnvironment.AZURE);
        final OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        credentials.applyCredentialsFilter(httpClientBuilder);

        final Retrofit.Builder retrofitBuilder = new Retrofit.Builder();
        configureProxy(httpClientBuilder);

        final RestClient client = new RestClient.Builder(httpClientBuilder, retrofitBuilder)
                .withDefaultBaseUrl(credentials.getEnvironment()).build();
        myAzure = Azure.authenticate(client, credentials.getDomain());
        myManager = new DefaultDeferredManager();
    }

    /**
     * Configures http proxy settings.
     *
     * @param builder is a http builder.
     */
    private static void configureProxy(@NotNull final OkHttpClient.Builder builder) {
        // Set HTTP proxy
        final String httpProxyHost = TeamCityProperties.getProperty(HTTP_PROXY_HOST);
        final int httpProxyPort = TeamCityProperties.getInteger(HTTP_PROXY_PORT, 80);
        if (!StringUtil.isEmpty(httpProxyHost)) {
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(httpProxyHost, httpProxyPort)));
        }

        // Set HTTPS proxy
        final String httpsProxyHost = TeamCityProperties.getProperty(HTTPS_PROXY_HOST);
        final int httpsProxyPort = TeamCityProperties.getInteger(HTTPS_PROXY_PORT, 443);
        if (!StringUtil.isEmpty(httpsProxyHost)) {
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(httpsProxyHost, httpsProxyPort)));
        }

        // Set proxy authentication
        final String httpProxyUser = TeamCityProperties.getProperty(HTTP_PROXY_USER);
        final String httpProxyPassword = TeamCityProperties.getProperty(HTTP_PROXY_PASSWORD);
        if (!StringUtil.isEmpty(httpProxyUser)) {
            builder.proxyAuthenticator(new CredentialsAuthenticator(httpProxyUser, httpProxyPassword));
        }
    }

    @Override
    public void test() throws CloudException {
        try {
            myAzure.subscriptions().list();
        } catch (Exception e) {
            final String message = "Failed to get list of groups: " + e.getMessage();
            LOG.debug(message, e);
            throw new CloudException(message, e);
        }
    }

    @Nullable
    @Override
    public InstanceStatus getInstanceStatusIfExists(@NotNull final AzureCloudInstance instance) {
        final AzureInstance azureInstance = new AzureInstance(instance.getName());
        final AzureCloudImageDetails details = instance.getImage().getImageDetails();
        final InstanceStatus[] instanceStatus = {null};

        try {
            myManager.when(getInstanceDataAsync(azureInstance, details)).fail(new FailCallback<Throwable>() {
                @Override
                public void onFail(Throwable result) {
                    final Throwable cause = result.getCause();
                    final String message = String.format(FAILED_TO_GET_INSTANCE_STATUS_FORMAT, instance.getName(), result.getMessage());
                    LOG.debug(message, result);
                    if (cause != null && NOT_FOUND_ERROR.equals(cause.getMessage()) ||
                            PROVISIONING_STATES.contains(instance.getStatus())) {
                        return;
                    }
                    instance.setStatus(InstanceStatus.ERROR);
                    instance.updateErrors(TypedCloudErrorInfo.fromException(result));
                }
            }).done(new DoneCallback<Void>() {
                @Override
                public void onDone(Void result) {
                    final InstanceStatus status = azureInstance.getInstanceStatus();
                    LOG.debug(String.format("Instance %s status is %s", instance.getName(), status));
                    instance.setStatus(status);
                    instance.updateErrors();
                    instanceStatus[0] = status;
                }
            }).waitSafely();
        } catch (InterruptedException e) {
            final String message = String.format(FAILED_TO_GET_INSTANCE_STATUS_FORMAT, instance.getName(), e);
            LOG.debug(message, e);
            final CloudException exception = new CloudException(message, e);
            instance.updateErrors(TypedCloudErrorInfo.fromException(exception));
        }

        return instanceStatus[0];
    }

    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public <R extends AbstractInstance> Map<AzureCloudImage, Map<String, R>> fetchInstances(@NotNull Collection<AzureCloudImage> images) throws CheckedCloudException {
        final Map<AzureCloudImage, Map<String, R>> imageMap = new HashMap<>();
        final List<Promise<Void, Throwable, Void>> promises = new ArrayList<>();

        for (final AzureCloudImage image : images) {
            final Promise<Void, Throwable, Void> promise = fetchInstancesAsync(image).fail(new FailCallback<Throwable>() {
                @Override
                public void onFail(Throwable result) {
                    LOG.warn(String.format("Failed to receive list of image %s instances: %s", image.getName(), result.getMessage()), result);
                    image.updateErrors(TypedCloudErrorInfo.fromException(result));
                }
            }).then(new DonePipe<Map<String, AbstractInstance>, Void, Throwable, Void>() {
                @Override
                public Promise<Void, Throwable, Void> pipeDone(Map<String, AbstractInstance> result) {
                    LOG.debug(String.format("Received list of image %s instances", image.getName()));
                    image.updateErrors();
                    imageMap.put(image, (Map<String, R>) result);
                    return new DeferredObject<Void, Throwable, Void>().resolve(null);
                }
            });

            promises.add(promise);
        }

        if (promises.size() != 0) {
            try {
                myManager.when(promises.toArray(new Promise[]{})).waitSafely();
            } catch (InterruptedException e) {
                final String message = "Failed to get list of images: " + e.getMessage();
                LOG.debug(message, e);
                throw new CloudException(message, e);
            }
        }

        return imageMap;
    }

    @SuppressWarnings("unchecked")
    private <R extends AbstractInstance> Promise<Map<String, R>, Throwable, Void> fetchInstancesAsync(final AzureCloudImage image) {
        final DeferredObject<Map<String, R>, Throwable, Void> deferred = new DeferredObject<>();
        final List<Promise<Void, Throwable, Void>> promises = new ArrayList<>();
        final Map<String, R> instances = new HashMap<>();
        final List<Throwable> exceptions = new ArrayList<>();
        final AzureCloudImageDetails details = image.getImageDetails();

        getVirtualMachinesAsync().fail(new FailCallback<Throwable>() {
            @Override
            public void onFail(Throwable t) {
                final String message = String.format("Failed to get list of instances for cloud image %s: %s", image.getName(), t.getMessage());
                LOG.debug(message, t);
                final CloudException exception = new CloudException(message, t);
                exceptions.add(exception);
                deferred.reject(exception);
            }
        }).then(new DoneCallback<List<VirtualMachine>>() {
            @Override
            public void onDone(List<VirtualMachine> machines) {
                for (VirtualMachine virtualMachine : machines) {
                    final String name = virtualMachine.name();
                    if (!StringUtil.startsWithIgnoreCase(name, details.getVmNamePrefix())) {
                        LOG.debug("Ignore vm with name " + name);
                        continue;
                    }

                    final Map<String, String> tags = virtualMachine.tags();
                    if (tags == null) {
                        LOG.debug("Ignore vm without tags");
                        continue;
                    }

                    final String serverId = tags.get(AzureConstants.TAG_SERVER);
                    if (!StringUtil.areEqual(serverId, myServerId)) {
                        LOG.debug("Ignore vm with invalid server tag " + serverId);
                        continue;
                    }

                    final String profileId = tags.get(AzureConstants.TAG_PROFILE);
                    if (!StringUtil.areEqual(profileId, myProfileId)) {
                        LOG.debug("Ignore vm with invalid profile tag " + profileId);
                        continue;
                    }

                    final String sourceName = tags.get(AzureConstants.TAG_SOURCE);
                    if (!StringUtil.areEqualIgnoringCase(sourceName, details.getSourceName())) {
                        LOG.debug("Ignore vm with invalid source tag " + sourceName);
                        continue;
                    }

                    final AzureInstance instance = new AzureInstance(name);
                    final Promise<Void, Throwable, Void> promise = getInstanceDataAsync(instance, details);
                    promise.fail(new FailCallback<Throwable>() {
                        @Override
                        public void onFail(Throwable result) {
                            LOG.debug(String.format("Failed to receive vm %s data: %s", name, result.getMessage()), result);
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

    private Promise<List<VirtualMachine>, Throwable, Void> getVirtualMachinesAsync() {
        final Deferred<List<VirtualMachine>, Throwable, Void> deferred = new DeferredObject<>();

        try {
            PagedList<VirtualMachine> list = myAzure.withSubscription(mySubscriptionId).virtualMachines().list();
            LOG.debug("Received list of virtual machines");
            deferred.resolve(list);
        } catch (Throwable t) {
            final String message = "Failed to get list of virtual machines: " + t.getMessage();
            LOG.debug(message, t);
            final CloudException exception = new CloudException(message, t);
            deferred.reject(exception);
        }

        return deferred.promise();
    }

    private Promise<Void, Throwable, Void> getInstanceDataAsync(final AzureInstance instance, final AzureCloudImageDetails details) {
        final String name = instance.getName();
        final Promise<Void, Throwable, Void> instanceViewPromise = getVirtualMachineAsync(name, name).then(new DonePipe<VirtualMachine, Void, Throwable, Void>() {
            @Override
            public Promise<Void, Throwable, Void> pipeDone(VirtualMachine machine) {
                LOG.debug(String.format("Received virtual machine %s info", name));

                for (InstanceViewStatus status : machine.instanceView().statuses()) {
                    final String code = status.code();
                    if (code.startsWith(PROVISIONING_STATE)) {
                        instance.setProvisioningState(code.substring(PROVISIONING_STATE.length()));
                        final DateTime dateTime = status.time();
                        if (dateTime != null) {
                            instance.setStartDate(dateTime.toDate());
                        }
                    }
                    if (code.startsWith(POWER_STATE)) {
                        instance.setPowerState(code.substring(POWER_STATE.length()));
                    }
                }

                return new DeferredObject<Void, Throwable, Void>().resolve(null);
            }
        });

        final Promise<Void, Throwable, Void> publicIpPromise;
        if (details.getVmPublicIp() && instance.getIpAddress() == null) {
            final String pipName = name + PUBLIC_IP_SUFFIX;
            publicIpPromise = getPublicIpAsync(name, pipName).then(new DonePipe<String, Void, Throwable, Void>() {
                @Override
                public Promise<Void, Throwable, Void> pipeDone(String result) {
                    LOG.debug(String.format("Received public ip %s for virtual machine %s", result, name));

                    if (!StringUtil.isEmpty(result)) {
                        instance.setIpAddress(result);
                    }

                    return new DeferredObject<Void, Throwable, Void>().resolve(null);
                }
            });
        } else {
            publicIpPromise = new DeferredObject<Void, Throwable, Void>().resolve(null);
        }

        final DeferredObject<Void, Throwable, Void> deferred = new DeferredObject<>();
        myManager.when(instanceViewPromise, publicIpPromise).done(new DoneCallback<MultipleResults>() {
            @Override
            public void onDone(MultipleResults result) {
                deferred.resolve(null);
            }
        }).fail(new FailCallback<OneReject>() {
            @Override
            public void onFail(OneReject result) {
                deferred.reject((Throwable) result.getReject());
            }
        });

        return deferred.promise();
    }

    private Promise<VirtualMachine, Throwable, Void> getVirtualMachineAsync(final String groupId, final String name) {
        final DeferredObject<VirtualMachine, Throwable, Void> deferred = new DeferredObject<>();

        try {
            VirtualMachine machine = myAzure.withSubscription(mySubscriptionId).virtualMachines().getByGroup(groupId, name);
            machine.refreshInstanceView();
            LOG.debug(String.format("Received virtual machine %s info", name));
            deferred.resolve(machine);
        } catch (Throwable t) {
            final String message = "Failed to get virtual machine info: " + t.getMessage();
            LOG.debug(message, t);
            final CloudException exception = new CloudException(message, t);
            deferred.reject(exception);
        }

        return deferred.promise();
    }

    private Promise<String, Throwable, Void> getPublicIpAsync(final String groupId, final String name) {
        final DeferredObject<String, Throwable, Void> deferred = new DeferredObject<>();

        try {
            PublicIpAddress ipAddress = myAzure.withSubscription(mySubscriptionId).publicIpAddresses().getByGroup(groupId, name);
            LOG.debug(String.format("Received public ip %s for %s", ipAddress, name));
            deferred.resolve(ipAddress.ipAddress());
        } catch (Throwable t) {
            final String message = String.format("Failed to get public ip address %s info: %s", name, t.getMessage());
            LOG.debug(message, t);
            final CloudException exception = new CloudException(message, t);
            deferred.reject(exception);
        }

        return deferred.promise();
    }

    @NotNull
    @Override
    public TypedCloudErrorInfo[] checkImage(@NotNull AzureCloudImage image) {
        final List<Throwable> exceptions = new ArrayList<>();
        final String imageUrl = image.getImageDetails().getImageUrl();
        final Promise<String, Throwable, Void> promise = getVhdOsTypeAsync(imageUrl).fail(new FailCallback<Throwable>() {
            @Override
            public void onFail(Throwable result) {
                LOG.debug("Failed to get os type for vhd " + imageUrl, result);
                exceptions.add(result);
            }
        });

        try {
            promise.waitSafely();
        } catch (InterruptedException e) {
            LOG.debug("Failed to wait for receiving vhd type " + imageUrl, e);
            exceptions.add(e);
        }

        if (exceptions.size() == 0) {
            return new TypedCloudErrorInfo[0];
        }

        final TypedCloudErrorInfo[] errors = new TypedCloudErrorInfo[exceptions.size()];
        for (int i = 0; i < exceptions.size(); i++) {
            errors[i] = TypedCloudErrorInfo.fromException(exceptions.get(i));
        }

        return errors;
    }

    @NotNull
    @Override
    public TypedCloudErrorInfo[] checkInstance(@NotNull AzureCloudInstance instance) {
        return new TypedCloudErrorInfo[0];
    }

    /**
     * Gets a list of VM sizes.
     *
     * @return list of sizes.
     */
    @NotNull
    @Override
    public Promise<List<String>, Throwable, Void> getVmSizesAsync() {
        final DeferredObject<List<String>, Throwable, Void> deferred = new DeferredObject<>();

        try {
            PagedList<VirtualMachineSize> vmSizes = myAzure.withSubscription(mySubscriptionId).virtualMachines().sizes().listByRegion(myLocation);
            LOG.debug("Received list of vm sizes in location " + myLocation);

            final Comparator<String> comparator = new AlphaNumericStringComparator();
            Collections.sort(vmSizes, new Comparator<VirtualMachineSize>() {
                @Override
                public int compare(VirtualMachineSize o1, VirtualMachineSize o2) {
                    final String size1 = o1.name();
                    final String size2 = o2.name();
                    return comparator.compare(size1, size2);
                }
            });

            final List<String> sizes = new ArrayList<>(vmSizes.size());
            for (VirtualMachineSize vmSize : vmSizes) {
                sizes.add(vmSize.name());
            }

            deferred.resolve(sizes);
        } catch (Throwable t) {
            final String message = String.format("Failed to get list of vm sizes in location %s: %s", myLocation, t.getMessage());
            LOG.debug(message, t);
            final CloudException exception = new CloudException(message, t);
            deferred.reject(exception);
        }

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
    @Override
    public Promise<Void, Throwable, Void> createVmAsync(@NotNull final AzureCloudInstance instance,
                                                        @NotNull final CloudInstanceUserData userData) {
        final String name = instance.getName();
        final String customData;
        try {
            customData = Base64.encodeBase64String(userData.serialize().getBytes("UTF-8"));
        } catch (Exception e) {
            final String message = String.format("Failed to encode custom data for instance %s: %s", name, e.getMessage());
            LOG.debug(message, e);
            final CloudException exception = new CloudException(message, e);
            return new DeferredObject<Void, Throwable, Void>().reject(exception);
        }

        final AzureCloudImageDetails details = instance.getImage().getImageDetails();
        return createResourceGroupAsync(name, myLocation).then(new DonePipe<Void, Void, Throwable, Void>() {
            @Override
            public Promise<Void, Throwable, Void> pipeDone(Void result) {
                LOG.debug(String.format("Created resource group %s in location %s", name, myLocation));

                final boolean publicIp = details.getVmPublicIp();
                final String templateName = publicIp ? "/templates/vm-template-pip.json" : "/templates/vm-template.json";
                final String templateValue = AzureUtils.getResourceAsString(templateName);

                final Map<String, JsonValue> params = new HashMap<>();
                params.put("imageUrl", new JsonValue(details.getImageUrl()));
                params.put("vmName", new JsonValue(name));
                params.put("networkId", new JsonValue(details.getNetworkId()));
                params.put("subnetName", new JsonValue(details.getSubnetId()));
                params.put("adminUserName", new JsonValue(details.getUsername()));
                params.put("adminPassword", new JsonValue(details.getPassword()));
                params.put("osType", new JsonValue(details.getOsType()));
                params.put("vmSize", new JsonValue(details.getVmSize()));
                params.put("customData", new JsonValue(customData));
                params.put("serverId", new JsonValue(myServerId));
                params.put("profileId", new JsonValue(userData.getProfileId()));
                params.put("sourceId", new JsonValue(details.getSourceName()));

                final String parameters = AzureUtils.serializeObject(params);
                LOG.debug("Deployment template: \n" + templateValue);
                String message = "Deployment parameters:";
                for (Map.Entry<String, JsonValue> param : params.entrySet()) {
                    if (param.getKey().equals("adminPassword")) {
                        message += String.format("\n - '%s' = '**********'", param.getKey());
                    } else {
                        message += String.format("\n - '%s' = '%s'", param.getKey(), param.getValue().getValue());
                    }
                }
                LOG.debug(message);

                return createDeploymentAsync(name, name, templateValue, parameters);
            }
        });
    }

    private Promise<Void, Throwable, Void> createResourceGroupAsync(final String groupId, String location) {
        final DeferredObject<Void, Throwable, Void> deferred = new DeferredObject<>();
        myAzure.withSubscription(mySubscriptionId).resourceGroups().define(groupId)
                .withRegion(location)
                .createAsync(new ServiceCallback<ResourceGroup>() {
                    @Override
                    public void failure(Throwable t) {
                        final String message = String.format("Failed to create resource group %s: %s", groupId, t.getMessage());
                        LOG.debug(message, t);
                        final CloudException exception = new CloudException(message, t);
                        deferred.reject(exception);
                    }

                    @Override
                    public void success(ResourceGroup result) {
                        deferred.resolve(null);
                    }
                });

        return deferred.promise();
    }

    private Promise<Void, Throwable, Void> createDeploymentAsync(final String groupId, String deploymentId, String template, String params) {
        final DeferredObject<Void, Throwable, Void> deferred = new DeferredObject<>();
        try {
            myAzure.withSubscription(mySubscriptionId).deployments().define(deploymentId)
                    .withExistingResourceGroup(groupId)
                    .withTemplate(template)
                    .withParameters(params)
                    .withMode(DeploymentMode.INCREMENTAL)
                    .createAsync(new ServiceCallback<Deployment>() {
                        @Override
                        public void failure(Throwable t) {
                            String message = String.format("Failed to create deployment in resource group %s: %s", groupId, t.getMessage());
                            LOG.debug(message, t);

                            if (t instanceof com.microsoft.azure.CloudException) {
                                com.microsoft.azure.CloudException cloudException = (com.microsoft.azure.CloudException) t;
                                com.microsoft.azure.CloudError cloudError = cloudException.getBody();
                                List<com.microsoft.azure.CloudError> details = cloudError.getDetails();
                                for (com.microsoft.azure.CloudError ce : details) {
                                    message += "\n" + ce.getMessage();
                                }
                            }
                            final CloudException exception = new CloudException(message, t);
                            deferred.reject(exception);
                        }

                        @Override
                        public void success(Deployment result) {
                            deferred.resolve(null);
                        }
                    });
        } catch (IOException e) {
            String message = String.format("Failed to specify deployment template: %s", e.getMessage());
            LOG.debug(message, e);
            deferred.reject(e);
        }

        return deferred.promise();
    }

    /**
     * Deletes a cloud instance.
     *
     * @param instance is a cloud instance.
     * @return promise.
     */
    @NotNull
    @Override
    public Promise<Void, Throwable, Void> deleteVmAsync(@NotNull final AzureCloudInstance instance) {
        final String name = instance.getName();
        return deleteResourceGroupAsync(instance.getName()).then(new DonePipe<Void, List<CloudBlob>, Throwable, Void>() {
            @Override
            public Promise<List<CloudBlob>, Throwable, Void> pipeDone(Void result) {
                final String url = instance.getImage().getImageDetails().getImageUrl();
                final URI storageBlobs;
                try {
                    final URI imageUrl = new URI(url);
                    storageBlobs = new URI(imageUrl.getScheme(), imageUrl.getHost(), "/vhds/" + name, null);
                } catch (URISyntaxException e) {
                    final String message = String.format("Failed to parse VHD image URL %s for instance %s: %s", url, name, e.getMessage());
                    LOG.debug(message, e);
                    final CloudException exception = new CloudException(message, e);
                    return new DeferredObject<List<CloudBlob>, Throwable, Void>().reject(exception);
                }

                return getBlobsAsync(storageBlobs);
            }
        }).then(new DonePipe<List<CloudBlob>, Void, Throwable, Void>() {
            @Override
            public Promise<Void, Throwable, Void> pipeDone(List<CloudBlob> blobs) {
                for (CloudBlob blob : blobs) {
                    try {
                        blob.deleteIfExists();
                    } catch (Exception e) {
                        final String message = String.format("Failed to delete blob %s for instance %s: %s", blob.getUri(), name, e.getMessage());
                        LOG.warnAndDebugDetails(message, e);
                    }
                }

                return new DeferredObject<Void, Throwable, Void>().resolve(null);
            }
        });
    }

    private Promise<Void, Throwable, Void> deleteResourceGroupAsync(final String groupId) {
        final DeferredObject<Void, Throwable, Void> deferred = new DeferredObject<>();
        myAzure.withSubscription(mySubscriptionId).resourceGroups().deleteAsync(groupId, new ServiceCallback<Void>() {
            @Override
            public void failure(Throwable t) {
                final String message = String.format("Failed to delete resource group %s: %s", groupId, t.getMessage());
                LOG.debug(message, t);
                final CloudException exception = new CloudException(message, t);
                deferred.reject(exception);
            }

            @Override
            public void success(Void result) {
                LOG.debug(String.format("Resource group %s has been successfully deleted", groupId));
                deferred.resolve(result);
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
    @Override
    public Promise<Void, Throwable, Void> restartVmAsync(@NotNull final AzureCloudInstance instance) {
        final DeferredObject<Void, Throwable, Void> deferred = new DeferredObject<>();
        final String name = instance.getName();
        try {
            myAzure.withSubscription(mySubscriptionId).virtualMachines().getByGroup(name, name).restart();
            LOG.debug(String.format("Virtual machine %s has been successfully restarted", name));
            deferred.resolve(null);
        } catch (Throwable t) {
            final String message = String.format("Failed to restart virtual machine %s: %s", name, t.getMessage());
            LOG.debug(message, t);
            final CloudException exception = new CloudException(message, t);
            deferred.reject(exception);
        }

        return deferred.promise();
    }

    @Override
    public Promise<Void, Throwable, Void> startVmAsync(@NotNull AzureCloudInstance instance) {
        final DeferredObject<Void, Throwable, Void> deferred = new DeferredObject<>();
        final String name = instance.getName();
        try {
            myAzure.withSubscription(mySubscriptionId).virtualMachines().getByGroup(name, name).start();
            LOG.debug(String.format("Virtual machine %s has been successfully started", name));
            deferred.resolve(null);
        } catch (Throwable t) {
            final String message = String.format("Failed to start virtual machine %s: %s", name, t.getMessage());
            LOG.debug(message, t);
            final CloudException exception = new CloudException(message, t);
            deferred.reject(exception);
        }

        return deferred.promise();
    }

    @Override
    public Promise<Void, Throwable, Void> stopVmAsync(@NotNull AzureCloudInstance instance) {
        final DeferredObject<Void, Throwable, Void> deferred = new DeferredObject<>();
        final String name = instance.getName();
        try {
            myAzure.withSubscription(mySubscriptionId).virtualMachines().getByGroup(name, name).deallocate();
            LOG.debug(String.format("Virtual machine %s has been successfully stopped", name));
            deferred.resolve(null);
        } catch (Throwable t) {
            final String message = String.format("Failed to stop virtual machine %s: %s", name, t.getMessage());
            LOG.debug(message, t);
            final CloudException exception = new CloudException(message, t);
            deferred.reject(exception);
        }

        return deferred.promise();
    }

    /**
     * Gets an OS type of VHD image.
     *
     * @param imageUrl is image URL.
     * @return OS type (Linux, Windows).
     */
    @NotNull
    @Override
    public Promise<String, Throwable, Void> getVhdOsTypeAsync(@NotNull final String imageUrl) {
        final DeferredObject<String, Throwable, Void> deferred = new DeferredObject<>();
        final URI uri;
        try {
            uri = URI.create(imageUrl);
        } catch (Exception e) {
            final String message = String.format("Invalid image URL %s: %s", imageUrl, e.getMessage());
            LOG.debug(message, e);
            final CloudException exception = new CloudException(message, e);
            return new DeferredObject<String, Throwable, Void>().reject(exception);
        }

        getBlobsAsync(uri).then(new DoneCallback<List<CloudBlob>>() {
            @Override
            public void onDone(List<CloudBlob> blobs) {
                if (blobs.size() == 0) {
                    final String message = String.format("VHD file %s not found in storage account", imageUrl);
                    LOG.debug(message);
                    deferred.reject(new CloudException(message));
                    return;
                }
                if (blobs.size() > 1) {
                    LOG.debug("Found more than one blobs for url " + imageUrl);
                    deferred.resolve(null);
                    return;
                }

                final CloudBlob blob = blobs.get(0);
                try {
                    if (!StringUtil.endsWithIgnoreCase(imageUrl, blob.getName())) {
                        LOG.debug(String.format("For url %s found blob with invalid name %s", imageUrl, blob.getName()));
                        deferred.resolve(null);
                        return;
                    }

                    blob.downloadAttributes();
                } catch (Exception e) {
                    final String message = "Failed to access storage blob: " + e.getMessage();
                    LOG.debug(message, e);
                    deferred.reject(new CloudException(message, e));
                    return;
                }

                final Map<String, String> metadata = blob.getMetadata();
                if (!"OSDisk".equals(metadata.get("MicrosoftAzureCompute_ImageType"))) {
                    LOG.debug(String.format("Found blob %s with invalid OSDisk metadata", blob.getUri()));
                    deferred.resolve(null);
                    return;
                }

                if (!"Generalized".equals(metadata.get("MicrosoftAzureCompute_OSState"))) {
                    LOG.debug(String.format("Found blob %s with invalid Generalized metadata", blob.getUri()));
                    deferred.reject(new CloudException("VHD image should be generalized."));
                    return;
                }

                deferred.resolve(metadata.get("MicrosoftAzureCompute_OSType"));
            }
        }, new FailCallback<Throwable>() {
            @Override
            public void onFail(Throwable result) {
                LOG.debug(String.format("Failed to receive blobs for url %s: %s", imageUrl, result.getMessage()));
                deferred.reject(result);
            }
        });

        return deferred.promise();
    }

    private Promise<List<CloudBlob>, Throwable, Void> getBlobsAsync(final URI uri) {
        if (uri.getHost() == null || uri.getPath() == null) {
            return new DeferredObject<List<CloudBlob>, Throwable, Void>()
                    .reject(new CloudException("Invalid URL"));
        }

        final int hostSuffix = uri.getHost().indexOf(".blob.core.windows.net");
        if (hostSuffix <= 0) {
            return new DeferredObject<List<CloudBlob>, Throwable, Void>()
                    .reject(new CloudException("Invalid host name"));
        }

        final String storage = uri.getHost().substring(0, hostSuffix);
        final String filesPrefix = uri.getPath();
        final int slash = filesPrefix.indexOf("/", 1);
        if (slash <= 0) {
            return new DeferredObject<List<CloudBlob>, Throwable, Void>()
                    .reject(new CloudException("File path must include container name"));
        }

        return getStorageAccountAsync(storage).then(new DonePipe<CloudStorageAccount, List<CloudBlob>, Throwable, Void>() {
            @Override
            public Promise<List<CloudBlob>, Throwable, Void> pipeDone(CloudStorageAccount account) {
                final String containerName = filesPrefix.substring(1, slash);
                final CloudBlobContainer container;
                try {
                    container = account.createCloudBlobClient().getContainerReference(containerName);
                } catch (Throwable e) {
                    final String message = String.format("Failed to connect to storage account %s: %s", storage, e.getMessage());
                    LOG.debug(message, e);
                    final CloudException exception = new CloudException(message, e);
                    return new DeferredObject<List<CloudBlob>, Throwable, Void>().reject(exception);
                }

                final String blobName = filesPrefix.substring(slash + 1);
                final List<CloudBlob> blobs = new ArrayList<>();
                try {
                    for (ListBlobItem item : container.listBlobs(blobName)) {
                        blobs.add((CloudBlob) item);
                    }
                } catch (Exception e) {
                    final String message = String.format("Failed to list container's %s blobs: %s", containerName, e.getMessage());
                    LOG.debug(message, e);
                    final CloudException exception = new CloudException(message, e);
                    return new DeferredObject<List<CloudBlob>, Throwable, Void>().reject(exception);
                }

                return new DeferredObject<List<CloudBlob>, Throwable, Void>().resolve(blobs);
            }
        });
    }

    private Promise<CloudStorageAccount, Throwable, Void> getStorageAccountAsync(final String storage) {
        return getStorageAccountsAsync().then(new DonePipe<List<StorageAccount>, List<StorageAccountKey>, Throwable, Void>() {
            @Override
            public Promise<List<StorageAccountKey>, Throwable, Void> pipeDone(List<StorageAccount> accounts) {
                final StorageAccount account = CollectionsUtil.findFirst(accounts, new Filter<StorageAccount>() {
                    @Override
                    public boolean accept(@NotNull StorageAccount account) {
                        return account.name().equalsIgnoreCase(storage);
                    }
                });

                if (account == null) {
                    final String message = String.format("Storage account %s not found", storage);
                    LOG.debug(message);
                    return new DeferredObject<List<StorageAccountKey>, Throwable, Void>().reject(new CloudException(message));
                }

                if (!account.regionName().equalsIgnoreCase(myLocation)) {
                    final String message = String.format("VHD image should be located in storage account in the %s region", myLocation);
                    LOG.debug(message);
                    return new DeferredObject<List<StorageAccountKey>, Throwable, Void>().reject(new CloudException(message));
                }

                final Matcher groupMatcher = RESOURCE_GROUP_PATTERN.matcher(account.id());
                if (!groupMatcher.find()) {
                    final String message = String.format("Invalid storage account identifier %s", account.id());
                    LOG.debug(message);
                    return new DeferredObject<List<StorageAccountKey>, Throwable, Void>().reject(new CloudException(message));
                }

                return getStorageAccountKeysAsync(groupMatcher.group(1), storage);
            }
        }).then(new DonePipe<List<StorageAccountKey>, CloudStorageAccount, Throwable, Void>() {
            @Override
            public Promise<CloudStorageAccount, Throwable, Void> pipeDone(List<StorageAccountKey> keys) {
                final DeferredObject<CloudStorageAccount, Throwable, Void> deferred = new DeferredObject<>();
                try {
                    deferred.resolve(new CloudStorageAccount(new StorageCredentialsAccountAndKey(storage, keys.get(0).value())));
                } catch (URISyntaxException e) {
                    final String message = String.format("Invalid storage account %s credentials: %s", storage, e.getMessage());
                    LOG.debug(message);
                    final CloudException exception = new CloudException(message, e);
                    deferred.reject(exception);
                }

                return deferred;
            }
        });
    }

    private Promise<List<StorageAccount>, Throwable, Void> getStorageAccountsAsync() {
        final DeferredObject<List<StorageAccount>, Throwable, Void> deferred = new DeferredObject<>();

        try {
            PagedList<StorageAccount> accounts = myAzure.withSubscription(mySubscriptionId).storageAccounts().list();
            LOG.debug("Received list of storage accounts");
            deferred.resolve(accounts);
        } catch (Throwable t) {
            final String message = "Failed to get list of storage accounts: " + t.getMessage();
            LOG.debug(message, t);
            final CloudException exception = new CloudException(message, t);
            deferred.reject(exception);
        }

        return deferred.promise();
    }

    private Promise<List<StorageAccountKey>, Throwable, Void> getStorageAccountKeysAsync(final String groupName,
                                                                                    final String storageName) {
        final DeferredObject<List<StorageAccountKey>, Throwable, Void> deferred = new DeferredObject<>();

        try {
            StorageAccount account = myAzure.withSubscription(mySubscriptionId).storageAccounts().getByGroup(groupName, storageName);
            LOG.debug("Received keys for storage account " + storageName);
            deferred.resolve(account.getKeys());
        } catch (Throwable t) {
            final String message = String.format("Failed to get storage account %s key: %s", storageName, t.getMessage());
            LOG.debug(message, t);
            final CloudException exception = new CloudException(message, t);
            deferred.reject(exception);
        }

        return deferred.promise();
    }

    /**
     * Gets a list of subscriptions.
     *
     * @return subscriptions.
     */
    @NotNull
    @Override
    public Promise<Map<String, String>, Throwable, Void> getSubscriptionsAsync() {
        final DeferredObject<Map<String, String>, Throwable, Void> deferred = new DeferredObject<>();

        try {
            PagedList<Subscription> list = myAzure.subscriptions().list();
            LOG.debug("Received list of subscriptions");

            final Map<String, String> subscriptions = new LinkedHashMap<>();
            Collections.sort(list, new Comparator<Subscription>() {
                @Override
                public int compare(Subscription o1, Subscription o2) {
                    return o1.displayName().compareTo(o2.displayName());
                }
            });

            for (Subscription subscription : list) {
                subscriptions.put(subscription.subscriptionId(), subscription.displayName());
            }

            deferred.resolve(subscriptions);
        } catch (Throwable t) {
            final String message = "Failed to get list of subscriptions " + t.getMessage();
            LOG.debug(message, t);
            final CloudException exception = new CloudException(message, t);
            deferred.reject(exception);
        }


        return deferred.promise();
    }

    /**
     * Gets a list of locations.
     *
     * @return locations.
     */
    @NotNull
    @Override
    public Promise<Map<String, String>, Throwable, Void> getLocationsAsync(@NotNull final String subscription) {
        final DeferredObject<Map<String, String>, Throwable, Void> deferred = new DeferredObject<>();

        try {
            PagedList<Location> list = myAzure.subscriptions().getByName(mySubscriptionId).listLocations();
            LOG.debug("Received list of locations in subscription " + subscription);

            final Map<String, String> locations = new LinkedHashMap<>();
            Collections.sort(list, new Comparator<Location>() {
                @Override
                public int compare(Location o1, Location o2) {
                    return o1.displayName().compareTo(o2.displayName());
                }
            });

            for (Location location : list) {
                locations.put(location.name(), location.displayName());
            }

            deferred.resolve(locations);
        } catch (Throwable t) {
            final String message = String.format("Failed to get list of locations in subscription %s: %s", subscription, t.getMessage());
            LOG.debug(message, t);
            final CloudException exception = new CloudException(message, t);
            deferred.reject(exception);
        }

        return deferred.promise();
    }

    /**
     * Gets a list of networks.
     *
     * @return list of networks.
     */
    @NotNull
    @Override
    public Promise<Map<String, List<String>>, Throwable, Void> getNetworksAsync() {
        final DeferredObject<Map<String, List<String>>, Throwable, Void> deferred = new DeferredObject<>();

        try {
            PagedList<Network> list = myAzure.withSubscription(mySubscriptionId).networks().list();
            LOG.debug("Received list of networks");

            final Map<String, List<String>> networks = new LinkedHashMap<>();
            for (Network network : list) {
                if (!network.regionName().equalsIgnoreCase(myLocation)) continue;

                final List<String> subNetworks = new ArrayList<>(network.subnets().keySet());
                networks.put(network.id(), subNetworks);
            }

            deferred.resolve(networks);
        } catch (Throwable t) {
            final String message = "Failed to get list of networks: " + t.getMessage();
            LOG.debug(message, t);
            final CloudException exception = new CloudException(message, t);
            deferred.reject(exception);
        }

        return deferred.promise();
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
     * Sets subscription identifier for ARM clients.
     *
     * @param subscriptionId is a an identifier.
     */
    public void setSubscriptionId(@NotNull String subscriptionId) {
        mySubscriptionId = subscriptionId;
    }

    /**
     * Sets a target location for resources.
     *
     * @param location is a location.
     */
    public void setLocation(@NotNull String location) {
        myLocation = location;
    }
}
