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
    public static final String GROUP_ID = "groupId";
    public static final String STORAGE_ID = "storageId";
    public static final String IMAGE_PATH = "imagePath";
    public static final String MAX_INSTANCES_COUNT = "maxInstances";
    public static final String VM_SIZE = "vmSize";
    public static final String VM_NAME_PREFIX = "vmNamePrefix";
    public static final String VM_USERNAME = "vmUsername";
    public static final String VM_PASSWORD = "vmPassword";
    public static final String OS_TYPE = "osType";
    public static final String TAG_SERVER = "teamcity-server";
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

    public String getGroupId() {
        return GROUP_ID;
    }

    public String getSubscriptionId() {
        return SUBSCRIPTION_ID;
    }

    public String getMaxInstancesCount() {
        return MAX_INSTANCES_COUNT;
    }

    public String getStorageId() {
        return STORAGE_ID;
    }

    public String getImagePath() {
        return IMAGE_PATH;
    }

    public String getVmNamePrefix() {
        return VM_NAME_PREFIX;
    }

    public String getVmSize() {
        return VM_SIZE;
    }

    public String getVmUsername() {
        return VM_USERNAME;
    }

    public String getVmPassword() {
        return VM_PASSWORD;
    }

    public String getOsType() {
        return OS_TYPE;
    }
}
