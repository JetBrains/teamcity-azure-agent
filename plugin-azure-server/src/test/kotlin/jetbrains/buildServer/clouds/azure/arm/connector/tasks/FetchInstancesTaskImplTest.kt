package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import io.mockk.mockk
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskNotifications
import org.jmock.MockObjectTestCase
import org.junit.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

class FetchInstancesTaskImplTest : MockObjectTestCase() {
    private lateinit var myNotifications: AzureTaskNotifications

    @BeforeMethod
    fun beforeMethod() {
        myNotifications = mockk(relaxed = true)
    }

    @Test
    fun shouldGetFromCacheReturnNullWhenCacheIsNotFilled() {
        // Given
        val instance = createInstance()

        // When
        val result = instance.getFromCache(FetchInstancesTaskParameter("serverId"))

        // Then
        Assert.assertNull(result)
    }

    private fun createInstance(): FetchInstancesTaskImpl {
        return FetchInstancesTaskImpl(myNotifications)
    }
}
