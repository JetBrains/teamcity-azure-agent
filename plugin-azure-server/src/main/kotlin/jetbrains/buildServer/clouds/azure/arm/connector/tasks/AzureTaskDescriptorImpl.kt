package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskDescriptor
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskNotifications
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTask

class AzureTaskDescriptorImpl<I, P, T>(
        override val taskId: I,
        private val factory: (AzureTaskNotifications) -> AzureThrottlerTask<AzureApi, P, T>
) : AzureTaskDescriptor<AzureApi, I, P, T> {
    override fun create(taskNotifications: AzureTaskNotifications): AzureThrottlerTask<AzureApi, P, T> {
        return factory(taskNotifications)
    }
}
