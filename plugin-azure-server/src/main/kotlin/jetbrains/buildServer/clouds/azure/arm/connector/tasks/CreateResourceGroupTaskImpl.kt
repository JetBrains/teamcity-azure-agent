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

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.resources.fluentcore.model.Indexable
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTaskBaseImpl
import rx.Single

data class CreateResourceGroupTaskParameter(
        val groupName: String,
        val region: String)

class CreateResourceGroupTaskImpl : AzureThrottlerTaskBaseImpl<AzureApi, CreateResourceGroupTaskParameter, Indexable>() {
    override fun create(api: AzureApi, taskContext: AzureTaskContext, parameter: CreateResourceGroupTaskParameter): Single<Indexable> {
        return api
                .resourceGroups()
                .define(parameter.groupName)
                .withRegion(parameter.region)
                .createAsync()
                .toSingle()
    }
}

