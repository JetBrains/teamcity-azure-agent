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
 */package jetbrains.buildServer.clouds.azure.arm.throttler

import rx.Observable

class AzureTimeManagerImpl(
    private val azureTicketTimeManager: AzureTicketTimeManager,
    private val azureDefettalSequenceTimeManager: AzureDefettalSequenceTimeManager
) : AzureTimeManager {
    override fun getTicket(corellationId: String): AzureOperationTicket =
        azureTicketTimeManager.getTicket(corellationId)

    override fun getDeferralSequence(corellationId: String): Observable<Unit> =
        azureDefettalSequenceTimeManager.getDeferralSequence(corellationId)

    override fun dispose() =
        azureDefettalSequenceTimeManager.dispose()
}

