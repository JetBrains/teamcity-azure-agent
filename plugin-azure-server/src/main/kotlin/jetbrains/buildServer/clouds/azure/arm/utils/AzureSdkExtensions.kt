/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import rx.Completable
import rx.Observable
import kotlin.coroutines.experimental.suspendCoroutine

suspend fun Completable.awaitOne() {
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

suspend fun <T> Observable<T>.awaitList(): List<T> {
    return suspendCoroutine { cont ->
        toList().subscribe({ r ->
            cont.resume(r)
        }, { e ->
            cont.resumeWithException(e)
        })
    }
}
