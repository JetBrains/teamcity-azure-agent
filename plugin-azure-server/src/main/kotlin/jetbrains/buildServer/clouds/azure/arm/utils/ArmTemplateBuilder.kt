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

package jetbrains.buildServer.clouds.azure.arm.utils

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.connector.models.JsonValue
import jetbrains.buildServer.util.StringUtil
import java.util.*

/**
 * Allows to customize ARM template.
 */
class ArmTemplateBuilder(template: String, tagsAsParameters: Boolean = false) {

    private val mapper = ObjectMapper()
    private var root: ObjectNode
    private val parameters = linkedMapOf<String, JsonValue>()
    private val tagsAsParameters = tagsAsParameters

    init {
        val reader = mapper.reader()
        root = reader.readTree(template) as ObjectNode
    }

    @Suppress("unused")
    fun addParameter(name: String, type: String, description: String): ArmTemplateBuilder {
        val parameters = (root["parameters"] as? ObjectNode) ?: root.putObject("parameters")
        parameters.putPOJO(name, object : Any() {
            val type = type
            val metadata = object : Any() {
                val description = description
            }
        })
        return this
    }

    fun setTags(resourceName: String, tags: Map<String, String>): ArmTemplateBuilder {
        if (tagsAsParameters)
        {
            // Moved to parameters because of incremental ARM templates
            for ((key, value) in tags) {
                this.setParameterValue(key, value);
            }
        }
        else
        {
            val resources = root["resources"] as ArrayNode
            val resource = resources.filterIsInstance<ObjectNode>()
                    .first { it["name"].asText() == resourceName }
            val element = (resource["tags"] as? ObjectNode) ?: resource.putObject("tags")
            element.apply {
                for ((key, value) in tags) {
                    this.put(key, value)
                }
            }
        }
        return this
    }

    @Suppress("unused", "MayBeConstant")
    fun setPublicIp(): ArmTemplateBuilder {
        (root["variables"] as ObjectNode).apply {
            this.put("pipName", "[concat(parameters('vmName'), '-pip')]")
        }

        (root["resources"] as ArrayNode).apply {
            this.filterIsInstance<ObjectNode>()
                    .first { it["name"].asText() == "[variables('nicName')]" }
                    .apply {
                        this.putPOJO("dependsOn",
                                listOf("[concat('Microsoft.Network/publicIPAddresses/', variables('pipName'))]"))
                        (this["properties"]["ipConfigurations"][0]["properties"] as ObjectNode).apply {
                            this.putPOJO("publicIPAddress", object : Any() {
                                val id = "[resourceId('Microsoft.Network/publicIPAddresses', variables('pipName'))]"
                            })
                        }
                    }
            this.addPOJO(object : Any() {
                val apiVersion = "2016-09-01"
                val type = "Microsoft.Network/publicIPAddresses"
                val name = "[variables('pipName')]"
                val location = "[variables('location')]"
                val properties = object : Any() {
                    val publicIPAllocationMethod = "Dynamic"
                }
            })
        }

        return this
    }

    @Suppress("unused", "MayBeConstant")
    fun setVhdImage(): ArmTemplateBuilder {
        (root["resources"] as ArrayNode).apply {
            this.filterIsInstance<ObjectNode>()
                    .first { it["name"].asText() == "[parameters('vmName')]" }
                    .apply {
                        (this["properties"]["storageProfile"]["osDisk"] as ObjectNode).apply {
                            this.putPOJO("image", object : Any() {
                                val uri = "[parameters('imageUrl')]"
                            })
                            this.putPOJO("vhd", object : Any() {
                                val uri = "[concat('https://', split(parameters('imageUrl'),'/')[2], '/vhds/', parameters('vmName'), '-os.vhd')]"
                            })
                        }
                    }
        }
        return this
    }

    @Suppress("unused", "MayBeConstant")
    fun setCustomImage(): ArmTemplateBuilder {
        (root["resources"] as ArrayNode).apply {
            this.filterIsInstance<ObjectNode>()
                    .first { it["name"].asText() == "[parameters('vmName')]" }
                    .apply {
                        (this["properties"]["storageProfile"] as ObjectNode).apply {
                            this.putPOJO("imageReference", object : Any() {
                                val id = "[parameters('imageId')]"
                            })
                        }
                    }
        }
        return this
    }

    @Suppress("unused")
    fun setStorageAccountType(storageAccountType: String?): ArmTemplateBuilder {
        if (!storageAccountType.isNullOrEmpty()) {
            (root["resources"] as ArrayNode).apply {
                this.filterIsInstance<ObjectNode>()
                        .first { it["name"].asText() == "[parameters('vmName')]" }
                        .apply {
                            (this["properties"]["storageProfile"]["osDisk"] as ObjectNode).apply {
                                this.putPOJO("managedDisk", object : Any() {
                                    val storageAccountType = storageAccountType
                                })
                            }
                        }
            }
        }
        return this
    }

    fun setCustomData(customData: String): ArmTemplateBuilder {
        if (tagsAsParameters)
        {
            // Moved to parameters because of incremental ARM templates
            this.setParameterValue("customData", customData);
        }
        else
        {
            (root["resources"] as ArrayNode).apply {
            this.filterIsInstance<ObjectNode>()
                    .first { it["name"].asText() == "[parameters('vmName')]" }
                    .apply {
                        val properties = (this["properties"] as? ObjectNode) ?: this.putObject("properties")
                        val osProfile = (properties["osProfile"] as? ObjectNode) ?: properties.putObject("osProfile")
                        osProfile.put("customData", customData)
                    }
            }
        }
        return this
    }

    fun setParameterValue(name: String, value: String): ArmTemplateBuilder {
        parameters[name] = JsonValue(value)
        return this
    }

    @Suppress("unused")
    fun addContainer(name: String, customEnvironmentVariables: List<Pair<String, String>> = Collections.emptyList()): ArmTemplateBuilder {
        val properties = getPropertiesOfResource("type", "Microsoft.ContainerInstance/containerGroups")
        val containers = (properties["containers"] as? ArrayNode) ?: properties.putArray("containers")
        val environmentVariables = mutableListOf(
                object {
                    val name = "SERVER_URL"
                    val value = "[parameters('teamcityUrl')]"
                },
                object {
                    val name = "AGENT_NAME"
                    val value = name
                }
        )
        environmentVariables.addAll(customEnvironmentVariables
                .filter { it.first != "SERVER_URL" && it.second != "SERVER_URL" }
                .map {
                    object {
                        val name = it.first
                        val value = it.second
                    }
                })
        containers.addPOJO(object {
            val name = name
            val properties = object {
                val image = "[parameters('imageId')]"
                val environmentVariables = environmentVariables.toList()
                val resources = object {
                    val requests = object {
                        val cpu = "[parameters('numberCores')]"
                        val memoryInGb = "[parameters('memory')]"
                    }
                }
            }
        })

        reloadTemplate()

        return this
    }

    @Suppress("unused", "MayBeConstant")
    fun addContainerCredentials(server: String, username: String, password: String): ArmTemplateBuilder {
        val properties = getPropertiesOfResource("type", "Microsoft.ContainerInstance/containerGroups")
        val credentials = (properties["imageRegistryCredentials"] as? ArrayNode) ?: properties.putArray("imageRegistryCredentials")

        credentials.addPOJO(object {
            val server = server
            val username = username
            val password = password
        })

        reloadTemplate()

        return this
    }

    @Suppress("unused", "MayBeConstant")
    fun addContainerVolumes(resourceName: String, name: String): ArmTemplateBuilder {
        val properties = getPropertiesOfResource(resourceName)
        val containers = containersFromProperties(properties)

        (containers.firstOrNull() as? ObjectNode)?.let {
            val props = (it["properties"] as? ObjectNode) ?: it.putObject("properties")
            val volumeMounts = (props["volumeMounts"] as? ArrayNode) ?: props.putArray("volumeMounts")
            volumeMounts.addPOJO(object {
                val name = name
                val mountPath = "/var/lib/waagent/"
                val readOnly = true
            })
            volumeMounts.addPOJO(object {
                val name = "$name-plugins"
                val mountPath = "/opt/buildagent/plugins/"
            })
            volumeMounts.addPOJO(object {
                val name = "$name-logs"
                val mountPath = "/opt/buildagent/logs/"
            })
            volumeMounts.addPOJO(object {
                val name = "$name-system"
                val mountPath = "/opt/buildagent/system/.teamcity-agent/"
            })
            volumeMounts.addPOJO(object {
                val name = "$name-tools"
                val mountPath = "/opt/buildagent/tools/"
            })
        }

        val volumes = (properties["volumes"] as? ArrayNode) ?: properties.putArray("volumes")
        volumes.addPOJO(object {
            val name = name
            val azureFile = object {
                val shareName = name
                val storageAccountName = "[parameters('storageAccountName')]"
                val storageAccountKey = "[parameters('storageAccountKey')]"
            }
        })

        for (volume in AzureConstants.CONTAINER_VOLUMES) {
            volumes.addPOJO(object {
                val name = "$name-$volume"
                val azureFile = object {
                    val shareName = "$name-$volume"
                    val storageAccountName = "[parameters('storageAccountName')]"
                    val storageAccountKey = "[parameters('storageAccountKey')]"
                }
            })
        }

        return this.addParameter("storageAccountName", "String", "")
                .addParameter("storageAccountKey", "SecureString", "")
    }

    private fun containersFromProperties(properties: ObjectNode) =
            (properties["containers"] as? ArrayNode) ?: properties.putArray("containers")

    @Suppress("unused")
    fun addContainerEnvironment(resourceName: String, environment: Map<String, String>): ArmTemplateBuilder {
        val properties = getPropertiesOfResource(resourceName)
        val containers = (properties["containers"] as? ArrayNode) ?: properties.putArray("containers")

        (containers.firstOrNull() as? ObjectNode)?.let {
            val props = (it["properties"] as? ObjectNode) ?: it.putObject("properties")
            val envVars = (props["environmentVariables"] as? ArrayNode) ?: props.putArray("environmentVariables")
            environment.forEach {
                envVars.addPOJO(object {
                    val name = it.key
                    val value = it.value
                })
            }
        }

        return this
    }

    fun serializeParameters(): String {
        return try {
            mapper.writeValueAsString(parameters)
        } catch (e: JsonProcessingException) {
            StringUtil.EMPTY
        }
    }

    private fun getPropertiesOfResource(resourceName: String): ObjectNode {
        return getPropertiesOfResource("name", resourceName)
    }

    private fun getPropertiesOfResource(fieldName: String, fieldValue: String): ObjectNode {
        val resources = root["resources"] as ArrayNode
        val groups = resources.filterIsInstance<ObjectNode>().first { it[fieldName].asText() == fieldValue }
        return (groups["properties"] as? ObjectNode) ?: groups.putObject("properties")
    }

    private fun reloadTemplate() {
        val reader = mapper.reader()
        root = reader.readTree(mapper.writeValueAsString(root)) as ObjectNode
    }

    override fun toString(): String = mapper.writeValueAsString(root)

    fun logDetails() {
        if (!LOG.isDebugEnabled) return

        LOG.debug("Deployment template: \n" + toString())
        val deploymentParameters = "Deployment parameters:" +
                parameters.entries.joinToString("\n") { (key, value) ->
                    val parameter = if (key == "adminPassword") "*****" else value.value
                    " - '$key' = '$parameter'"
                }
        LOG.debug(deploymentParameters)
    }

    companion object {
        private val LOG = Logger.getInstance(ArmTemplateBuilder::class.java.name)
    }
}
