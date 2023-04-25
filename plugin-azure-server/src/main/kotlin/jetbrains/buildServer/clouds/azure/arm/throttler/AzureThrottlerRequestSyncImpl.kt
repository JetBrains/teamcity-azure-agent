package jetbrains.buildServer.clouds.azure.arm.throttler

import jetbrains.buildServer.serverSide.TeamCityProperties
import java.time.ZonedDateTime
import java.util.concurrent.locks.ReentrantLock

class AzureThrottlerRequestSyncImpl : AzureThrottlerRequestSync {
    private val sync = ReentrantLock(true)

    @Volatile
    private var lastLockTakenAt = 0L

    override fun waitForNextTimeSlot() {
        if (TeamCityProperties.getBoolean(TEAMCITY_CLOUDS_AZURE_THROTTLER_GLOBAL_SYNC_DISABLE)) return

        sync.lock()
        try {
            waitForTimeSpin(lastLockTakenAt + DELAY_IN_MILLI)
            lastLockTakenAt = getCurrentMilli()
        }
        finally {
            sync.unlock()
        }
    }

    private fun waitForTimeSpin(time: Long) {
        while(getCurrentMilli() <= time) {
            Thread.sleep(CHECK_DELAY_IN_MILLI)
        }
    }

    fun getCurrentMilli() : Long = ZonedDateTime.now().toInstant().toEpochMilli()

    companion object {
        private val CHECK_DELAY_IN_MILLI = 100L
        private val DELAY_IN_MILLI = 400L
    }
}
