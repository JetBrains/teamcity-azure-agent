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
