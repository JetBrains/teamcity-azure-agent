package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskDescriptor
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTask

class AzureTaskDescriptorImpl<I, P, T>(
        override val taskId: I,
        private val factory: () -> AzureThrottlerTask<Azure, P, T>
) : AzureTaskDescriptor<Azure, I, P, T> {
    override fun create(): AzureThrottlerTask<Azure, P, T> {
        return factory()
    }
}
