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

import com.microsoft.azure.management.Azure
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
    val api: Azure
}

interface AzureTaskEventHandler<TArgs : AzureTaskEventArgs> {
    fun handle(args: TArgs)
}
