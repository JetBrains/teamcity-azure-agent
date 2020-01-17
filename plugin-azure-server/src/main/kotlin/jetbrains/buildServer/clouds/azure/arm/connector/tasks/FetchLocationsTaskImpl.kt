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
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

data class FetchLocationsTaskLocationDescriptor(val name: String, val displayName: String)

class FetchLocationsTaskImpl : AzureThrottlerCacheableTaskBaseImpl<Unit, List<FetchLocationsTaskLocationDescriptor>>() {
    override fun create(api: Azure, parameter: Unit): Single<List<FetchLocationsTaskLocationDescriptor>> {
        return api
                .subscriptions()
                .getByIdAsync(api.subscriptionId())
                .last()
                .map { subscription -> subscription.listLocations().map { FetchLocationsTaskLocationDescriptor(it.name(), it.displayName()) } }
                .toSingle()
    }
}
