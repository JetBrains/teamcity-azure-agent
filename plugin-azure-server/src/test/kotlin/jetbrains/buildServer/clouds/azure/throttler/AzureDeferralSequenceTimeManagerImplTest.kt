package jetbrains.buildServer.clouds.azure.throttler

import io.mockk.mockk
import io.mockk.verify
import jetbrains.buildServer.BaseTestCase
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureDeferralSequenceTimeManagerImpl
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_BUCKET_REFILL_INTERVAL_MSEC
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_BUCKET_SIZE
import org.testng.annotations.Test
import rx.functions.Action1

class AzureDeferralSequenceTimeManagerImplTest : BaseTestCase() {
    @Test
    fun shouldGenerateElement() {
        // Given
        setInternalProperty(TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_BUCKET_SIZE, "1")
        setInternalProperty(TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_BUCKET_REFILL_INTERVAL_MSEC, "10000")
        val instance = createInstance()

        val action = mockk<Action1<Unit>>(relaxed = true)

        // When
        val subscription = instance.getDeferralSequence("TestId 2").subscribe(action)

        // Then
        verify(exactly = 1) { action.call(Unit) }

        subscription.unsubscribe()
    }

    @Test
    fun shouldNotGenerateElementWhenBucketIsExausted() {
        // Given
        setInternalProperty(TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_BUCKET_SIZE, "1")
        setInternalProperty(TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_BUCKET_REFILL_INTERVAL_MSEC, "10000")
        val instance = createInstance()

        instance.getDeferralSequence("TestId").toBlocking().single()

        val action = mockk<Action1<Unit>>(relaxed = true)

        // When
        val subscription = instance.getDeferralSequence("TestId 2").subscribe(action)

        // Then
        verify(exactly = 0, timeout = 500) { action.call(Unit) }

        subscription.unsubscribe()
    }

    @Test
    fun shouldGenerateElementAfterBucketRefills() {
        // Given
        setInternalProperty(TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_BUCKET_SIZE, "1")
        setInternalProperty(TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_BUCKET_REFILL_INTERVAL_MSEC, "100000")
        val instance = createInstance()

        instance.getDeferralSequence("TestId").toBlocking().single()

        val action = mockk<Action1<Unit>>(relaxed = true)

        setInternalProperty(TEAMCITY_CLOUDS_AZURE_THROTTLER_TIMEMANAGER_BUCKET_REFILL_INTERVAL_MSEC, "100")
        val subscription = instance.getDeferralSequence("TestId 2").subscribe {
            action.call(Unit)
        }

        // When
        instance.refill()

        // Then
        verify(exactly = 1, timeout = 200) { action.call(Unit) }

        subscription.unsubscribe()
    }

    fun createInstance() = AzureDeferralSequenceTimeManagerImpl()
}
