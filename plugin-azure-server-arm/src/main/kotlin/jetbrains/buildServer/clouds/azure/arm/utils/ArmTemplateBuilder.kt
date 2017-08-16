package jetbrains.buildServer.clouds.azure.arm.utils

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.azure.arm.connector.models.JsonValue
import jetbrains.buildServer.util.StringUtil

/**
 * Allows to customize ARM template.
 */
class ArmTemplateBuilder(template: String) {

    private val LOG = Logger.getInstance(ArmTemplateBuilder::class.java.name)
    private val mapper = ObjectMapper()
    private val root: ObjectNode
    private val parameters = linkedMapOf<String, JsonValue>()

    init {
        val reader = mapper.reader()
        root = reader.readTree(template) as ObjectNode
    }

    @Suppress("unused")
    fun addParameter(name: String, type: String, description: String): ArmTemplateBuilder {
        val parameters = root["parameters"] as ObjectNode
        parameters.putPOJO(name, object : Any() {
            val type = type
            val metadata = object : Any() {
                val description = description
            }
        })
        return this
    }

    fun setTags(tags: Map<String, String>): ArmTemplateBuilder {
        val resources = root["resources"] as ArrayNode
        val machine = resources.filterIsInstance<ObjectNode>()
                .first { it["name"].asText() == "[parameters('vmName')]" }
        val element = (machine["tags"] as? ObjectNode) ?: machine.putObject("tags")
        element.apply {
            for ((key, value) in tags) {
                this.put(key, value)
            }
        }
        return this
    }

    @Suppress("unused")
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

    @Suppress("unused")
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

    @Suppress("unused")
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

    fun setCustomData(customData: String): ArmTemplateBuilder {
        (root["resources"] as ArrayNode).apply {
            this.filterIsInstance<ObjectNode>()
                    .first { it["name"].asText() == "[parameters('vmName')]" }
                    .apply {
                        val properties = (this["properties"] as? ObjectNode) ?: this.putObject("properties")
                        val osProfile = (properties["osProfile"] as? ObjectNode) ?: properties.putObject("osProfile")
                        osProfile.put("customData", customData)
                    }
        }
        return this
    }

    fun setParameterValue(name: String, value: String): ArmTemplateBuilder {
        parameters[name] = JsonValue(value)
        return this
    }

    fun serializeParameters(): String {
        return try {
            mapper.writeValueAsString(parameters)
        } catch (e: JsonProcessingException) {
            StringUtil.EMPTY
        }
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
}
