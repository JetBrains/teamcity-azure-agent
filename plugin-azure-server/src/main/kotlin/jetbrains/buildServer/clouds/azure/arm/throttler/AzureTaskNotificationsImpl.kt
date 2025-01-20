package jetbrains.buildServer.clouds.azure.arm.throttler

import rx.Observable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

class AzureTaskNotificationsImpl : AzureTaskNotifications {
    private val myHandlers: MutableMap<KClass<out AzureTaskEventArgs>, MutableList<AzureTaskEventHandler<AzureTaskEventArgs>>> = ConcurrentHashMap()

    override fun <TArgs : AzureTaskEventArgs> registerHandler(eventClass: KClass<out TArgs>, handler: AzureTaskEventHandler<TArgs>) {
        val handlers: MutableList<AzureTaskEventHandler<AzureTaskEventArgs>> =
            myHandlers.getOrPut(eventClass) { CopyOnWriteArrayList() }
        handlers.add(handler as AzureTaskEventHandler<AzureTaskEventArgs>)
    }

    override fun raise(event: AzureTaskEventArgs) : Observable<Unit> {
        val handlersList = myHandlers[event::class]
        return if (handlersList.isNullOrEmpty())
            Observable.just(Unit)
        else {
            Observable
                .from(handlersList)
                .concatMap { it.handle(event).defaultIfEmpty(Unit) }
                .takeLast(1)
        }
    }
}
