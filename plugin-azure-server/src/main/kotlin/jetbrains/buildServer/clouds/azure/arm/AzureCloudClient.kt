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

package jetbrains.buildServer.clouds.azure.arm

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.CloudClientParameters
import jetbrains.buildServer.clouds.azure.AzureCloudClientBase
import jetbrains.buildServer.clouds.azure.AzureCloudImagesHolder
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerSchedulersProvider
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector
import jetbrains.buildServer.log.LogUtil
import jetbrains.buildServer.serverSide.BuildAgentManagerEx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * ARM cloud client.
 */
class AzureCloudClient(
    private val params: CloudClientParameters,
    apiConnector: CloudApiConnector<AzureCloudImage, AzureCloudInstance>,
    imagesHolder: AzureCloudImagesHolder,
    schedulersProvider: AzureThrottlerSchedulersProvider,
    private val buildAgentManager: BuildAgentManagerEx
)
    : AzureCloudClientBase<AzureCloudInstance, AzureCloudImage, AzureCloudImageDetails>(params, apiConnector, imagesHolder) {
    private val myScope: CoroutineScope
    private val myInstanceListener = object : AzureInstanceEventListener {
        override fun instanceTerminated(instance: AzureCloudInstance) = unregisterAgent(instance)
        override fun instanceFailedToCreate(instance: AzureCloudInstance, throwable: Throwable) = unregisterAgent(instance)
    }

    init {
        myScope = CoroutineScope(SupervisorJob() + schedulersProvider.getDispatcher())
    }

    override fun createImage(imageDetails: AzureCloudImageDetails): AzureCloudImage {
        if (imageDetails.target == AzureCloudDeployTarget.NewGroup && imageDetails.region.isNullOrEmpty()) {
            imageDetails.region = params.getParameter(AzureConstants.REGION)
        }

        return AzureCloudImage(imageDetails, myApiConnector as AzureApiConnector, myScope, myInstanceListener)
    }

    private fun unregisterAgent(instance: AzureCloudInstance) {
        buildAgentManager.findAgentByName(instance.name, true)?.let {
            LOG.debug("Found agent: ${LogUtil.describe(it)}. IsRegistered:${it.isRegistered}")
            if (it.isCloudAgent && it.isRegistered && findInstanceByAgent(it) == instance) {
                LOG.info("Azure instance '${it.name}' has been terminated but the agent is still registered. Unregistering agent: ${LogUtil.describe(it)}")
                buildAgentManager.unregisterAgent(it.id, "Cloud instance has gone")
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(AzureCloudClient::class.java.name)
    }
}
