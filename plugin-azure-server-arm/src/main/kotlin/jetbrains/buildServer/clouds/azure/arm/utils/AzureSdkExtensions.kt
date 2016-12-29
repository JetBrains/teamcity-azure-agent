package jetbrains.buildServer.clouds.azure.arm.utils

import com.microsoft.azure.management.resources.fluentcore.collection.SupportsDeleting
import com.microsoft.azure.management.resources.fluentcore.model.Creatable
import com.microsoft.rest.ServiceCallback
import java.util.concurrent.CompletableFuture

fun <T> Creatable<T>.aCreate(): CompletableFuture<T> {
    val future = CompletableFuture<T>()

    this.createAsync(object: ServiceCallback<T>() {
        override fun failure(e: Throwable?) {
            future.completeExceptionally(e)
        }

        override fun success(result: T) {
            future.complete(result)
        }
    })

    return future
}

fun SupportsDeleting.aDelete(id: String): CompletableFuture<Void> {
    val future = CompletableFuture<Void>()

    this.deleteAsync(id, object: ServiceCallback<Void>() {
        override fun failure(e: Throwable?) {
            future.completeExceptionally(e)
        }

        override fun success(result: Void) {
            future.complete(result)
        }
    })

    return future
}