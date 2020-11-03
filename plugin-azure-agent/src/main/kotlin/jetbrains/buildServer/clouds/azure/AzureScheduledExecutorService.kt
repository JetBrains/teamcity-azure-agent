package jetbrains.buildServer.clouds.azure

import java.util.concurrent.TimeUnit

interface AzureScheduledExecutorService {
    fun scheduleWithFixedDelay(r: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit): AzureCancellable
}


interface AzureCancellable {
    fun cancel()
}
