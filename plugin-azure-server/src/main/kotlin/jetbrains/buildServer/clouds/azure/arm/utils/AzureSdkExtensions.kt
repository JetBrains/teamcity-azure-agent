

package jetbrains.buildServer.clouds.azure.arm.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Completable
import rx.CompletableSubscriber
import rx.Observable
import rx.Single
import rx.SingleSubscriber
import rx.Subscriber
import rx.Subscription
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
