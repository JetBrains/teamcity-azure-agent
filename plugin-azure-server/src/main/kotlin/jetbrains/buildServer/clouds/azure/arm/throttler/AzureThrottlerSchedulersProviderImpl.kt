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

import com.microsoft.azure.management.resources.fluentcore.utils.SdkContext
import jetbrains.buildServer.serverSide.BuildServerAdapter
import jetbrains.buildServer.serverSide.BuildServerListener
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.util.EventDispatcher
import jetbrains.buildServer.util.ThreadUtil
import jetbrains.buildServer.util.executors.ExecutorsFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import rx.Scheduler
import rx.plugins.RxJavaHooks
import rx.plugins.RxJavaPlugins
import rx.plugins.RxJavaSchedulersHook
import rx.schedulers.Schedulers
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

class AzureThrottlerSchedulersProviderImpl(serverDispatcher: EventDispatcher<BuildServerListener>) : AzureThrottlerSchedulersProvider {
    private var myProvider = if (useOldSchedulers) OldSchedulersImpl() else SchedulersImpl(serverDispatcher)

    override fun getReadRequestsSchedulers(): AzureThrottlerSchedulers = myProvider.getReadRequestsSchedulers()

    override fun getActionRequestsSchedulers(): AzureThrottlerSchedulers = myProvider.getActionRequestsSchedulers()

    override fun getDispatcher(): CoroutineDispatcher = myProvider.getDispatcher()

    override fun close() = myProvider.close()

    private val useOldSchedulers: Boolean
        get() = TeamCityProperties.getBoolean(TEAMCITY_CLOUDS_AZURE_THROTTLER_USE_OLD_SCHEDULERS)

    private class OldSchedulersImpl : AzureThrottlerSchedulersProvider {
        override fun getReadRequestsSchedulers(): AzureThrottlerSchedulers =
            AzureThrottlerSchedulers(Schedulers.immediate(), Schedulers.computation())

        override fun getActionRequestsSchedulers(): AzureThrottlerSchedulers =
            AzureThrottlerSchedulers(Schedulers.io(), Schedulers.computation())

        override fun getDispatcher(): CoroutineDispatcher = Dispatchers.IO

        override fun close() = Unit
    }

    private class SchedulersImpl(serverDispatcher: EventDispatcher<BuildServerListener>) : AzureThrottlerSchedulersProvider {
        private var myComputationScheduler: Scheduler
        private var myIOScheduler: Scheduler
        private var myNewThreadUtilScheduler: Scheduler
        private var myDispatcher: ExecutorCoroutineDispatcher
        private var myNewThreadSchedulerService: CloseableExecutorService<ExecutorService>
        private var myComputationSchedulerService: CloseableExecutorService<ExecutorService>
        private var myIOSchedulerService: CloseableExecutorService<ExecutorService>
        private val myDispatcherExecutorService: CloseableExecutorService<ExecutorService>
        private var myScheduledExecutorService: CloseableExecutorService<ScheduledExecutorService>

        init {
            myComputationSchedulerService = CloseableExecutorService(
                    serverDispatcher,
                    ServiceFactory(
                        "Azure Rx computation pool",
                        TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_THROTTLER_COMPUTATION_POOL_MIN_SIZE, 0),
                        TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_THROTTLER_COMPUTATION_POOL_MAX_SIZE, 5),
                        TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_THROTTLER_COMPUTATION_POOL_QUEUE_MAX_SIZE, 50)
                    )
            )
            myIOSchedulerService = CloseableExecutorService(
                    serverDispatcher,
                    ServiceFactory(
                        "Azure Rx io pool",
                        TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_THROTTLER_IO_POOL_MIN_SIZE, 1),
                        TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_THROTTLER_IO_POOL_MAX_SIZE, 20),
                        TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_THROTTLER_IO_POOL_QUEUE_MAX_SIZE, 500)
                    )
            )
            myNewThreadSchedulerService = CloseableExecutorService(
                    serverDispatcher,
                    ServiceFactory(
                        "Azure Rx new thread pool",
                        TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_THROTTLER_NEW_THREAD_POOL_MIN_SIZE, 0),
                        TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_THROTTLER_NEW_THREAD_POOL_MAX_SIZE, 2),
                        TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_THROTTLER_NEW_THREAD_POOL_QUEUE_MAX_SIZE, 50)
                    )
            )
            myScheduledExecutorService = CloseableExecutorService(
                    serverDispatcher,
                    ScheduledServiceFactory(
                            "Azure Rx schedulers pool",
                            TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_THROTTLER_SCHEDULER_POOL_MAX_SIZE, 3))
            )
            myDispatcherExecutorService = CloseableExecutorService(
                    serverDispatcher,
                    ServiceFactory(
                            "Azure Dispatcher pool",
                            TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_THROTTLER_DISPATCHER_POOL_MIN_SIZE, 0),
                            TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_THROTTLER_DISPATCHER_POOL_MAX_SIZE, 8),
                            TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_THROTTLER_DISPATCHER_POOL_QUEUE_MAX_SIZE, 200)
                    )
            )

            myIOScheduler = Schedulers.from(myIOSchedulerService.executor)
            myComputationScheduler = Schedulers.from(myComputationSchedulerService.executor)
            myNewThreadUtilScheduler = Schedulers.from(myNewThreadSchedulerService.executor)

            @Suppress("DEPRECATION")
            RxJavaPlugins.getInstance().registerSchedulersHook(RxJavaSchedulersHookEx(myComputationScheduler, myIOScheduler, myNewThreadUtilScheduler))
            RxJavaHooks.setOnComputationScheduler { myComputationScheduler }
            RxJavaHooks.setOnIOScheduler { myIOScheduler }
            RxJavaHooks.setOnNewThreadScheduler { myNewThreadUtilScheduler }
            RxJavaHooks.setOnGenericScheduledExecutorService { myScheduledExecutorService.executor }

            SdkContext.setRxScheduler(myIOScheduler)

            myDispatcher = myDispatcherExecutorService.executor.asCoroutineDispatcher()
        }

        override fun getReadRequestsSchedulers(): AzureThrottlerSchedulers =
            AzureThrottlerSchedulers(Schedulers.immediate(), myComputationScheduler)

        override fun getActionRequestsSchedulers(): AzureThrottlerSchedulers =
            AzureThrottlerSchedulers(myIOScheduler, myComputationScheduler)

        override fun getDispatcher(): CoroutineDispatcher = myDispatcher

        override fun close() {
            myComputationSchedulerService.close()
            myIOSchedulerService.close()
            myNewThreadSchedulerService.close()
            myScheduledExecutorService.close()
            myDispatcher.close()
            myDispatcherExecutorService.close()
        }

        private class CloseableScheduledExecutorService(serverDispatcher: EventDispatcher<BuildServerListener>, poolName: String, poolSize: Int) : Closeable {
            private var myCloseable: Closeable
            public val executor: ScheduledExecutorService

            init {
                executor = ExecutorsFactory.newFixedScheduledDaemonExecutor(poolName, poolSize)
                myCloseable = Closeable { ThreadUtil.shutdownGracefully(executor, poolName) }

                serverDispatcher.addListener(object : BuildServerAdapter() {
                    override fun serverShutdown() {
                        myCloseable.close()
                    }
                })
            }

            override fun close() = myCloseable.close()
        }

        private interface ExecutorServiceFectory<T : ExecutorService> {
            val name: String
            fun create() : T
        }

        private class ScheduledServiceFactory(override val name: String, val poolSize: Int): ExecutorServiceFectory<ScheduledExecutorService> {
            override fun create(): ScheduledExecutorService {
                return ExecutorsFactory.newFixedScheduledDaemonExecutor(name, poolSize)
            }
        }

        private class ServiceFactory(override val name: String, val minPoolSize: Int, val maxPoolSize: Int, val maxQueueSize: Int): ExecutorServiceFectory<ExecutorService> {
            override fun create(): ExecutorService {
                return ExecutorsFactory.newFixedDaemonExecutor(name, minPoolSize, maxPoolSize, maxQueueSize)
            }
        }

        private class CloseableExecutorService<T : ExecutorService>(serverDispatcher: EventDispatcher<BuildServerListener>, factory: ExecutorServiceFectory<T>) : Closeable {
            private var myCloseable: Closeable
            public val executor: T

            init {
                executor = factory.create()
                myCloseable = Closeable { ThreadUtil.shutdownGracefully(executor, factory.name) }

                serverDispatcher.addListener(object : BuildServerAdapter() {
                    override fun serverShutdown() {
                        myCloseable.close()
                    }
                })
            }

            override fun close() = myCloseable.close()
        }

        private class RxJavaSchedulersHookEx(
                private val computationScheduler: Scheduler,
                private val ioScheduler: Scheduler,
                private val newThreadScheduler: Scheduler
        ) : RxJavaSchedulersHook() {
            override fun getComputationScheduler(): Scheduler {
                return computationScheduler
            }

            override fun getIOScheduler(): Scheduler {
                return ioScheduler
            }

            override fun getNewThreadScheduler(): Scheduler {
                return newThreadScheduler
            }
        }
    }
}
