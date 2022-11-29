/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm.utils

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.util.io.StreamUtil
import com.microsoft.aad.adal4j.AuthenticationException
import jetbrains.buildServer.clouds.azure.arm.AzureCloudDeployTarget
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageType
import jetbrains.buildServer.util.ExceptionUtil
import jetbrains.buildServer.util.StringUtil
import java.io.IOException

/**
 * Utilities.
 */
object AzureUtils {
    private val INVALID_TENANT = Regex("AADSTS90002: No service namespace named '([\\w-]+)' was found in the data store\\.")
    private val ENVIRONMENT_VARIABLE_REGEX = Regex("^([a-z_][a-z0-9_]*?)=.*?\$", RegexOption.IGNORE_CASE)
    private val CUSTOM_TAG_REGEX = Regex("^([^<>%&\\\\?/]*?)=.*?\$", RegexOption.IGNORE_CASE)

    internal val mapper = ObjectMapper()

    fun getResourceAsString(name: String): String {
        val stream = AzureUtils::class.java.getResourceAsStream(name) ?: return ""

        return try {
            StreamUtil.readText(stream)
        } catch (e: IOException) {
            ""
        }
    }

    fun customEnvironmentVariableSyntaxIsValid(envVar: String): Boolean {
        return envVar.matches(ENVIRONMENT_VARIABLE_REGEX)
    }

    fun customTagSyntaxIsValid(tag: String): Boolean {
        return tag.matches(CUSTOM_TAG_REGEX)
    }

    fun getExceptionDetails(e: com.microsoft.azure.CloudException): String {
        e.body()?.let { error ->
            return error.message() + " " + error.details().joinToString("\n", transform = {
                deserializeAzureError(it.message()) + " (${it.code()})"
            })
        }
        return e.message ?: ""
    }

    internal fun deserializeAzureError(json: String) = try {
        mapper.readValue(json, CloudErrorDetails::class.java)?.error?.let {
            "${it.message} (${it.code})"
        } ?: json
    } catch (e: JsonProcessingException) {
        json
    }

    fun getAuthenticationErrorMessage(e: Throwable): String {
        val authException = ExceptionUtil.getCause(e, AuthenticationException::class.java)
        return if (authException != null) {
            deserializeAuthError(authException.message ?: "")
        } else {
            e.message ?: ""
        }
    }

    internal fun deserializeAuthError(json: String) = try {
        mapper.readValue(json, CloudAuthError::class.java)?.error_description?.let {
            val error = StringUtil.splitByLines(it).firstOrNull() ?: it
            INVALID_TENANT.matchEntire(error)?.let { matchResult ->
                val (id) = matchResult.destructured
                "Application with identifier '$id' was not found in specified tenant or environment."
            } ?: error
        } ?: json
    } catch (e: JsonProcessingException) {
        json
    }

}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CloudErrorDetails(val error: CloudError? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CloudError(val code: String? = null,
                              val message: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CloudAuthError(val error: String? = null,
                                  val error_description: String? = null)

fun AzureCloudImageDetails.isVmInstance(): Boolean {
    return deployTarget == AzureCloudDeployTarget.Instance || type != AzureCloudImageType.Container
}
