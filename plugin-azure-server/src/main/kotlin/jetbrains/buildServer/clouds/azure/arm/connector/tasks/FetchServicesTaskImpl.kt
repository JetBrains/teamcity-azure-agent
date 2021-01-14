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
import com.microsoft.azure.management.resources.fluentcore.arm.Region
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

data class FetchServicesTaskServiceDescriptor(val namespace: String, val resourceTypes: List<String>)

class FetchServicesTaskImpl : AzureThrottlerCacheableTaskBaseImpl<String, List<FetchServicesTaskServiceDescriptor>>() {
    override fun createQuery(api: Azure, parameter: String): Single<List<FetchServicesTaskServiceDescriptor>> {
        return api
                .providers()
                .listAsync()
                .filter { it.registrationState() == REGISTERED }
                .withLatestFrom(
                        api
                                .subscriptions()
                                .getByIdAsync(api.subscriptionId())
                                .map { it.getLocationByRegion(Region.fromName(parameter)).displayName() }
                ) {
                    provider, locationName ->
                        val resourceTypes =
                            provider
                                    .resourceTypes()
                                    .filter { it.locations().contains(locationName) }
                                    .map { it.resourceType() }
                    FetchServicesTaskServiceDescriptor(
                            provider.namespace(),
                            resourceTypes
                    )
                }
                .filter { it.resourceTypes.isNotEmpty() }
                .toList()
                .last()
                .toSingle()
    }

    companion object {
        private const val REGISTERED = "Registered"
    }
}
