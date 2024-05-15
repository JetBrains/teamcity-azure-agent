

package jetbrains.buildServer.clouds.azure.arm.throttler

import com.microsoft.azure.credentials.AzureTokenCredentials
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureApi
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerActionTasks
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerReadTasks
import jetbrains.buildServer.serverSide.TeamCityProperties
import java.util.concurrent.atomic.AtomicLong

class AzureThrottlerFactoryImpl(
    private val mySchedulersProvider: AzureThrottlerSchedulersProvider
) : AzureThrottlerFactory {
    private val throttlerId = AtomicLong(0)

    override fun createReadRequestsThrottler(
        credentials: AzureTokenCredentials,
        subscriptionId: String?,
        taskNotifications: AzureTaskNotifications,
        timeManager: AzureTimeManager,
    ): AzureThrottler<AzureApi, AzureThrottlerReadTasks.Values> {
        val azureAdapter = AzureThrottlerAdapterImpl(
                AzureThrottlerConfigurableImpl(),
                ReqourceGraphConfigurableImpl(),
                credentials,
                subscriptionId,
                timeManager,
                mySchedulersProvider.getReadRequestsSchedulers().requestScheduler,
                "${throttlerId.incrementAndGet()}-ReadAdapter")

        val randomTaskReservation = { TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_RANDOM_TASK_RESERVATION, 20) }
        val taskReservation = { TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_TASK_RESERVATION, 10) }
        val aggressiveThrottlingLimit = { TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_AGGRESSIVE_THROTTLING_LIMIT, 90) }
        val adapterThrottlerTimeInMs = {
            if (TeamCityProperties.getBoolean(TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_NEW_THROTTLING_MODEL_DISABLE))
                TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_DEFAULT_DELAY_IN_MS, 400)
            else
                0
        }
        val maxAdapterThrottlerTimeInMs = { TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_MAX_DELAY_IN_MS, 3000) }

        val readsStrategy = AzureThrottlerStrategyImpl<AzureApi, AzureThrottlerReadTasks.Values>(
                azureAdapter,
                randomTaskReservation,
                taskReservation,
                aggressiveThrottlingLimit,
                adapterThrottlerTimeInMs,
                maxAdapterThrottlerTimeInMs)

        val throttler = AzureThrottlerImpl(
                azureAdapter,
                readsStrategy,
                mySchedulersProvider.getReadRequestsSchedulers(),
                AzureThrottlerScheduledExecutorFactortyImpl(),
                taskNotifications
        )

        val randomTaskCacheTimeout = TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_RANDOM_TASK_CACHE_TIMEOUT, 90)
        val periodicalTaskCacheTimeout = TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_PERIODICAL_TASK_CACHE_TIMEOUT, 150)

        return throttler
                .registerTask(AzureThrottlerReadTasks.FetchResourceGroups,
                        AzureThrottlerTaskTimeExecutionType.Periodical,
                        periodicalTaskCacheTimeout)
                .registerTask(AzureThrottlerReadTasks.FetchVirtualMachines,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
                .registerTask(AzureThrottlerReadTasks.FetchInstances,
                        AzureThrottlerTaskTimeExecutionType.Periodical,
                        periodicalTaskCacheTimeout)
                .registerTask(AzureThrottlerReadTasks.FetchCustomImages,
                        AzureThrottlerTaskTimeExecutionType.Periodical,
                        periodicalTaskCacheTimeout)
                .registerTask(AzureThrottlerReadTasks.FetchStorageAccounts,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
                .registerTask(AzureThrottlerReadTasks.FetchVirtualMachineSizes,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
                .registerTask(AzureThrottlerReadTasks.FetchSubscriptions,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
                .registerTask(AzureThrottlerReadTasks.FetchLocations,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
                .registerTask(AzureThrottlerReadTasks.FetchNetworks,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
                .registerTask(AzureThrottlerReadTasks.FetchServices,
                        AzureThrottlerTaskTimeExecutionType.Periodical,
                        periodicalTaskCacheTimeout)
                .registerTask(AzureThrottlerReadTasks.FetchStorageAccountKeys,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
    }

    override fun createActionRequestsThrottler(
        credentials: AzureTokenCredentials,
        subscriptionId: String?,
        taskNotifications: AzureTaskNotifications,
        timeManager: AzureTimeManager,
    ): AzureThrottler<AzureApi, AzureThrottlerActionTasks.Values> {
        val azureActionAdapter = AzureThrottlerAdapterImpl(
                AzureThrottlerConfigurableImpl(),
                ReqourceGraphConfigurableImpl(),
                credentials,
                subscriptionId,
                timeManager,
                mySchedulersProvider.getActionRequestsSchedulers().requestScheduler,
                "${throttlerId.incrementAndGet()}-ActionAdapter")

        val randomTaskReservation = { TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_RANDOM_TASK_RESERVATION, 50) }
        val taskReservation = { TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_TASK_RESERVATION, 10) }
        val aggressiveThrottlingLimit = { TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_AGGRESSIVE_THROTTLING_LIMIT, 90) }
        val adapterThrottlerTimeInMs = {
            if (TeamCityProperties.getBoolean(TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_NEW_THROTTLING_MODEL_DISABLE))
                TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_DEFAULT_DELAY_IN_MS, 100)
            else
                0
        }
        val maxAdapterThrottlerTimeInMs = { TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_MAX_DELAY_IN_MS, 1000) }

        val actionsStrategy = AzureThrottlerStrategyImpl<AzureApi, AzureThrottlerActionTasks.Values>(
                azureActionAdapter,
                randomTaskReservation,
                taskReservation,
                aggressiveThrottlingLimit,
                adapterThrottlerTimeInMs,
                maxAdapterThrottlerTimeInMs)

        val randomTaskCacheTimeout = TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_RANDOM_TASK_CACHE_TIMEOUT, 60)

        val throttler = AzureThrottlerImpl(
                azureActionAdapter,
                actionsStrategy,
                mySchedulersProvider.getActionRequestsSchedulers(),
                AzureThrottlerScheduledExecutorFactortyImpl(),
                taskNotifications
        )

        return throttler
                .registerTask(AzureThrottlerActionTasks.CreateDeployment,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
                .registerTask(AzureThrottlerActionTasks.CreateResourceGroup,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
                .registerTask(AzureThrottlerActionTasks.DeleteResourceGroup,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
                .registerTask(AzureThrottlerActionTasks.StopVirtualMachine,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
                .registerTask(AzureThrottlerActionTasks.StartVirtualMachine,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
                .registerTask(AzureThrottlerActionTasks.RestartVirtualMachine,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
                .registerTask(AzureThrottlerActionTasks.DeleteDeployment,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
    }
}
