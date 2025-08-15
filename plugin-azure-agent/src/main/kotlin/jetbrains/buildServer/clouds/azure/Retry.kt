package jetbrains.buildServer.clouds.azure

import kotlin.math.min

interface RetryConfig<T : RetryConfig<T>> {
    fun retryOn(vararg exceptions: Class<out Throwable>): T
    fun intervalFunction(intervalFunction: (attempt: Int) -> Long): T
    fun sleep(sleepFunction: (delay: Long) -> Unit): T
    fun maxDelay(maxDelay: Long): T
    fun maxRetries(maxRetries: Int): T
    fun logRetry(logRetry: (attempt: Int, throwable: Throwable) -> Unit): T
}

class Retry<T> : RetryConfig<Retry<T>> {
    private var exceptions: Array<out Class<out Throwable>> = emptyArray()
    private var intervalFunction: (attempt: Int) -> Long = { attempt -> attempt * 1000L }
    private var sleepFunction: (delay: Long) -> Unit = { Thread.sleep(it) }
    private var logRetry: (attempt: Int, throwable: Throwable) -> Unit = { _, _ -> }
    private var maxDelay: Long = 10000L
    private var maxRetries: Int = 3

    override fun retryOn(vararg exceptions: Class<out Throwable>): Retry<T> {
        this.exceptions = exceptions
        return this
    }

    override fun intervalFunction(intervalFunction: (Int) -> Long): Retry<T> {
        this.intervalFunction = intervalFunction
        return this
    }

    override fun sleep(sleepFunction: (Long) -> Unit): Retry<T> {
        this.sleepFunction = sleepFunction
        return this
    }

    override fun maxDelay(maxDelay: Long): Retry<T> {
        this.maxDelay = maxDelay
        return this
    }

    override fun maxRetries(maxRetries: Int): Retry<T> {
        this.maxRetries = maxRetries
        return this
    }

    override fun logRetry(logRetry: (Int, Throwable) -> Unit): Retry<T> {
        this.logRetry = logRetry
        return this
    }

    fun block(block: () -> T): T {
        repeat(maxRetries - 1) { attempt ->
            try {
                block()
            } catch (e: Throwable) {
                if (exceptions.any { it.isAssignableFrom(e.javaClass) }) {
                    logRetry(attempt + 1, e)
                    val delay = min(intervalFunction(attempt + 1), maxDelay)
                    sleepFunction(delay)
                } else {
                    throw e
                }
            }
        }
        return block()
    }
}
