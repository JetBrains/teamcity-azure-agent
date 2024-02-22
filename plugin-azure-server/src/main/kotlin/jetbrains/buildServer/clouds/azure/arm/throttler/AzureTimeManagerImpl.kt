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

package jetbrains.buildServer.clouds.azure.arm.throttler

import com.intellij.openapi.diagnostic.Logger
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference

class AzureTimeManagerImpl : AzureTimeManager {
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

    companion object {
        private val LOG = Logger.getInstance(AzureTimeManagerImpl::class.java)
    }
}
