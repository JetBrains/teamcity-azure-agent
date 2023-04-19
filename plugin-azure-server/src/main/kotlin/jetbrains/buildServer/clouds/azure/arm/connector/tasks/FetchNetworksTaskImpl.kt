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

import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

data class FetchNetworksTaskNetworkDescriptor(val id: String, val regionName: String, val subnets: List<String>)

class FetchNetworksTaskImpl : AzureThrottlerCacheableTaskBaseImpl<Unit, List<FetchNetworksTaskNetworkDescriptor>>() {
    override fun createQuery(api: AzureApi, parameter: Unit): Single<List<FetchNetworksTaskNetworkDescriptor>> {
        return api
                .networks()
                .listAsync()
                .map { FetchNetworksTaskNetworkDescriptor(it.id(), it.regionName(), it.subnets().keys.toList())}
                .toList()
                .last()
                .toSingle()
    }
}
