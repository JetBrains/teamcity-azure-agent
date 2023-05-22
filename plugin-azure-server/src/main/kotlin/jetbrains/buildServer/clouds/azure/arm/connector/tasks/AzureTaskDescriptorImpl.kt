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

import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskDescriptor
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskNotifications
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTask

class AzureTaskDescriptorImpl<I, P, T>(
        override val taskId: I,
        private val factory: (AzureTaskNotifications) -> AzureThrottlerTask<AzureApi, P, T>
) : AzureTaskDescriptor<AzureApi, I, P, T> {
    override fun create(taskNotifications: AzureTaskNotifications): AzureThrottlerTask<AzureApi, P, T> {
        return factory(taskNotifications)
    }
}
