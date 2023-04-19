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

import com.microsoft.azure.AzureEnvironment
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureApi
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerActionTasks
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerReadTasks
import rx.Single
import java.util.concurrent.TimeUnit

interface AzureRequestThrottler {
    val subscriptionId : String?

    fun <P, T> executeReadTask(taskDescriptor: AzureTaskDescriptor<AzureApi, AzureThrottlerReadTasks.Values, P, T>, parameters: P) : Single<T>;
    fun <P, T> executeReadTaskWithTimeout(taskDescriptor: AzureTaskDescriptor<AzureApi, AzureThrottlerReadTasks.Values, P, T>, parameters: P) : Single<T>;
    fun <P, T> executeReadTaskWithTimeout(taskDescriptor: AzureTaskDescriptor<AzureApi, AzureThrottlerReadTasks.Values, P, T>, parameters: P, timeout: Long, timeUnit: TimeUnit) : Single<T>;
    fun <P, T> executeUpdateTask(taskDescriptor: AzureTaskDescriptor<AzureApi, AzureThrottlerActionTasks.Values, P, T>, parameters: P) : Single<T>;

    fun start()
}

interface AzureRequestThrottlerCache {
    fun getOrCreateThrottler(params: Map<String, String>) : AzureRequestThrottler
    fun getOrCreateByEnv(env: AzureEnvironment, subscriptionId: String?) : AzureRequestThrottler
    fun getOrCreateByCredentials(clientId: String?, tenantId: String?, clientSecret: String?, env: AzureEnvironment, subscriptionId: String?) : AzureRequestThrottler
}

