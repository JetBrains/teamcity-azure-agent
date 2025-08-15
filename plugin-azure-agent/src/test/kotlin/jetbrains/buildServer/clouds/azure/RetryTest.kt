package jetbrains.buildServer.clouds.azure

import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.Assert.fail
import org.testng.annotations.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Test
class RetryTest {

    fun testRetryOnSuccess() {
        // Test that the block executes successfully and returns the result
        val sleepValue = AtomicLong(0)
        val result = Retry<Int>()
            .sleep { sleepValue.set(it) }
            .block { 42 }

        assertEquals(result, 42)
        assertEquals(sleepValue.get(), 0L)
    }

    fun testRetryOnException() {
        // Test that retry happens when specified exception occurs
        val counter = AtomicInteger(0)
        val loggedSleeps = mutableListOf<Long>()
        val result = Retry<Int>()
            .retryOn(RuntimeException::class.java)
            .maxRetries(3)
            .sleep { loggedSleeps.add(it) }
            .block {
                if (counter.incrementAndGet() < 3) {
                    throw RuntimeException("Test exception")
                }
                counter.get()
            }
        
        assertEquals(result, 3)
        assertEquals(counter.get(), 3)
        assertEquals(loggedSleeps.size, 2)
        assertEquals(loggedSleeps[0], 1000L)
        assertEquals(loggedSleeps[1], 2000L)
    }

    fun testNoRetryOnDifferentException() {
        // Test that retry doesn't happen for non-specified exceptions
        val counter = AtomicInteger(0)
        val loggedSleeps = mutableListOf<Long>()
        try {
            Retry<Int>()
                .retryOn(IllegalArgumentException::class.java)
                .maxRetries(3)
                .sleep { loggedSleeps.add(it) }
                .block {
                    counter.incrementAndGet()
                    throw RuntimeException("Test exception")
                }
            fail("Should have thrown RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals(counter.get(), 1)
            assertEquals(loggedSleeps.size, 0)
        }
    }

    fun testMaxRetries() {
        // Test that retry stops after maxRetries
        val counter = AtomicInteger(0)
        val loggedSleeps = mutableListOf<Long>()
        try {
            Retry<Int>()
                .retryOn(RuntimeException::class.java)
                .maxRetries(3)
                .sleep { loggedSleeps.add(it) }
                .block {
                    counter.incrementAndGet()
                    throw RuntimeException("Test exception")
                }
            fail("Should have thrown RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals(counter.get(), 3)
            assertEquals(loggedSleeps.size, 2)
            assertEquals(loggedSleeps[0], 1000L)
            assertEquals(loggedSleeps[1], 2000L)
        }
    }

    fun testIntervalFunction() {
        // Test that intervalFunction is used for delay calculation
        val counter = AtomicInteger(0)
        val loggedSleeps = mutableListOf<Long>()

        val result = Retry<Int>()
            .retryOn(RuntimeException::class.java)
            .maxRetries(3)
            .sleep { loggedSleeps.add(it) }
            .intervalFunction { arrayOf(1000L, 1234L)[it - 1] }
            .block {
                if (counter.incrementAndGet() < 3) {
                    throw RuntimeException("Test exception")
                }
                counter.get()
            }

        assertEquals(result, 3)
        assertEquals(counter.get(), 3)
        assertEquals(loggedSleeps.size, 2)
        assertEquals(loggedSleeps[0], 1000L)
        assertEquals(loggedSleeps[1], 1234L)
    }

    fun testMaxDelay() {
        // Test that delay is capped at maxDelay
        val counter = AtomicInteger(0)
        val loggedSleeps = mutableListOf<Long>()
        val result = Retry<Int>()
            .retryOn(RuntimeException::class.java)
            .maxRetries(3)
            .sleep { loggedSleeps.add(it) }
            .intervalFunction { 5000L } // Large delay
            .maxDelay(10L) // But capped at 10ms
            .block {
                if (counter.incrementAndGet() < 3) {
                    throw RuntimeException("Test exception")
                }
                counter.get()
            }

        assertEquals(result, 3)
        assertEquals(counter.get(), 3)
        assertEquals(loggedSleeps.size, 2)
        assertEquals(loggedSleeps[0], 10L)
        assertEquals(loggedSleeps[1], 10L)
    }

    fun testLogRetry() {
        // Test that logRetry is called on each retry
        val counter = AtomicInteger(0)
        val loggedAttempts = mutableListOf<Int>()
        val loggedExceptions = mutableListOf<Throwable>()

        val result = Retry<Int>()
            .retryOn(RuntimeException::class.java)
            .maxRetries(3)
            .sleep {  }
            .logRetry { attempt, throwable ->
                loggedAttempts.add(attempt)
                loggedExceptions.add(throwable)
            }
            .block {
                if (counter.incrementAndGet() < 3) {
                    throw RuntimeException("Test exception ${counter.get()}")
                }
                counter.get()
            }

        assertEquals(result, 3)
        assertEquals(counter.get(), 3)
        assertEquals(loggedAttempts.size, 2)
        assertEquals(loggedAttempts[0], 1)
        assertEquals(loggedAttempts[1], 2)
        assertTrue(loggedExceptions[0].message!!.contains("1"))
        assertTrue(loggedExceptions[1].message!!.contains("2"))
    }
}
