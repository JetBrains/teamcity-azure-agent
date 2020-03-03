/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.resources.DeploymentMode
import com.microsoft.azure.management.resources.fluentcore.model.Indexable
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTask
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTaskBaseImpl
import rx.Single

data class CreateDeploymentTaskParameter(
        val groupName: String,
        val deploymentName: String,
        val template: String,
        val params: String)

class CreateDeploymentTaskImpl : AzureThrottlerTaskBaseImpl<Azure, CreateDeploymentTaskParameter, Indexable>() {
    override fun create(api: Azure, parameter: CreateDeploymentTaskParameter): Single<Indexable> {
        return api
                .deployments()
                .define(parameter.deploymentName)
                .withExistingResourceGroup(parameter.groupName)
                .withTemplate(parameter.template)
                .withParameters(parameter.params)
                .withMode(DeploymentMode.INCREMENTAL)
                .createAsync()
                .toSingle()
    }
}
