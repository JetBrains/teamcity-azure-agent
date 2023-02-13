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

package jetbrains.buildServer.clouds.azure.arm.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import rx.*
import java.lang.IllegalStateException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun Completable.awaitOne(): Unit = suspendCancellableCoroutine { cont ->
    lateinit var subscription: Subscription

    subscribe(object : CompletableSubscriber {
        override fun onSubscribe(d: Subscription) {
            subscription = d
        }

        override fun onError(e: Throwable) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onCompleted() {
            if (cont.isActive) cont.resume(Unit)
        }
    })

    cont.invokeOnCancellation {
        subscription.unsubscribe()
    }
}

@Suppress("UNCHECKED_CAST")
suspend fun <T> Observable<T>.awaitOne(): T = suspendCancellableCoroutine { cont ->
    lateinit var subscription: Subscription
    var value: T? = null
    var seenValue = false
    var completed = false

    subscription = subscribe(object : Subscriber<T>() {
        override fun onNext(t: T) {
            if (completed) {
                if (cont.isActive) {
                    cont.resumeWithException(IllegalStateException("Received onNext call after completion"))
                }
                return
            }

            if (seenValue) {
                if (cont.isActive) {
                    cont.resumeWithException(IllegalArgumentException("More than one onNext call"))
                }
                subscription.unsubscribe()
            } else {
                value = t
                seenValue = true
            }
        }

        override fun onCompleted() {
            if (completed) {
                if (cont.isActive) {
                    cont.resumeWithException(IllegalStateException("Received onCompleted call after completion"))
                }
                return
            }

            completed = true

            if (!cont.isActive) return

            if (seenValue) {
                cont.resume(value as T)
            } else {
                cont.resumeWithException(NoSuchElementException("No value received via onNext call"))
            }
        }

        override fun onError(e: Throwable) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    })

    cont.invokeOnCancellation {
        subscription.unsubscribe()
    }
}

suspend fun <T> Single<T>.awaitOne(): T = suspendCancellableCoroutine { cont ->
    var subscription: Subscription? = null

    subscription = subscribe(object : SingleSubscriber<T>() {
        override fun onSuccess(t: T) {
            subscription?.unsubscribe()
            if (cont.isActive) cont.resume(t)
        }

        override fun onError(e: Throwable) {
            subscription?.unsubscribe()
            if (cont.isActive) cont.resumeWithException(e)
        }
    })

    cont.invokeOnCancellation {
        subscription.unsubscribe()
    }
}

suspend fun <T> Observable<T>.awaitList(): List<T> = toList().awaitOne()
