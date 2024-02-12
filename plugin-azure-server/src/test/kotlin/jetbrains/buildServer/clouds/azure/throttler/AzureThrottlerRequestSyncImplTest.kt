/*
 * Copyright 2000-2024 JetBrains s.r.o.
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
package jetbrains.buildServer.clouds.azure.throttler

import io.mockk.*
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerRequestSyncImpl
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerSleeper
import org.jmock.MockObjectTestCase
import org.joda.time.DateTime
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread
import kotlin.random.Random

class AzureThrottlerRequestSyncImplTest {
    private lateinit var sleeper: AzureThrottlerSleeper

    @BeforeMethod
    fun beforeMethod() {
        MockKAnnotations.init(this)

        sleeper = mockk()
    }

    @AfterMethod
    fun afterMethod() {
    }

    @Test(invocationCount = 1)
    fun shouldThreadProcessingBeFair() {
        // Given
        val instance = createInstance()

        val additionalThreadCount = 1
        val random = Random(1233)
        val startBarrier = CyclicBarrier(4 + additionalThreadCount)
        val thread1LockTaken = CountDownLatch(1)
        val thread2IsWaiting = CountDownLatch(2)

        val thread1 = thread(start = true) {
            startBarrier.await()
            instance.waitForNextTimeSlot()
        }

        val thread2 = thread(start = true) {
            startBarrier.await()
            thread1LockTaken.await()
            instance.waitForNextTimeSlot()
        }

        val thread3 = thread(start = true) {
            startBarrier.await()
            thread1LockTaken.await()
            Thread.sleep(random.nextLong(390, 425))
            instance.waitForNextTimeSlot()
        }

        val lockThreadOrder = Collections.synchronizedList(mutableListOf<Long>())
        every { sleeper.sleep((any())) } answers {
            lockThreadOrder.add(Thread.currentThread().id)
            thread1LockTaken.countDown()
            thread2IsWaiting.countDown()
            Thread.sleep(400)
        }

        val executor = Executors.newFixedThreadPool(additionalThreadCount)
        val additionalFeatures = (1..additionalThreadCount).map {
            executor.submit {
                startBarrier.await()
                thread1LockTaken.await()
                val time = DateTime.now().toInstant().millis + random.nextLong(375, 425)
                var x = 1
                while(time >= DateTime.now().toInstant().millis) {
                    x += 1
                }
                instance.waitForNextTimeSlot()
            }
        }

        instance.waitForNextTimeSlot()

        // When
        startBarrier.await()

        thread1.join()
        thread2.join()
        thread3.join()
        additionalFeatures.forEach { it.get() }

        // Then
        Assert.assertEquals(thread2.id, lockThreadOrder[1])
    }
    private fun createInstance() = AzureThrottlerRequestSyncImpl(sleeper)
}
