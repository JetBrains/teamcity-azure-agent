package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.compute.OperatingSystemStateTypes
import com.microsoft.azure.management.compute.OperatingSystemTypes
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import rx.Single

data class CustomImageTaskImageDescriptor(
        val id: String,
        val name: String,
        val regionName: String,
        val osState: OperatingSystemStateTypes,
        val osType: OperatingSystemTypes)

class FetchCustomImagesTaskImpl : AzureThrottlerCacheableTaskBaseImpl<Unit, List<CustomImageTaskImageDescriptor>>() {
    override fun create(api: Azure, parameter: Unit): Single<List<CustomImageTaskImageDescriptor>> {
        return api
                .virtualMachineCustomImages()
                .listAsync()
                .map {
                    CustomImageTaskImageDescriptor(
                            it.id(),
                            it.name(),
                            it.regionName(),
                            it.osDiskImage().osState(),
                            it.osDiskImage().osType()
                    )
                }
                   .toList()
                .last()
                .toSingle()
    }
}
