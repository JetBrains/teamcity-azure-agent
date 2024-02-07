

package jetbrains.buildServer.clouds.azure.arm.throttler

import jetbrains.buildServer.clouds.azure.arm.connector.tasks.AzureApi
import kotlin.reflect.KClass

interface AzureTaskNotifications {
    fun <TArgs : AzureTaskEventArgs> registerHandler(eventClass: KClass<out TArgs>, handler: AzureTaskEventHandler<TArgs>)

    fun raise(event: AzureTaskEventArgs)
}

inline fun <reified TArgs : AzureTaskEventArgs> AzureTaskNotifications.register(handler: AzureTaskEventHandler<TArgs>) {
    this.registerHandler(TArgs::class, handler)
}

inline fun <reified TArgs : AzureTaskEventArgs> AzureTaskNotifications.register(crossinline handler: (eventArgs: TArgs) -> Unit) {
    this.registerHandler(TArgs::class, object : AzureTaskEventHandler<TArgs> {
        override fun handle(args: TArgs) {
            handler(args)
        }
    })
}

interface AzureTaskEventArgs {
    val api: AzureApi
}

interface AzureTaskEventHandler<TArgs : AzureTaskEventArgs> {
    fun handle(args: TArgs)
}
