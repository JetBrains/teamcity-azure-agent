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

package jetbrains.buildServer.clouds.azure.arm.throttler

import com.microsoft.azure.credentials.AzureTokenCredentials
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureApi
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerActionTasks
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerReadTasks
import kotlinx.coroutines.CoroutineDispatcher
import rx.Scheduler
import java.io.Closeable

interface AzureThrottlerFactory {
    fun createReadRequestsThrottler(credentials: AzureTokenCredentials, subscriptionId: String?, taskNotifications: AzureTaskNotifications, timeManager: AzureTimeManager): AzureThrottler<AzureApi, AzureThrottlerReadTasks.Values>
    fun createActionRequestsThrottler(credentials: AzureTokenCredentials, subscriptionId: String?, taskNotifications: AzureTaskNotifications, timeManager: AzureTimeManager): AzureThrottler<AzureApi, AzureThrottlerActionTasks.Values>
}

interface AzureThrottlerSchedulersProvider : Closeable {
    fun getReadRequestsSchedulers(): AzureThrottlerSchedulers
    fun getActionRequestsSchedulers(): AzureThrottlerSchedulers
    fun getDispatcher(): CoroutineDispatcher
}

data class AzureThrottlerSchedulers(val requestScheduler: Scheduler, val timeoutScheduler: Scheduler)
