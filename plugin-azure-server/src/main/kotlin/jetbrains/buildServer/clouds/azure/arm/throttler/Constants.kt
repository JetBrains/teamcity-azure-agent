package jetbrains.buildServer.clouds.azure.arm.throttler

const val TEAMCITY_CLOUDS_AZURE_DEPLOYMENT_LONG_RUNNING_QUERY_RETRY_TIMEOUT = "teamcity.clouds.azure.deployment.lrq.timeout"
const val TEAMCITY_CLOUDS_AZURE_DEPLOYMENT_LONG_RUNNING_QUERY_RETRY_COUNT = "teamcity.clouds.azure.deployment.lrq.retry.count"
const val TEAMCITY_CLOUDS_AZURE_RESOURCEGRAPH_LONG_RUNNING_QUERY_RETRY_TIMEOUT = "teamcity.clouds.azure.resourcegraph.lrq.retry.timeout"

const val TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_RANDOM_TASK_CACHE_TIMEOUT = "teamcity.clouds.azure.read.throttler.random.task.cache.timeout"
const val TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_PERIODICAL_TASK_CACHE_TIMEOUT = "teamcity.clouds.azure.read.throttler.periodical.task.cache.timeout"

const val TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_RANDOM_TASK_RESERVATION = "teamcity.clouds.azure.read.throttler.random.task.reservation"
const val TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_TASK_RESERVATION = "teamcity.clouds.azure.read.throttler.task.reservation"
const val TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_AGGRESSIVE_THROTTLING_LIMIT = "teamcity.clouds.azure.read.throttler.aggressive.throttler.limit"
const val TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_DEFAULT_DELAY_IN_MS = "teamcity.clouds.azure.read.throttler.default.delay"
const val TEAMCITY_CLOUDS_AZURE_READ_THROTTLER_MAX_DELAY_IN_MS = "teamcity.clouds.azure.read.throttler.max.delay"

const val TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_RANDOM_TASK_CACHE_TIMEOUT = "teamcity.clouds.azure.action.throttler.random.task.cache.timeout"

const val TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_RANDOM_TASK_RESERVATION = "teamcity.clouds.azure.action.throttler.random.task.reservation"
const val TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_TASK_RESERVATION = "teamcity.clouds.azure.action.throttler.task.reservation"
const val TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_AGGRESSIVE_THROTTLING_LIMIT = "teamcity.clouds.azure.action.throttler.aggressive.throttler.limit"
const val TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_DEFAULT_DELAY_IN_MS = "teamcity.clouds.azure.action.throttler.default.delay"
const val TEAMCITY_CLOUDS_AZURE_ACTION_THROTTLER_MAX_DELAY_IN_MS = "teamcity.clouds.azure.action.throttler.max.delay"

const val TEAMCITY_CLOUDS_AZURE_THROTTLER_QUEUE_PERIOD = "teamcity.clouds.azure.throttler.queue.period"

const val TEAMCITY_CLOUDS_AZURE_THROTTLER_QUEUE_MAX_RETRY_COUNT = "teamcity.clouds.azure.throttler.queue.max.retry.count"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_QUEUE_MAX_TASK_LIVE_IN_SEC = "teamcity.clouds.azure.throttler.queue.max.task.live"
const val TEAMCITY_CLOUDS_AZURE_TASKS_THROTTLER_TIMEOUT_SEC = "teamcity.clouds.azure.tasks.fetchinstances.throttler.timeout"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_TASK_TIMEOUT_SEC = "teamcity.clouds.azure.throttler.task.timeout"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_TASK_CACHE_TIMEOUT_SEC = "teamcity.clouds.azure.throttler.task.cache.timeout"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_TASK_THROTTLE_TIMEOUT_SEC = "teamcity.clouds.azure.throttler.task.throttle.timeout"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_PRINT_DIAGNOSTIC_INTERVAL_SEC = "teamcity.clouds.azure.throttler.print.diagnostic.interval"

const val TEAMCITY_CLOUDS_AZURE_TASKS_FETCHINSTANCES_FULLSTATEAPI_DISABLE = "teamcity.clouds.azure.tasks.fetchinstances.fullstateapi.disable"
const val TEAMCITY_CLOUDS_AZURE_TASKS_FETCHINSTANCES_RESOURCEGRAPH_DISABLE = "teamcity.clouds.azure.tasks.fetchinstanbces.resourcegraph.disable"
const val TEAMCITY_CLOUDS_AZURE_TASKS_FETCHCUSTOMIMAGES_RESOURCEGRAPH_DISABLE = "teamcity.clouds.azure.tasks.fetchcustomimages.resourcegraph.disable"
const val TEAMCITY_CLOUDS_AZURE_TASKS_FETAHCSTORAGEACCOUNTS_RESOURCEGRAPH_DISABLE = "teamcity.clouds.azure.tasks.fetchstorageaccounts.resourcegraph.disable"
const val TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_CONTAINER_NIC_RETRY_DELAY_SEC = "teamcity.clouds.azure.tasks.deletedeployment.container.nic.retry.delay"
const val TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_KNOWN_RESOURCE_TYPES = "teamcity.clouds.azure.tasks.deletedeployment.knownGeneric.resourceTypes"
const val TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_KNOWN_RESOURCE_NAMES = "teamcity.clouds.azure.tasks.deletedeployment.knownGeneric.resourceNames"
const val TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_USE_MILTITHREAD_POLLING = "teamcity.clouds.azure.tasks.deletedeployment.useMultitreadPolling"
const val TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_USE_PROVIDER_CACHE = "teamcity.clouds.azure.tasks.deletedeployment.useProviderCache"
const val TEAMCITY_CLOUDS_AZURE_TASKS_DELETEDEPLOYMENT_DELETE_VM_FIRST_ON_ANY_FAIL = "teamcity.clouds.azure.tasks.deletedeployment.deleteVmFirstOnAnyFail"
const val TEAMCITY_CLOUDS_AZURE_TASKS_CTREATEDEPLOYMENT_USE_MILTITHREAD_POLLING = "teamcity.clouds.azure.tasks.createdeployment.useMultitreadPolling"

const val TEAMCITY_CLOUDS_AZURE_THROTTLER_USE_OLD_SCHEDULERS = "teamcity.clouds.azure.throttler.use.old.schedulers"

const val TEAMCITY_CLOUDS_AZURE_THROTTLER_COMPUTATION_POOL_MAX_SIZE = "teamcity.clouds.azure.throttler.computation.pool.max.size"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_COMPUTATION_POOL_MIN_SIZE = "teamcity.clouds.azure.throttler.computation.pool.min.size"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_COMPUTATION_POOL_QUEUE_MAX_SIZE = "teamcity.clouds.azure.throttler.computation.pool.queue.max.size"

const val TEAMCITY_CLOUDS_AZURE_THROTTLER_IO_POOL_MAX_SIZE = "teamcity.clouds.azure.throttler.io.pool.max.size"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_IO_POOL_MIN_SIZE = "teamcity.clouds.azure.throttler.io.pool.min.size"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_IO_POOL_QUEUE_MAX_SIZE = "teamcity.clouds.azure.throttler.io.pool.queue.max.size"

const val TEAMCITY_CLOUDS_AZURE_THROTTLER_NEW_THREAD_POOL_MAX_SIZE = "teamcity.clouds.azure.throttler.new.thread.pool.max.size"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_NEW_THREAD_POOL_MIN_SIZE = "teamcity.clouds.azure.throttler.new.thread.pool.min.size"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_NEW_THREAD_POOL_QUEUE_MAX_SIZE = "teamcity.clouds.azure.throttler.new.thread.pool.queue.max.size"

const val TEAMCITY_CLOUDS_AZURE_THROTTLER_DISPATCHER_POOL_MAX_SIZE = "teamcity.clouds.azure.throttler.dispatcher.pool.max.size"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_DISPATCHER_POOL_MIN_SIZE = "teamcity.clouds.azure.throttler.dispatcher.pool.min.size"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_DISPATCHER_POOL_QUEUE_MAX_SIZE = "teamcity.clouds.azure.throttler.dispatcher.pool.queue.max.size"

const val TEAMCITY_CLOUDS_AZURE_THROTTLER_SCHEDULER_POOL_MAX_SIZE = "teamcity.clouds.azure.throttler.scheduler.pool.max.size"

const val TEAMCITY_CLOUDS_AZURE_THROTTLER_GLOBAL_SYNC_DISABLE = "teamcity.clouds.azure.throttler.global.sync.disable"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_NEW_THROTTLING_MODEL_DISABLE = "teamcity.clouds.azure.throttler.new.model.disable"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_BUCKET_SIZE = "teamcity.clouds.azure.throttler.timeManager.bucket.size"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_BUCKET_REFILL_INTERVAL_MSEC = "teamcity.clouds.azure.throttler.timeManager.bucket.refillInterval"
const val TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_BUCKET_REFILL_VALUE = "teamcity.clouds.azure.throttler.timeManager.bucket.refillValue"

const val TEAMCITY_CLOUDS_AZURE_DEPLOYMENT_TEMPLATE_POST_UPDATE_DISABLE = "teamcity.clouds.azure.deployment.template.post.update.disable"

const val DEFAULT_REMAINING_READS_PER_HOUR = 12000L
