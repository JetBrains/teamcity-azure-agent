package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

class FetchVirtualMachineSizesTaskImpl  : AzureThrottlerCacheableTaskBaseImpl<String, List<String>>() {
    override fun create(api: Azure, parameter: String): Single<List<String>> {
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

