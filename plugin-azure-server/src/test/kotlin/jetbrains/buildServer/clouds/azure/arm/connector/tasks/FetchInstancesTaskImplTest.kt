/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
    fun shouldGetFromCaheReturnNullWhenCacheIsNotFilled() {
        // Given
        val instance = createInstance()

        // When
        val result = instance.getFromCache(FetchInstancesTaskParameter(null, null, emptyArray()))

        // Then
        Assert.assertNull(result)
    }

    private fun createInstance(): FetchInstancesTaskImpl {
        return FetchInstancesTaskImpl(myNotifications)
    }
}
