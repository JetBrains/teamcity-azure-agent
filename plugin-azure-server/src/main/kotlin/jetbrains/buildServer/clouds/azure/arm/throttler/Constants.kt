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

const val TEAMCITY_CLOUDS_AZURE_DEPLOYMENT_LONG_RUNNING_QUERY_RETRY_TIMEOUT = "teamcity.clouds.azure.deployment.lrq.timeout"

const val TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_RANDOM_TASK_CACHE_TIMEOUT = "teamcity.clouds.azure.read.throttler.random.task.cache.timeout"
const val TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_PERIODICAL_TASK_CACHE_TIMEOUT = "teamcity.clouds.azure.read.throttler.periodical.task.cache.timeout"

const val TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_RANDOM_TASK_RESERVATION = "teamcity.clouds.azure.read.throttler.random.task.reservation"
const val TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_TASK_RESERVATION = "teamcity.clouds.azure.read.throttler.task.reservation"
const val TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_AGGRESSIVE_THROTTLING_LIMIT = "teamcity.clouds.azure.read.throttler.aggressive.throttler.limit"
const val TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_DEFAULT_DELAY_IN_MS = "teamcity.clouds.azure.read.throttler.default.delay"

const val TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_RANDOM_TASK_CACHE_TIMEOUT = "teamcity.clouds.azure.action.throttler.random.task.cache.timeout"

const val TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_RANDOM_TASK_RESERVATION = "teamcity.clouds.azure.action.throttler.random.task.reservation"
const val TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_TASK_RESERVATION = "teamcity.clouds.azure.action.throttler.task.reservation"
const val TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_AGGRESSIVE_THROTTLING_LIMIT = "teamcity.clouds.azure.action.throttler.aggressive.throttler.limit"
const val TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_DEFAULT_DELAY_IN_MS = "teamcity.clouds.azure.action.throttler.default.delay"

const val TEAMCITY_CLOUDS_AZURE_THROTTLER_QUEUE_PERIOD = "teamcity.clouds.azure.throttler.queue.period"

const val TEAMCITY_CLOUDS_AZURE_THROTTLER_QUEUE_MAX_RETRY_COUNT = "teamcity.clouds.azure.throttler.queue.max.retry.count"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_QUEUE_MAX_TASK_LIVE_IN_SEC = "teamcity.clouds.azure.throttler.queue.max.task.live"

const val DEFAULT_REMAINING_READS_PER_HOUR = 12000L
