/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm.types

import jetbrains.buildServer.TeamCityAsserts
import org.testng.Assert
import org.testng.annotations.Test

/**
 * @author Dmitrii Bogdanov
 */
@Test
class AzureContainerHandlerTest {
    fun testParseEnvironmentVariablesWithInvalidCharacters() {
        TeamCityAsserts.assertEmpty(AzureContainerHandler.parseEnvironmentVariables("`=a\n7V_1=b\nno_equals"))
    }

    fun testParseMalformedStringReturnsEmptyCollection() {
        TeamCityAsserts.assertEmpty(AzureContainerHandler.parseEnvironmentVariables(""))
        TeamCityAsserts.assertEmpty(AzureContainerHandler.parseEnvironmentVariables(null))
        TeamCityAsserts.assertEmpty(AzureContainerHandler.parseEnvironmentVariables("\n "))
        TeamCityAsserts.assertEmpty(AzureContainerHandler.parseEnvironmentVariables("=VAR"))
        TeamCityAsserts.assertEmpty(AzureContainerHandler.parseEnvironmentVariables("VAR"))
    }

    fun testParseWellFormedStringReturnsCorrectValues() {
        Assert.assertEquals(AzureContainerHandler.parseEnvironmentVariables("VAR1=VALUE1"), listOf(Pair("VAR1", "VALUE1")))
        Assert.assertEquals(AzureContainerHandler.parseEnvironmentVariables("VAR1="), listOf(Pair("VAR1", "")))
    }

    fun testParsePartiallyMalformedStringSkipsMalformedValues() {
        Assert.assertEquals(AzureContainerHandler.parseEnvironmentVariables("VAR1=VALUE1\n \nVAR2\nVAR3=VALUE3\n ="), listOf(Pair("VAR1", "VALUE1"), Pair("VAR3", "VALUE3")))
    }
}
