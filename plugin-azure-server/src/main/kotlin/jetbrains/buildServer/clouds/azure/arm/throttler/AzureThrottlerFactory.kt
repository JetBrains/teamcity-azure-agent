

package jetbrains.buildServer.clouds.azure.arm.throttler

import com.microsoft.azure.credentials.AzureTokenCredentials
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureApi
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerActionTasks
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerReadTasks
import kotlinx.coroutines.CoroutineDispatcher
import rx.Scheduler
import java.io.Closeable

interface AzureThrottlerFactory {
    fun createReadRequestsThrottler(credentials: AzureTokenCredentials, subscriptionId: String?, taskNotifications: AzureTaskNotifications, requestSync: AzureThrottlerRequestSync): AzureThrottler<AzureApi, AzureThrottlerReadTasks.Values>
    fun createActionRequestsThrottler(credentials: AzureTokenCredentials, subscriptionId: String?, taskNotifications: AzureTaskNotifications, requestSync: AzureThrottlerRequestSync): AzureThrottler<AzureApi, AzureThrottlerActionTasks.Values>
}

interface AzureThrottlerSchedulersProvider : Closeable {
    fun getReadRequestsSchedulers(): AzureThrottlerSchedulers
    fun getActionRequestsSchedulers(): AzureThrottlerSchedulers
    fun getDispatcher(): CoroutineDispatcher
}

data class AzureThrottlerSchedulers(val requestScheduler: Scheduler, val timeoutScheduler: Scheduler)
