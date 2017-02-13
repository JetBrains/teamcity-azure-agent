package jetbrains.buildServer.clouds.azure.arm.utils

import rx.Completable
import rx.Observable
import kotlin.coroutines.experimental.suspendCoroutine

suspend fun Completable.awaitOne(): Unit {
    return suspendCoroutine { cont ->
        subscribe({
            cont.resume(Unit)
        }, { e ->
            cont.resumeWithException(e!!)
        })
    }
}

suspend fun <T> Observable<T>.awaitOne(): T {
    return suspendCoroutine { cont ->
        subscribe({ r ->
            cont.resume(r)
        }, { e ->
            cont.resumeWithException(e)
        })
    }
}