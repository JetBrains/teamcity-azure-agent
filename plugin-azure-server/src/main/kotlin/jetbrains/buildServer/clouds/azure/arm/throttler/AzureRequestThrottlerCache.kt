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
import com.microsoft.azure.credentials.ApplicationTokenCredentials
import com.microsoft.azure.credentials.AzureTokenCredentials
import com.microsoft.azure.credentials.MSICredentials
import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerActionTasks
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerReadTasks
import rx.Single
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

interface AzureRequestThrottler {
    val subscriptionId : String?

    fun <P, T> executeReadTask(taskDescriptor: AzureTaskDescriptor<Azure, AzureThrottlerReadTasks.Values, P, T>, parameters: P) : Single<T>;
    fun <P, T> executeReadTaskWithTimeout(taskDescriptor: AzureTaskDescriptor<Azure, AzureThrottlerReadTasks.Values, P, T>, parameters: P) : Single<T>;
    fun <P, T> executeUpdateTask(taskDescriptor: AzureTaskDescriptor<Azure, AzureThrottlerActionTasks.Values, P, T>, parameters: P) : Single<T>;

    fun start()
}

object AzureRequestThrottlerCache {
    val envMap: ConcurrentMap<EnvKey, AzureRequestThrottler> = ConcurrentHashMap<EnvKey, AzureRequestThrottler>();
    val appMap: ConcurrentMap<AppKey, AzureRequestThrottler> = ConcurrentHashMap<AppKey, AzureRequestThrottler>();

    fun getOrCreateThrottler(params: Map<String, String>) : AzureRequestThrottler {
        var subscriptionId: String? = null
        params[AzureConstants.SUBSCRIPTION_ID]?.let {
            subscriptionId = it
        }

        val environment = params[AzureConstants.ENVIRONMENT]
        val env = when (environment) {
            "AZURE_CHINA" -> AzureEnvironment.AZURE_CHINA
            "AZURE_GERMANY" -> AzureEnvironment.AZURE_GERMANY
            "AZURE_US_GOVERNMENT" -> AzureEnvironment.AZURE_US_GOVERNMENT
            else -> AzureEnvironment.AZURE
        }

        val credentialsType = params[AzureConstants.CREDENTIALS_TYPE]
        if (credentialsType == AzureConstants.CREDENTIALS_MSI) {
            return getOrCreateByEnv(env, subscriptionId)
        } else {
            val tenantId = params[AzureConstants.TENANT_ID]
            val clientId = params[AzureConstants.CLIENT_ID]
            val clientSecret = params[AzureConstants.CLIENT_SECRET]
            return getOrCreateByCredentials(clientId, tenantId, clientSecret, env, subscriptionId)
        }
    }

    private fun getOrCreateByEnv(env: AzureEnvironment, subscriptionId: String?) : AzureRequestThrottler {
        return envMap.getOrPut(EnvKey(env, subscriptionId)) {
            AzureRequestThrottlerImpl(subscriptionId, MSICredentials(env))
        }
    }

    private fun getOrCreateByCredentials(clientId: String?, tenantId: String?, clientSecret: String?, env: AzureEnvironment, subscriptionId: String?) : AzureRequestThrottler {
        return appMap.getOrPut(AppKey(clientId, tenantId, clientSecret, subscriptionId)) {
            AzureRequestThrottlerImpl(subscriptionId, ApplicationTokenCredentials(clientId, tenantId, clientSecret, env))
        }
    }

    data class EnvKey(val env: AzureEnvironment, val subscriptionId: String?);
    data class AppKey(val clientId: String?, val tenantId: String?, val clientSecret: String?, val subscriptionId: String?);

    class AzureRequestThrottlerImpl(
            override val subscriptionId: String?,
            private val credentials: AzureTokenCredentials
    ) : AzureRequestThrottler {
        private var myReadsThrottler: AzureThrottler<Azure, AzureThrottlerReadTasks.Values>
        private var myUpdatesThrottler: AzureThrottler<Azure, AzureThrottlerActionTasks.Values>
        private var myTaskNotifications = AzureTaskNotificationsImpl()

        init {
            myReadsThrottler = AzureThrottlerFactory.createReadRequestsThrottler(credentials, subscriptionId, myTaskNotifications)
            myUpdatesThrottler = AzureThrottlerFactory.createActionRequestsThrottler(credentials, subscriptionId, myTaskNotifications)
        }

        override fun <P, T> executeReadTask(taskDescriptor: AzureTaskDescriptor<Azure, AzureThrottlerReadTasks.Values, P, T>, parameters: P): Single<T> {
            return myReadsThrottler.executeTask(taskDescriptor, parameters);
        }

        override fun <P, T> executeReadTaskWithTimeout(taskDescriptor: AzureTaskDescriptor<Azure, AzureThrottlerReadTasks.Values, P, T>, parameters: P): Single<T> {
            return myReadsThrottler.executeTaskWithTimeout(taskDescriptor, parameters);
        }

        override fun <P, T> executeUpdateTask(taskDescriptor: AzureTaskDescriptor<Azure, AzureThrottlerActionTasks.Values, P, T>, parameters: P): Single<T> {
            return myUpdatesThrottler.executeTask(taskDescriptor, parameters);
        }

        override fun start() {
            myReadsThrottler.start()
            myUpdatesThrottler.start()
        }
    }
}
