package jetbrains.buildServer.clouds.azure.arm.utils

import com.fasterxml.jackson.core.JsonParser
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
class ArmTemplateBuilder(template: String, private val disableTemplateModification: Boolean = false) {

    private val mapper = ObjectMapper()
    private var root: ObjectNode
    private val parameters = linkedMapOf<String, JsonValue>()

    init {
        mapper.enable(JsonParser.Feature.ALLOW_COMMENTS)

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
        val resources = root["resources"] as ArrayNode
        val resource = resources.filterIsInstance<ObjectNode>()
                .first { it["name"].asText() == resourceName }
        val element = (resource["tags"] as? ObjectNode) ?: resource.putObject("tags")
        element.apply {
            for ((key, value) in tags) {
                this.put(key, value)
            }
        }
        return this
    }

    fun setVMTags(tags: Map<String, String>): ArmTemplateBuilder {
        if (!disableTemplateModification) {
            return setTags("[parameters('vmName')]", tags)
        }
        tags.entries.forEach { (name, value) -> setParameterValue(name, value) }
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
        if (!disableTemplateModification) {
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
        return setParameterValue("customData", customData)
    }

    fun setParameterValue(name: String, value: String): ArmTemplateBuilder {
        parameters[name] = JsonValue(value)
        return this
    }

    fun appendInnerTemplate(innerTemplate: String): ArmTemplateBuilder {
        (root["resources"] as ArrayNode).apply {
            this.addPOJO(mapper.readTree(innerTemplate))
        }
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

    fun addContainerNetwork(): ArmTemplateBuilder {
        addParameter("networkId", "String", "Virtual Network name for the container.")
        addParameter("subnetName", "String", "Sub network name for the container.")

        (root["variables"] as ObjectNode).apply {
            this.put("netProfileName", "[concat(parameters('containerName'), '-net-profile')]")
            this.put("netConfigName", "[concat(parameters('containerName'), '-net-config')]")
            this.put("netIPConfigName", "[concat(parameters('containerName'), '-net-ip-config')]")
            this.put("subnetRef", "[concat(parameters('networkId'), '/subnets/', parameters('subnetName'))]")
        }

        (root["resources"] as ArrayNode).apply {
            this.addPOJO(object {
                val apiVersion = "2019-11-01"
                val type = "Microsoft.Network/networkProfiles"
                val name = "[variables('netProfileName')]"
                val location = "[variables('location')]"
                val properties = object {
                    val containerNetworkInterfaceConfigurations = arrayOf(
                            object {
                                val name = "[variables('netConfigName')]"
                                val properties = object {
                                    val ipConfigurations = arrayOf(
                                            object {
                                                val name = "[variables('netIPConfigName')]"
                                                val properties = object {
                                                    val subnet = object {
                                                        val id = "[variables('subnetRef')]"
                                                    }
                                                }
                                            })
                                }
                            }
                    )
                }
            })
        }

        val container = (root["resources"].filterIsInstance<ObjectNode>().first { it["type"].asText() == "Microsoft.ContainerInstance/containerGroups" })
        container
                .putArray("dependsOn")
                .add("[resourceId('Microsoft.Network/networkProfiles', variables('netProfileName'))]")

        val containerNetworkProfileRef = container["properties"] as ObjectNode
        containerNetworkProfileRef
                .putObject("networkProfile")
                .put("id", "[resourceId('Microsoft.Network/networkProfiles', variables('netProfileName'))]")

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

    @Suppress("unused", "MayBeConstant")
    fun enableAcceleratedNerworking(): ArmTemplateBuilder {
        val properties = getPropertiesOfResource("type", "Microsoft.Network/networkInterfaces")
        properties.put("enableAcceleratedNetworking", true)

        return this
    }

    fun serializeParameters(): String {
        return try {
            mapper.writeValueAsString(parameters)
        } catch (e: JsonProcessingException) {
            StringUtil.EMPTY
        }
    }

    private fun getFirstResourceOfType(type: String): ObjectNode {
        val resources = root["resources"] as ArrayNode
        val groups = resources.filterIsInstance<ObjectNode>().first { it["type"].asText() == type }
        return groups
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

    fun setupSpotInstance(enableSpotPrice: Boolean?, spotPrice: Int?): ArmTemplateBuilder {
        val properties = getPropertiesOfResource("type", "Microsoft.Compute/virtualMachines")
        properties.put("priority", "Spot")
        properties.put("evictionPolicy", "Deallocate")

        val billingProfile = properties.putObject("billingProfile")
        if (enableSpotPrice == true && spotPrice != null) {
            billingProfile.put("maxPrice", spotPrice / PRICE_DIVIDER)
        } else {
            billingProfile.put("maxPrice", -1)
        }

        return this
    }

    fun setupIdentity(userAssignedIdentity: String?, enableSystemAssignedIdentity: Boolean?): ArmTemplateBuilder {
        val resource = getFirstResourceOfType("Microsoft.Compute/virtualMachines")
        
        val hasUserAssigned = !userAssignedIdentity.isNullOrEmpty();
        val hasSystemAssigned = enableSystemAssignedIdentity == true;

        if (hasUserAssigned || hasSystemAssigned) {
            val identity = resource.putObject("identity")

            val fullType = if (hasUserAssigned && hasSystemAssigned) "SystemAssigned, UserAssigned" else if (hasSystemAssigned) "SystemAssigned" else "UserAssigned"
            identity.put("type", fullType)

            if (hasUserAssigned) {
                identity.putObject("userAssignedIdentities").putObject(userAssignedIdentity)
            }
        }

        return this
    }

    companion object {
        private val LOG = Logger.getInstance(ArmTemplateBuilder::class.java.name)
        private val PRICE_DIVIDER = 100000F
    }
}
