package jetbrains.buildServer.clouds.azure.arm.throttler

import com.microsoft.azure.AzureEnvironment
import com.microsoft.azure.credentials.ApplicationTokenCredentials
import com.microsoft.azure.credentials.AzureTokenCredentials
import com.microsoft.azure.credentials.MSICredentials
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureApi
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerActionTasks
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerReadTasks
import rx.Single
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit

class AzureRequestThrottlerCacheImpl(
        private val mySchedulersProvider: AzureThrottlerSchedulersProvider,
        private val myAzureThrottlerFactory: AzureThrottlerFactory,
        private val myAzureTimeManagerFactory: AzureTimeManagerFactory,
) : AzureRequestThrottlerCache {
    private val envMap: ConcurrentMap<EnvKey, AzureRequestThrottler> = ConcurrentHashMap<EnvKey, AzureRequestThrottler>();
    private val appMap: ConcurrentMap<AppKey, AzureRequestThrottler> = ConcurrentHashMap<AppKey, AzureRequestThrottler>();
    private val myReadTimeManager = myAzureTimeManagerFactory.create()
    private val myActionTimeManager = myAzureTimeManagerFactory.create()

    override fun getOrCreateThrottler(params: Map<String, String>) : AzureRequestThrottler {
        val subscriptionId = params[AzureConstants.SUBSCRIPTION_ID]
        val env = getEnvironment(params)

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

    override fun getOrCreateByEnv(env: AzureEnvironment, subscriptionId: String?) : AzureRequestThrottler {
        return envMap.getOrPut(EnvKey(env, subscriptionId)) {
            AzureRequestThrottlerImpl(subscriptionId, MSICredentials(env), myAzureThrottlerFactory, myReadTimeManager, myActionTimeManager)
        }
    }

    override fun getOrCreateByCredentials(clientId: String?, tenantId: String?, clientSecret: String?, env: AzureEnvironment, subscriptionId: String?) : AzureRequestThrottler {
        return appMap.getOrPut(AppKey(clientId, tenantId, clientSecret, subscriptionId)) {
            AzureRequestThrottlerImpl(subscriptionId, ApplicationTokenCredentials(clientId, tenantId, clientSecret, env), myAzureThrottlerFactory, myReadTimeManager, myActionTimeManager)
        }
    }

    private fun getEnvironment(params: Map<String, String>): AzureEnvironment {
        val environment = params[AzureConstants.ENVIRONMENT]
        val env = when (environment) {
            "AZURE_CHINA" -> AzureEnvironment.AZURE_CHINA
            "AZURE_GERMANY" -> AzureEnvironment.AZURE_GERMANY
            "AZURE_US_GOVERNMENT" -> AzureEnvironment.AZURE_US_GOVERNMENT
            else -> AzureEnvironment.AZURE
        }
        return env
    }

    data class EnvKey(val env: AzureEnvironment, val subscriptionId: String?);
    data class AppKey(val clientId: String?, val tenantId: String?, val clientSecret: String?, val subscriptionId: String?);

    class AzureRequestThrottlerImpl(
        override val subscriptionId: String?,
        private val credentials: AzureTokenCredentials,
        private val myAzureThrottlerFactory: AzureThrottlerFactory,
        private val myReadTimeManager: AzureTimeManager,
        private val myActionTimeManager: AzureTimeManager,
    ) : AzureRequestThrottler {
        private var myReadsThrottler: AzureThrottler<AzureApi, AzureThrottlerReadTasks.Values>
        private var myUpdatesThrottler: AzureThrottler<AzureApi, AzureThrottlerActionTasks.Values>
        private var myTaskNotifications = AzureTaskNotificationsImpl()

        init {
            myReadsThrottler = myAzureThrottlerFactory.createReadRequestsThrottler(credentials, subscriptionId, myTaskNotifications, myReadTimeManager)
            myUpdatesThrottler = myAzureThrottlerFactory.createActionRequestsThrottler(credentials, subscriptionId, myTaskNotifications, myReadTimeManager)
        }

        override fun <P, T> executeReadTask(taskDescriptor: AzureTaskDescriptor<AzureApi, AzureThrottlerReadTasks.Values, P, T>, parameters: P): Single<T> {
            return myReadsThrottler.executeTask(taskDescriptor, parameters);
        }

        override fun <P, T> executeReadTaskWithTimeout(taskDescriptor: AzureTaskDescriptor<AzureApi, AzureThrottlerReadTasks.Values, P, T>, parameters: P, timeout: Long, timeUnit: TimeUnit): Single<T> {
            return myReadsThrottler.executeTaskWithTimeout(taskDescriptor, parameters, timeout, timeUnit)
        }

        override fun <P, T> executeReadTaskWithTimeout(taskDescriptor: AzureTaskDescriptor<AzureApi, AzureThrottlerReadTasks.Values, P, T>, parameters: P): Single<T> {
            return myReadsThrottler.executeTaskWithTimeout(taskDescriptor, parameters);
        }

        override fun <P, T> executeUpdateTask(taskDescriptor: AzureTaskDescriptor<AzureApi, AzureThrottlerActionTasks.Values, P, T>, parameters: P): Single<T> {
            return myUpdatesThrottler.executeTask(taskDescriptor, parameters);
        }

        override fun start() {
            myReadsThrottler.start()
            myUpdatesThrottler.start()
        }
    }
}
