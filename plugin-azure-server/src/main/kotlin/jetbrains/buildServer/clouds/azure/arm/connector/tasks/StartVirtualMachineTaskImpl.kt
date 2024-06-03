

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
            .getByResourceGroupWithRawServiceResponseAsync(parameter.groupId, parameter.name)
            .flatMap { raw ->
                val rawVm = mutableMapOf(*raw.body().map { it.toPair() }.toTypedArray())

                @Suppress("UNCHECKED_CAST")
                val propertiesRaw = rawVm[TEMPATE_PROPERTIES_FIELD] as MutableMap<String, Any>?

                val source = if (propertiesRaw != null) {
                    propertiesRaw.put(USERDATA_TEMPLATE_FIELD, parameter.userData)

                    LOG.debug("Updating userData for virtual machine: ${rawVm["id"]}. CorellationId: ${taskContext.corellationId}")
                    taskContext
                        .getDeferralSequence()
                        .flatMap {
                            api
                                .virtualMachinesEx()
                                .inner()
                                .updateRawAsync(parameter.groupId, parameter.name, rawVm)
                                .doOnNext {
                                    LOG.debug("Updated userData for virtual machine: ${it["id"]}. CorellationId: ${taskContext.corellationId}")
                                }
                        }
                } else {
                    Observable.error(Exception("Could not update userData for VM. VM metadata JSON is empty. Group name: ${parameter.groupId}, VM name:${parameter.name}. CorellationId: ${taskContext.corellationId}"))
                }

                source.flatMap {
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
