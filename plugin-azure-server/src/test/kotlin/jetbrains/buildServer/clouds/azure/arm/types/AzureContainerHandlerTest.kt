package jetbrains.buildServer.clouds.azure.arm.types

import jetbrains.buildServer.TeamCityAsserts
import org.testng.Assert
import org.testng.annotations.Test

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
