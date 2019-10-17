package jetbrains.buildServer.clouds.azure.arm.throttler

import com.microsoft.azure.credentials.AzureTokenCredentials
import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerActionTasks
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerReadTasks
import jetbrains.buildServer.serverSide.TeamCityProperties

object AzureThrottlerFactory {
    fun createReadRequestsThrottler(credentials: AzureTokenCredentials, subscriptionId: String?): AzureThrottler<Azure, AzureThrottlerReadTasks.Values> {
        val azureAdapter = AzureThrottlerAdapterImpl(
                AzureThrottlerConfigurableImpl(),
                credentials,
                subscriptionId,
                "ReadAdapter")

        val randomTaskReservation = TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_RANDOM_TASK_RESERVATION, 50)
        val taskReservation = TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_TASK_RESERVATION, 10)
        val aggressiveThrottlingLimit = TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_AGGRESSIVE_THROTTLING_LIMIT, 90)

        val readsStrategy = AzureThrottlerStrategyImpl<Azure, AzureThrottlerReadTasks.Values>(
                azureAdapter,
                randomTaskReservation,
                taskReservation,
                aggressiveThrottlingLimit)

        val throttler = AzureThrottlerImpl(azureAdapter, readsStrategy)

        val randomTaskCacheTimeout = TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_RANDOM_TASK_CACHE_TIMEOUT, 60)
        val periodicalTaskCacheTimeout = TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_PERIODICAL_TASK_CACHE_TIMEOUT, 120)

        return throttler
                .registerTask(AzureThrottlerReadTasks.FetchResourceGroups,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
                .registerTask(AzureThrottlerReadTasks.FetchVirtualMachines,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
                .registerTask(AzureThrottlerReadTasks.FetchInstances,
                        AzureThrottlerTaskTimeExecutionType.Periodical,
                        periodicalTaskCacheTimeout)
                .registerTask(AzureThrottlerReadTasks.FetchCustomImages,
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
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
                        AzureThrottlerTaskTimeExecutionType.Random,
                        randomTaskCacheTimeout)
    }

    fun createActionRequestsThrottler(credentials: AzureTokenCredentials, subscriptionId: String?): AzureThrottler<Azure, AzureThrottlerActionTasks.Values> {
        val azureActionAdapter = AzureThrottlerAdapterImpl(
                AzureThrottlerConfigurableImpl(),
                credentials,
                subscriptionId,
                "ActionAdapter")

        val randomTaskReservation = TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_RANDOM_TASK_RESERVATION, 50)
        val taskReservation = TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_TASK_RESERVATION, 10)
        val aggressiveThrottlingLimit = TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_AGGRESSIVE_THROTTLING_LIMIT, 90)

        val actionsStrategy = AzureThrottlerStrategyImpl<Azure, AzureThrottlerActionTasks.Values>(
                azureActionAdapter,
                randomTaskReservation,
                taskReservation,
                aggressiveThrottlingLimit)

        val randomTaskCacheTimeout = TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_RANDOM_TASK_CACHE_TIMEOUT, 60)

        val throttler =  AzureThrottlerImpl(azureActionAdapter, actionsStrategy)
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
