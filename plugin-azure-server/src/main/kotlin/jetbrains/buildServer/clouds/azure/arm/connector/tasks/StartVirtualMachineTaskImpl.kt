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

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskNotifications
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTaskBaseImpl
import rx.Observable
import rx.Single

data class StartVirtualMachineTaskParameter(
    val groupId: String,
    val name: String,
    val userData: String
)

class StartVirtualMachineTaskImpl(private val myNotifications: AzureTaskNotifications) : AzureThrottlerTaskBaseImpl<AzureApi, StartVirtualMachineTaskParameter, Unit>() {
    override fun create(api: AzureApi, taskContext: AzureTaskContext, parameter: StartVirtualMachineTaskParameter): Single<Unit> {
        LOG.debug("Starting virtual machine. Group: ${parameter.groupId}, Name: ${parameter.name}, corellationId: [${taskContext.corellationId}]")
        return api
            .virtualMachinesEx()
            .inner()
            .updateRawAsync(
                parameter.groupId,
                parameter.name,
                mapOf(
                    TEMPATE_PROPERTIES_FIELD to mapOf(
                        USERDATA_TEMPLATE_FIELD to parameter.userData
                    )
                )
            )
            .doOnNext {
                LOG.debug("Updated userData for virtual machine: ${it["id"]}. CorellationId: ${taskContext.corellationId}")
            }
            .flatMap {
                taskContext
                    .getDeferralSequence()
                    .flatMap {
                        api
                            .virtualMachines()
                            .getByResourceGroupAsync(parameter.groupId, parameter.name)
                            .flatMap { vm ->
                                if (vm != null) {
                                    vm.startAsync()
                                        .toObservable<Unit>()
                                        .concatMap { myNotifications.raise(AzureTaskVirtualMachineStatusChangedEventArgs(api, vm)) }
                                } else {
                                    LOG.warnAndDebugDetails("Could not find resource to start. GroupId: ${parameter.groupId}, Name: ${parameter.name}", null)
                                    Observable.just(Unit)
                                }
                            }
                    }
            }
            .defaultIfEmpty(Unit)
            .toSingle()
    }

    companion object {
        private val LOG = Logger.getInstance(StartVirtualMachineTaskImpl::class.java.name)
        private const val TEMPATE_PROPERTIES_FIELD = "properties"
        private const val USERDATA_TEMPLATE_FIELD = "userData"
    }
}
