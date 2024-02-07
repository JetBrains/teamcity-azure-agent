

package jetbrains.buildServer.clouds.azure.arm.throttler

import rx.Observable
import rx.internal.util.SubscriptionList
import rx.subjects.Subject
import java.lang.Exception
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

class AzureThrottlerRequestQueueImpl<I, P, T>(
        private val parameterEqualityComparer: AzureThrottlerTaskParameterEqualityComparer<P>
) : AzureThrottlerRequestQueue<I, P, T> {
    private val myRequestQueue = ConcurrentLinkedQueue<QueueItem<T, P>>()
    private val myRequestQueueId = AtomicLong(0)

    override fun addRequest(timeToExecute: LocalDateTime, parameter: P, result: Subject<AzureThrottlerAdapterResult<T>, AzureThrottlerAdapterResult<T>>, force: Boolean, canBeCombined: Boolean, attemptNo: Int, createdDate: LocalDateTime) {
        myRequestQueue.add(QueueItem(
                myRequestQueueId.incrementAndGet(),
                timeToExecute,
                parameter,
                force,
                canBeCombined,
                result,
                attemptNo,
                createdDate
        ))
    }

    override fun extractNextBatch(): AzureThrottlerRequestBatch<P, T> {
        val forceItem = myRequestQueue.firstOrNull { it.force }
        if (forceItem != null) {
            return extractBatch(forceItem)
        }
        val expiredItem = myRequestQueue.firstOrNull { it.timeToExecute <= LocalDateTime.now(Clock.systemUTC()) }
        if (expiredItem != null) {
            return extractBatch(expiredItem)
        }
        return EmptyBatch()
    }

    override fun extractBatchFor(parameter: P): AzureThrottlerRequestBatch<P, T> {
        val item = myRequestQueue.firstOrNull { areParametersEqual(it.parameter, parameter) }
        if (item == null) {
            return EmptyBatch()
        }
        return extractBatch(item)
    }

    private fun extractBatch(item: QueueItem<T, P>): AzureThrottlerRequestBatch<P, T> {
        if (!myRequestQueue.remove(item)) {
            return EmptyBatch()
        }
        val items = mutableListOf<QueueItem<T, P>>(item)
        myRequestQueue
                .filter { it.canBeCombined && areParametersEqual(it.parameter, item.parameter) }
                .forEach {
                    if (myRequestQueue.remove(it)) {
                        items.add(it)
                    }
                }
        return Batch(item.parameter, items.toTypedArray())
    }

    private fun areParametersEqual(parameter: P, other: P): Boolean {
        return parameterEqualityComparer.areParametersEqual(parameter, other)
    }

    class EmptyBatch<P, T> : AzureThrottlerRequestBatch<P, T> {
        override fun canBeCombined(): Boolean {
            throw Exception("Request batch is empty")
        }

        override fun getMaxAttempNo(): Int {
            throw Exception("Request batch is empty")
        }

        override fun getMinCreatedDate(): LocalDateTime {
            throw Exception("Request batch is empty")
        }

        override val parameter: P
            get() = throw Exception("Request batch is empty")

        override fun count(): Int {
            return 0
        }

        override fun hasForceRequest(): Boolean {
            throw Exception("Request batch is empty")
        }

        override fun subscribeTo(source: Observable<AzureThrottlerAdapterResult<T>>, anchor: SubscriptionList) {
            throw Exception("Request batch is empty")
        }
    }

    class Batch<P, T>(
            override val parameter: P,
            private val items : Array<QueueItem<T, P>>
    ): AzureThrottlerRequestBatch<P, T> {
        override fun canBeCombined(): Boolean {
            return items.all { it.canBeCombined }
        }

        override fun getMaxAttempNo(): Int {
            return items.maxByOrNull  { it.attemptNo }!!.attemptNo
        }

        override fun getMinCreatedDate(): LocalDateTime {
            return items.minByOrNull { it.createdDate }!!.createdDate
        }

        override fun count(): Int {
            return items.size
        }

        override fun hasForceRequest(): Boolean {
            return items.any { it.force }
        }

        override fun subscribeTo(source: Observable<AzureThrottlerAdapterResult<T>>, anchor: SubscriptionList) {
            items.forEach { queueItem ->
                val subscription = SubscriptionList()
                anchor.add(subscription)

                subscription.add(source
                        .take(1)
                        .doOnUnsubscribe { anchor.remove(subscription) }
                        .subscribe(queueItem.result));
            }
        }
    }

    data class QueueItem<T, P>(
            val id: Long,
            val timeToExecute: LocalDateTime,
            val parameter: P,
            val force: Boolean,
            val canBeCombined: Boolean,
            val result: Subject<AzureThrottlerAdapterResult<T>, AzureThrottlerAdapterResult<T>>,
            val attemptNo: Int,
            val createdDate: LocalDateTime
    )
}
