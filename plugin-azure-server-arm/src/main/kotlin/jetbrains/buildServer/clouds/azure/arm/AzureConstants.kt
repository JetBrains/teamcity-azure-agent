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

import jetbrains.buildServer.clouds.CloudImageParameters

/**
 * ARM constants.
 */
class AzureConstants {
    val environment: String
        get() = ENVIRONMENT

    val tenantId: String
        get() = TENANT_ID

    val clientId: String
        get() = CLIENT_ID

    val clientSecret: String
        get() = CLIENT_SECRET

    val subscriptionId: String
        get() = SUBSCRIPTION_ID

    val deployTarget: String
        get() = DEPLOY_TARGET

    val region: String
        get() = REGION

    val groupId: String
        get() = GROUP_ID

    val imageType: String
        get() = IMAGE_TYPE

    val imageUrl: String
        get() = IMAGE_URL

    val imageId: String
        get() = IMAGE_ID

    val instanceId: String
        get() = INSTANCE_ID

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

    val storageAccountType: String
        get() = STORAGE_ACCOUNT_TYPE

    val template: String
        get() = TEMPLATE

    val imagesData: String
        get() = CloudImageParameters.SOURCE_IMAGES_JSON

    val agentPoolId: String
        get() = CloudImageParameters.AGENT_POOL_ID_FIELD

    companion object {
        const val ENVIRONMENT = "environment"
        const val TENANT_ID = "tenantId"
        const val CLIENT_ID = "clientId"
        const val CLIENT_SECRET = "clientSecret"
        const val SUBSCRIPTION_ID = "subscriptionId"
        const val DEPLOY_TARGET = "deployTarget"
        const val REGION = "region"
        const val GROUP_ID = "groupId"
        const val IMAGE_TYPE = "imageType"
        const val IMAGE_URL = "imageUrl"
        const val IMAGE_ID = "imageId"
        const val INSTANCE_ID = "instanceId"
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
        const val STORAGE_ACCOUNT_TYPE = "storageAccountType"
        const val TEMPLATE = "template"
        const val TAG_SERVER = "teamcity-server"
        const val TAG_PROFILE = "teamcity-profile"
        const val TAG_SOURCE = "teamcity-source"
        const val TAG_DATA_HASH = "teamcity-data-hash"
        const val TAG_IMAGE_HASH = "teamcity-image-hash"
        const val PROFILE_ID = "profileId"
    }
}
