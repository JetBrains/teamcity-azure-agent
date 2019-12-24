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
    fun <P, T> executeUpdateTask(taskDescriptor: AzureTaskDescriptor<Azure, AzureThrottlerActionTasks.Values, P, T>, parameters: P) : Single<T>;
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
            return getOrCreateByAppToken(ApplicationTokenCredentials(clientId, tenantId, clientSecret, env), subscriptionId)
        }
    }

    private fun getOrCreateByEnv(env: AzureEnvironment, subscriptionId: String?) : AzureRequestThrottler {
        return envMap.getOrPut(EnvKey(env, subscriptionId)) {
            AzureRequestThrottlerImpl(subscriptionId, MSICredentials(env))
        }
    }

    private fun getOrCreateByAppToken(credentials: ApplicationTokenCredentials, subscriptionId: String?) : AzureRequestThrottler {
        return appMap.getOrPut(AppKey(credentials.domain(), credentials.clientId(), subscriptionId)) {
            AzureRequestThrottlerImpl(subscriptionId, credentials)
        }
    }

    data class EnvKey(val env: AzureEnvironment, val subscriptionId: String?);
    data class AppKey(val tenantId: String?, val clientId: String?, val subscriptionId: String?);

    class AzureRequestThrottlerImpl(
            override val subscriptionId: String?,
            private val credentials: AzureTokenCredentials
    ) : AzureRequestThrottler {
        private var myReadsThrottler: AzureThrottler<Azure, AzureThrottlerReadTasks.Values>
        private var myUpdatesThrottler: AzureThrottler<Azure, AzureThrottlerActionTasks.Values>

        init {
            myReadsThrottler = AzureThrottlerFactory.createReadRequestsThrottler(credentials, subscriptionId)
            myUpdatesThrottler = AzureThrottlerFactory.createActionRequestsThrottler(credentials, subscriptionId)
        }

        override fun <P, T> executeReadTask(taskDescriptor: AzureTaskDescriptor<Azure, AzureThrottlerReadTasks.Values, P, T>, parameters: P): Single<T> {
            return myReadsThrottler.executeTask(taskDescriptor, parameters);
        }

        override fun <P, T> executeUpdateTask(taskDescriptor: AzureTaskDescriptor<Azure, AzureThrottlerActionTasks.Values, P, T>, parameters: P): Single<T> {
            return myUpdatesThrottler.executeTask(taskDescriptor, parameters);
        }
    }
}
