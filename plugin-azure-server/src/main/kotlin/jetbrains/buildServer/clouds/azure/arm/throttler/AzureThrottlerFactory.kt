/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerActionTasks
import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureThrottlerReadTasks
import jetbrains.buildServer.serverSide.TeamCityProperties
import rx.schedulers.Schedulers

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
        val adapterThrottlerTimeInMs = TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_DEFAULT_DELAY_IN_MS, 400)

        val readsStrategy = AzureThrottlerStrategyImpl<Azure, AzureThrottlerReadTasks.Values>(
                azureAdapter,
                randomTaskReservation,
                taskReservation,
                aggressiveThrottlingLimit,
                adapterThrottlerTimeInMs)

        val throttler = AzureThrottlerImpl(azureAdapter, readsStrategy, Schedulers.immediate())

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
        val adapterThrottlerTimeInMs = TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_DEFAULT_DELAY_IN_MS, 100)

        val actionsStrategy = AzureThrottlerStrategyImpl<Azure, AzureThrottlerActionTasks.Values>(
                azureActionAdapter,
                randomTaskReservation,
                taskReservation,
                aggressiveThrottlingLimit,
                adapterThrottlerTimeInMs)

        val randomTaskCacheTimeout = TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_RANDOM_TASK_CACHE_TIMEOUT, 60)

        val throttler =  AzureThrottlerImpl(azureActionAdapter, actionsStrategy, Schedulers.io())
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
