package jetbrains.buildServer.clouds.azure

import com.google.gson.GsonBuilder
import com.google.gson.annotations.Since
import jetbrains.buildServer.agent.BuildAgentConfigurationEx
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.util.FileUtil
import org.apache.commons.codec.binary.Base64
import org.jmock.Expectations
import org.jmock.Mockery
import org.jmock.lib.concurrent.Synchroniser
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.io.File


@Test
class AzureMetadataReaderTest {
    fun testSerializeMetadataForConfiguration() {
        val cloudInstanceUserData = CloudInstanceUserData(
            "Agent name",
            "Auth token",
            "Server URL",
            null,
            "Profile ID",
            "Profile description",
            emptyMap()
        )
        val userData = AzureUserData.serializeV1(cloudInstanceUserData.serialize(), "Sample")
        val json = FileUtil
            .readText(File("src/test/resources/metadata2.json"))
            .replace("@userData", userData)

        val metadata = AzureMetadata.deserializeInstanceMetadata(json)
        val configParams = metadata?.compute?.asMap()

        assert(configParams?.get("tagsList") == null)
    }

    @Test(dataProvider = "boolProvider")
    fun testHasMarkerTags(hasMarkerTags: Boolean) {
        val cloudInstanceUserData = CloudInstanceUserData(
            "Agent name",
            "Auth token",
            "Server URL",
            null,
            "Profile ID",
            "Profile description",
            emptyMap()
        )
        val userData = AzureUserData.serializeV1(cloudInstanceUserData.serialize(), "Sample")
        val json = FileUtil
            .readText(File("src/test/resources/metadata3.json"))
            .replace("@userData", userData)
            .let {
                if (hasMarkerTags) {
                    it
                        .replace("@tags", "teamcity-data-hash:1234567890")
                        .replace("@tagList", "[{\"name\":\"teamcity-data-hash\",\"value\":\"1234567890\"}]")
                } else {
                    it
                        .replace("@tags", "")
                        .replace("@tagList", "[]")
                }
            }

        val metadata = AzureMetadata.deserializeInstanceMetadata(json)

        val data = AzureMetadataReaderData(metadata)

        assert(data.hasMarkerTags == hasMarkerTags)
    }

    @Test(dataProvider = "boolProvider")
    fun testHasNewIntegrationMarkerTags(hasNewIntegrationMarkerTags: Boolean) {
        val cloudInstanceUserData = CloudInstanceUserData(
            "Agent name",
            "Auth token",
            "Server URL",
            null,
            "Profile ID",
            "Profile description",
            emptyMap()
        )
        val userData = AzureUserData.serializeV1(cloudInstanceUserData.serialize(), "Sample")
        val json = FileUtil
            .readText(File("src/test/resources/metadata3.json"))
            .replace("@userData", userData)
            .let {
                if (hasNewIntegrationMarkerTags) {
                    it
                        .replace("@tags", "teamcity-instance-id:1234567890")
                        .replace("@tagList", "[{\"name\":\"teamcity-instance-id\",\"value\":\"1234567890\"}]")
                } else {
                    it
                        .replace("@tags", "")
                        .replace("@tagList", "[]")
                }
            }

        val metadata = AzureMetadata.deserializeInstanceMetadata(json)

        val data = AzureMetadataReaderData(metadata)

        assert(data.hasNewIntegrationMarkerTags == hasNewIntegrationMarkerTags)
    }

    fun testUserData() {
        val cloudInstanceUserData = CloudInstanceUserData(
            "Agent name",
            "Auth token",
            "Server URL",
            null,
            "Profile ID",
            "Profile description",
            emptyMap()
        )
        val userData = AzureUserData.serializeV1(cloudInstanceUserData.serialize(), "Sample")
        val json = FileUtil
            .readText(File("src/test/resources/metadata.json"))
            .replace("@userData", userData)

        val metadata = AzureMetadata.deserializeInstanceMetadata(json)
        AzureMetadataReaderData(metadata).getUserData().getOrThrow().let {
            assert(it.version == "1.0")
            assert(it.pluginCode == "arm")
            assert(it.instanceId == "Sample")
            assert(CloudInstanceUserData.deserialize(it.cloudInstanceUserData) == cloudInstanceUserData)
        }
    }

    fun testUserDataHasPluginCodeForAnotherPlugin() {
        val cloudInstanceUserData = CloudInstanceUserData(
            "Agent name",
            "Auth token",
            "Server URL",
            null,
            "Profile ID",
            "Profile description",
            emptyMap()
        )
        val userData = TestAzureUserData.serialize(cloudInstanceUserData.serialize(), "Sample", "another-plugin")
        val json = FileUtil
            .readText(File("src/test/resources/metadata.json"))
            .replace("@userData", userData)

        val metadata = AzureMetadata.deserializeInstanceMetadata(json)
        AzureMetadataReaderData(metadata)
            .getUserData()
            .onSuccess { assert(false) }
            .onFailure { it ->
                assert(it is UserDataIncorrectCodeException)
                assert(it.message == "Unsupported plugin code (another-plugin) in Azure userData. Current plugin code is arm.")
            }
    }

    fun testUserDataIsBroken() {
        val cloudInstanceUserData = CloudInstanceUserData(
            "Agent name",
            "Auth token",
            "Server URL",
            null,
            "Profile ID",
            "Profile description",
            emptyMap()
        )
        val userData = "{}"
        val json = FileUtil
            .readText(File("src/test/resources/metadata.json"))
            .replace("@userData", userData)

        val metadata = AzureMetadata.deserializeInstanceMetadata(json)
        AzureMetadataReaderData(metadata)
            .getUserData()
            .onSuccess { assert(false) }
            .onFailure { it ->
                assert(it is UserDataDeserializationException)
                assert(it.message == "Could not parse Azure userData.")
            }
    }

    fun testUserDataIsEmpty() {
        val cloudInstanceUserData = CloudInstanceUserData(
            "Agent name",
            "Auth token",
            "Server URL",
            null,
            "Profile ID",
            "Profile description",
            emptyMap()
        )
        val userData = ""
        val json = FileUtil
            .readText(File("src/test/resources/metadata.json"))
            .replace("@userData", userData)

        val metadata = AzureMetadata.deserializeInstanceMetadata(json)
        AzureMetadataReaderData(metadata)
            .getUserData()
            .onSuccess { assert(false) }
            .onFailure { it ->
                assert(it is UserDataIsEmptyException)
                assert(it.message == "No Azure userData provided")
            }
    }

    fun testReadMetadata() {
        val m = Mockery()
        m.setThreadingPolicy(Synchroniser())
        val agentConfiguration = m.mock(BuildAgentConfigurationEx::class.java)
        val cloudInstanceUserData = CloudInstanceUserData(
            "Agent name",
            "Auth token",
            "Server URL",
            null,
            "Profile ID",
            "Profile description",
            emptyMap()
        )
        val userData = AzureUserData.serializeV1(cloudInstanceUserData.serialize(), "Sample")
        val json = FileUtil
            .readText(File("src/test/resources/metadata.json"))
            .replace("@userData", userData)

        m.checking(object : Expectations() {
            init {
                one(agentConfiguration).name
                will(returnValue(""))
                one(agentConfiguration).name = "IMDSSample"
                one(agentConfiguration).addSystemProperty("azure.instance.name", "IMDSSample")
                one(agentConfiguration).addSystemProperty("azure.instance.offer", "UbuntuServer")
                one(agentConfiguration).addSystemProperty("azure.instance.osType", "Linux")
                one(agentConfiguration).addSystemProperty("azure.instance.sku", "16.04.0-LTS")
                one(agentConfiguration).addSystemProperty("azure.instance.version", "16.04.201610200")
                one(agentConfiguration).addSystemProperty("azure.instance.vmId", "5d33a910-a7a0-4443-9f01-6a807801b29b")
                one(agentConfiguration).addSystemProperty("azure.instance.vmSize", "Standard_A1")
                one(agentConfiguration).addSystemProperty("azure.instance.userData", userData)
                one(agentConfiguration).addAlternativeAgentAddress("X.X.X.X")
                one(agentConfiguration).addSystemProperty("ec2.public-hostname", "X.X.X.X")
            }
        })

        val metadata = AzureMetadata.deserializeInstanceMetadata(json)
        AzureMetadataReaderData(metadata).updateConfiguration(agentConfiguration)

        m.assertIsSatisfied()
    }

    fun testReadMetadata2() {
        val m = Mockery()
        m.setThreadingPolicy(Synchroniser())
        val agentConfiguration = m.mock(BuildAgentConfigurationEx::class.java)
        val cloudInstanceUserData = CloudInstanceUserData(
            "Agent name",
            "Auth token",
            "Server URL",
            null,
            "Profile ID",
            "Profile description",
            emptyMap()
        )
        val userData = AzureUserData.serializeV1(cloudInstanceUserData.serialize(), "Sample")
        val json = FileUtil
            .readText(File("src/test/resources/metadata2.json"))
            .replace("@userData", userData)

        m.checking(object : Expectations() {
            init {
                one(agentConfiguration).name
                will(returnValue(""))
                one(agentConfiguration).name = "examplevmname"
                one(agentConfiguration).addSystemProperty("azure.instance.priority", "Regular")
                one(agentConfiguration).addSystemProperty("azure.instance.name", "examplevmname")
                one(agentConfiguration).addSystemProperty("azure.instance.licenseType", "Windows_Client")
                one(agentConfiguration).addSystemProperty("azure.instance.offer", "WindowsServer")
                one(agentConfiguration).addSystemProperty("azure.instance.osType", "Windows")
                one(agentConfiguration).addSystemProperty("azure.instance.sku", "2019-Datacenter")
                one(agentConfiguration).addSystemProperty("azure.instance.version", "15.05.22")
                one(agentConfiguration).addSystemProperty("azure.instance.vmId", "02aab8a4-74ef-476e-8182-f6d2ba4166a6")
                one(agentConfiguration).addSystemProperty("azure.instance.vmSize", "Standard_A3")
                one(agentConfiguration).addSystemProperty("azure.instance.vmScaleSetName", "crpteste9vflji9")
                one(agentConfiguration).addSystemProperty("azure.instance.tags", "baz:bash;foo:bar")
                one(agentConfiguration).addSystemProperty("azure.instance.userData", userData)
                one(agentConfiguration).addAlternativeAgentAddress("1.2.3.4")
                one(agentConfiguration).addSystemProperty("ec2.public-hostname", "1.2.3.4")
            }
        })

        val metadata = AzureMetadata.deserializeInstanceMetadata(json)
        AzureMetadataReaderData(metadata).updateConfiguration(agentConfiguration)

        m.assertIsSatisfied()
    }

    @DataProvider(name = "boolProvider")
    fun boolProvider(): Array<Array<Any>> {
        return arrayOf(
            arrayOf(false),
            arrayOf(true)
        )
    }

    class TestAzureUserData {
        @Since(1.0)
        val version: String

        @Since(1.0)
        val cloudInstanceUserData: String

        @Since(1.0)
        val instanceId: String

        @Since(1.0)
        val pluginCode: String

        private constructor(cloudInstanceUserData: String, instanceId: String, code: String) {
            version = "1.0"
            this.cloudInstanceUserData = cloudInstanceUserData
            this.instanceId = instanceId
            pluginCode = code
        }

        fun serialize(): String =
            Base64.encodeBase64String(gson.toJson(this).toByteArray(Charsets.UTF_8))

        companion object {
            private val gson = GsonBuilder().setVersion(1.0).create()

            fun serialize(customData: String, instanceId: String, pluginCode: String): String =
                TestAzureUserData(customData, instanceId, pluginCode).serialize()

            fun deserialize(data: String): TestAzureUserData =
                gson.fromJson(String(Base64.decodeBase64(data)), TestAzureUserData::class.java)
        }
    }
}
