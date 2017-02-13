/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm

/**
 * ARM constants.
 */
class AzureConstants {
    val tenantId: String
        get() = TENANT_ID

    val clientId: String
        get() = CLIENT_ID

    val clientSecret: String
        get() = CLIENT_SECRET

    val subscriptionId: String
        get() = SUBSCRIPTION_ID

    val location: String
        get() = LOCATION

    val imageUrl: String
        get() = IMAGE_URL

    val osType: String
        get() = OS_TYPE

    val maxInstancesCount: String
        get() = MAX_INSTANCES_COUNT

    val networkId: String
        get() = NETWORK_ID

    val subnetId: String
        get() = SUBNET_ID

    val vmSize: String
        get() = VM_SIZE

    val vmNamePrefix: String
        get() = VM_NAME_PREFIX

    val vmPublicIp: String
        get() = VM_PUBLIC_IP

    val vmUsername: String
        get() = VM_USERNAME

    val vmPassword: String
        get() = VM_PASSWORD

    val reuseVm: String
        get() = REUSE_VM

    companion object {
        const val TENANT_ID = "tenantId"
        const val CLIENT_ID = "clientId"
        const val CLIENT_SECRET = "clientSecret"
        const val SUBSCRIPTION_ID = "subscriptionId"
        const val LOCATION = "location"
        const val IMAGE_URL = "imageUrl"
        const val OS_TYPE = "osType"
        const val NETWORK_ID = "networkId"
        const val SUBNET_ID = "subnetId"
        const val MAX_INSTANCES_COUNT = "maxInstances"
        const val VM_SIZE = "vmSize"
        const val VM_NAME_PREFIX = "vmNamePrefix"
        const val VM_PUBLIC_IP = "vmPublicIp"
        const val VM_USERNAME = "vmUsername"
        const val VM_PASSWORD = "vmPassword"
        const val REUSE_VM = "reuseVm"
        const val TAG_SERVER = "teamcity-server"
        const val TAG_PROFILE = "teamcity-profile"
        const val TAG_SOURCE = "teamcity-source"
    }
}
