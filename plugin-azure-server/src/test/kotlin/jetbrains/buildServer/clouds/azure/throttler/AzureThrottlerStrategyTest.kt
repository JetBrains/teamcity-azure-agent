package jetbrains.buildServer.clouds.azure.throttler

import jetbrains.buildServer.clouds.azure.arm.throttler.*
import org.jmock.Expectations
import org.jmock.MockObjectTestCase
import org.jmock.Mockery
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.time.LocalDateTime

class AzureThrottlerStrategyTest : MockObjectTestCase() {
    private var enableAggressiveThrottlingWhenReachLimitInPercents: Int = 0
    private var resourceReservationInPercents: Int = 0
    private var randomTasksResourceReservationInPercents: Int = 0

    private lateinit var mockery: Mockery
    private lateinit var adapter: AzureThrottlerAdapter<Unit>
    private lateinit var container: AzureThrottlerStrategyTaskContainer<String>
    private lateinit var tasks: List<AzureThrottlerStrategyTask<String>>

    @BeforeMethod
    fun beforeMethod() {
        mockery = Mockery()

        adapter = mockery.mock()

        tasks = emptyList()

        container = mockery.mock()
        mockery.checking(
                object : Expectations() {
                    init {
                        oneOf(container).getTaskList()
                        will(returnValue(tasks))
                    }
                }
        )
    }

    @Test
    fun shouldBeInitializedWithNormalFlow() {
        // Given
        // When
        val instance = createInstance()
        instance.setContainer(container)

        // Then
        Assert.assertEquals(instance.getFlow(), AzureThrottlerFlow.Normal)
    }

    @Test(dataProvider = "testThrottlerTimes")
    fun shouldSetThrottlerTime(defaultReads: Long, remainingReads: Long, aggressiveLimits: Int, windowInMs: Long, expectedTime: Long) {
        // Given
        enableAggressiveThrottlingWhenReachLimitInPercents = aggressiveLimits

        val instance = createInstance()
        instance.setContainer(container)

        mockery.checking(object : Expectations() {
            init {
                oneOf(adapter).getDefaultReads()
                will(returnValue(defaultReads))

                oneOf(adapter).getRemainingReads()
                will(returnValue(remainingReads))

                oneOf(adapter).getWindowStartDateTime()
                will(returnValue(LocalDateTime.parse("2019-10-14T00:00:00")))

                oneOf(adapter).getWindowWidthInMilliseconds()
                will(returnValue(windowInMs))
            }
        })

        mockery.checking(object : Expectations() {
            init {
                oneOf(adapter).setThrottlerTime(expectedTime)
            }
        })

        // When
        instance.applyTaskChanges()

        // Then
        mockery.assertIsSatisfied()
    }

    private fun createInstance() : AzureThrottlerStrategyImpl<Unit, String> {
        return AzureThrottlerStrategyImpl(
                adapter,
                randomTasksResourceReservationInPercents,
                resourceReservationInPercents,
                enableAggressiveThrottlingWhenReachLimitInPercents)
    }

    @DataProvider
    private fun testThrottlerTimes(): Array<Array<out Any?>> {
        //defaultReads: Long, remainingReads: Long, aggressiveLimits: Int, windowInMs: Long, expectedTime: Long
        return arrayOf(
                arrayOf(0, 0, 0, 1000, 1000),
                arrayOf(0, 0, 0, 0, 0),
                arrayOf(20, 0, 0, 1234, 1234),
                arrayOf(20, 0, 100, 1234, 1234),

                arrayOf(10, 1, 90, 1234, 1234),
                arrayOf(20, 2, 90, 1234, 1234/2),
                arrayOf(20, 2, 100, 1234, 0),
                arrayOf(20, 20, 90, 1234, 0)
        )
    }

    private inline fun <reified T : Any> Mockery.mock(): T = this.mock(T::class.java)!!
}
