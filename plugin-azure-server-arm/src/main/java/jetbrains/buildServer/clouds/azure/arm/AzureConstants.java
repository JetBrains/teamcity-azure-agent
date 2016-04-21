/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm;

/**
 * ARM constants.
 */
public class AzureConstants {

    public static final String TENANT_ID = "tenantId";
    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_SECRET = "clientSecret";
    public static final String SUBSCRIPTION_ID = "subscriptionId";
    public static final String LOCATION = "location";
    public static final String IMAGE_URL = "imageUrl";
    public static final String OS_TYPE = "osType";
    public static final String NETWORK_ID = "networkId";
    public static final String SUBNET_ID = "subnetId";
    public static final String MAX_INSTANCES_COUNT = "maxInstances";
    public static final String VM_SIZE = "vmSize";
    public static final String VM_NAME_PREFIX = "vmNamePrefix";
    public static final String VM_PUBLIC_IP = "vmPublicIp";
    public static final String VM_USERNAME = "vmUsername";
    public static final String VM_PASSWORD = "vmPassword";
    public static final String TAG_SERVER = "teamcity-server";
    public static final String TAG_PROFILE = "teamcity-profile";
    public static final String TAG_SOURCE = "teamcity-source";

    public String getTenantId() {
        return TENANT_ID;
    }

    public String getClientId() {
        return CLIENT_ID;
    }

    public String getClientSecret() {
        return CLIENT_SECRET;
    }

    public String getSubscriptionId() {
        return SUBSCRIPTION_ID;
    }

    public String getLocation() {
        return LOCATION;
    }

    public String getImageUrl() {
        return IMAGE_URL;
    }

    public String getOsType() {
        return OS_TYPE;
    }

    public String getMaxInstancesCount() {
        return MAX_INSTANCES_COUNT;
    }

    public String getNetworkId() {
        return NETWORK_ID;
    }

    public String getSubnetId() {
        return SUBNET_ID;
    }

    public String getVmSize() {
        return VM_SIZE;
    }

    public String getVmNamePrefix() {
        return VM_NAME_PREFIX;
    }

    public String getVmPublicIp() {
        return VM_PUBLIC_IP;
    }

    public String getVmUsername() {
        return VM_USERNAME;
    }

    public String getVmPassword() {
        return VM_PASSWORD;
    }
}
