/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm.types

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.*
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import jetbrains.buildServer.clouds.azure.arm.utils.AzureUtils
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import kotlinx.coroutines.coroutineScope
import java.util.*

class AzureVhdHandler(private val connector: AzureApiConnector) : AzureHandler {
    @Suppress("UselessCallOnNotNull")
    override suspend fun checkImage(image: AzureCloudImage) = coroutineScope {
        val exceptions = ArrayList<Throwable>()
        val details = image.imageDetails

        details.checkSourceId(exceptions)
        details.checkRegion(exceptions)
        details.checkOsType(exceptions)
        details.checkNetworkId(exceptions)
        details.checkCustomTags(exceptions)
        details.checkResourceGroup(connector, exceptions)
        details.checkServiceExistence("Microsoft.Compute", connector, exceptions)

        val imageUrl = details.imageUrl
        if (imageUrl == null || imageUrl.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Image URL is empty"))
        } else {
            try {
                val region = details.region!!
                connector.getVhdOsType(imageUrl, region)
            } catch (e: Throwable) {
                LOG.infoAndDebugDetails("Failed to get os type for vhd $imageUrl", e)
                exceptions.add(e)
            }
        }

        exceptions
    }

    override suspend fun prepareBuilder(instance: AzureCloudInstance) = coroutineScope {
        val details = instance.image.imageDetails
        val template = AzureUtils.getResourceAsString("/templates/vm-template.json")
        val builder = ArmTemplateBuilder(template)

        if (details.vmPublicIp == true) {
            builder.setPublicIp()
        }

        connector.deleteVmBlobs(instance)

        builder.setParameterValue("vmName", instance.name)
                .addParameter(AzureConstants.IMAGE_URL, "string", "This is the name of the generalized VHD image")
                .setParameterValue(AzureConstants.IMAGE_URL, details.imageUrl!!.trim())
                .setVhdImage()
                .setParameterValue("networkId", details.networkId!!)
                .setParameterValue("subnetName", details.subnetId!!)
                .setParameterValue("adminUserName", details.username!!)
                .setParameterValue("adminPassword", details.password!!)
                .setParameterValue(AzureConstants.OS_TYPE, details.osType!!)
                .setParameterValue("vmSize", details.vmSize!!)

        if (details.enableAcceleratedNetworking == true) {
            builder.enableAcceleratedNerworking()
        }

        builder
    }

    override suspend fun getImageHash(details: AzureCloudImageDetails) = coroutineScope {
        val imageUrl = details.imageUrl!!
        val region = details.region!!
        val metadata = connector.getVhdMetadata(imageUrl, region) ?: emptyMap()
        metadata[METADATA_ETAG] ?: ""
    }

    companion object {
        private val LOG = Logger.getInstance(AzureVhdHandler::class.java.name)
        private const val METADATA_ETAG = "etag"
    }
}
