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
                .concatMap { it.handle(event) }
                .last()
        }
    }
}
