

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

class FetchVirtualMachineSizesTaskImpl  : AzureThrottlerCacheableTaskBaseImpl<String, List<String>>() {
    override fun createQuery(api: AzureApi, taskContext: AzureTaskContext, parameter: String): Single<List<String>> {
        return api
                .virtualMachines()
                .sizes()
                .listByRegionAsync(parameter)
                .map { it.name() }
                .toList()
                .last()
                .toSingle()
    }
}
