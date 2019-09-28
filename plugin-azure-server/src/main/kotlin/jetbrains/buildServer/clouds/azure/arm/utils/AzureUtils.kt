/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.util.io.StreamUtil
import com.microsoft.aad.adal4j.AuthenticationException
import jetbrains.buildServer.clouds.CloudException
import jetbrains.buildServer.clouds.azure.arm.AzureCloudDeployTarget
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageType
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import jetbrains.buildServer.util.ExceptionUtil
import jetbrains.buildServer.util.StringUtil
import java.io.IOException

/**
 * Utilities.
 */
object AzureUtils {
    private val INVALID_TENANT = Regex("AADSTS90002: No service namespace named '([\\w-]+)' was found in the data store\\.")
    private val mapper = ObjectMapper()

    fun getResourceAsString(name: String): String {
        val stream = AzureUtils::class.java.getResourceAsStream(name) ?: return ""

        return try {
            StreamUtil.readText(stream)
        } catch (e: IOException) {
            ""
        }
    }

    fun checkTemplate(template: String) {
        val root = try {
            val reader = mapper.reader()
            reader.readTree(template) as ObjectNode
        } catch (e: Exception) {
            throw CheckedCloudException("Invalid JSON template", e)
        }

        try {
            root["parameters"]["vmName"] as ObjectNode
        } catch (e: Exception) {
            throw CheckedCloudException("No 'vmName' parameter", e)
        }

        try {
            val resources = root["resources"] as ArrayNode
            resources.filterIsInstance<ObjectNode>()
                    .first {
                        it["type"].asText() == "Microsoft.Compute/virtualMachines" &&
                                it["name"].asText() == "[parameters('vmName')]"
                    }
        } catch (e: Exception) {
            throw CheckedCloudException("No virtual machine resource with name set to 'vmName' parameter", e)
        }
    }

    fun getExceptionDetails(e: com.microsoft.azure.CloudException): String {
        e.body()?.let {
            return it.message() + " " + it.details().joinToString("\n", transform = {
                AzureUtils.deserializeAzureError(it.message()) + " (${it.code()})"
            })
        }
        return e.message ?: ""
    }

    internal fun deserializeAzureError(json: String) = try {
        mapper.readValue<CloudErrorDetails>(json, CloudErrorDetails::class.java)?.error?.let {
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
        mapper.readValue<CloudAuthError>(json, CloudAuthError::class.java)?.error_description?.let {
            val error = StringUtil.splitByLines(it).firstOrNull() ?: it
            INVALID_TENANT.matchEntire(error)?.let {
                val (id) = it.destructured
                "Application with identifier '$id' was not found in specified tenant or environment."
            } ?: error
        } ?: json
    } catch (e: JsonProcessingException) {
        json
    }

    internal fun getResourceGroup(details: AzureCloudImageDetails, instanceName: String): String {
        return when (details.target) {
            AzureCloudDeployTarget.NewGroup -> instanceName
            AzureCloudDeployTarget.SpecificGroup -> details.groupId!!
            AzureCloudDeployTarget.Instance -> {
                RESOURCE_GROUP_PATTERN.find(details.instanceId!!)?.let {
                    val (groupId) = it.destructured
                    return groupId
                }
                throw CloudException("Invalid instance ID ${details.instanceId}")
            }
        }
    }

    private val RESOURCE_GROUP_PATTERN = Regex("resourceGroups/([^/]+)/providers/")
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
