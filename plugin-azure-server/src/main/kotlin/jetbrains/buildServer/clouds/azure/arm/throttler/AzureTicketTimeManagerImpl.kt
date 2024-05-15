package jetbrains.buildServer.clouds.azure.arm.throttler

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference

class AzureTicketTimeManagerImpl : AzureTicketTimeManager {
    private val myOperationDelay = Duration.of(400, ChronoUnit.MILLIS)
    private val myNextOperationOffset = AtomicReference<LocalDateTime>(LocalDateTime.MIN)
    override fun getTicket(corellationId: String): AzureOperationTicket =
        AzureOperationTicket(corellationId, LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now(ZoneOffset.UTC), reserveNextOperationOffset())
    private fun reserveNextOperationOffset(): LocalDateTime {
        var nextSlot: LocalDateTime
        var resultSlot: LocalDateTime
        do {
            val currentDateTime = LocalDateTime.now(ZoneOffset.UTC)
            val slot = myNextOperationOffset.get()
            resultSlot = if (currentDateTime > slot) currentDateTime else slot
            nextSlot = resultSlot.plus(myOperationDelay)
        } while (!myNextOperationOffset.compareAndSet(slot, nextSlot))

        return resultSlot
    }
}
