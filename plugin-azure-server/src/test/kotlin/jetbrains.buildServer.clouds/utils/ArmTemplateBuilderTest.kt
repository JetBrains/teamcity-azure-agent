package jetbrains.buildServer.clouds.utils

import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.utils.ArmTemplateBuilder
import org.testng.Assert
import org.testng.annotations.Test

@Test
class ArmTemplateBuilderTest {

    fun testSetTemplateTags() {
        val builder = ArmTemplateBuilder("""{
        "resources": [
            {"name": "[parameters('vmName')]"},
            {"vm": "vm"}
        ]}""").setTags("[parameters('vmName')]", mapOf(AzureConstants.TAG_PROFILE to "profile"))

        Assert.assertEquals(builder.toString(),
                """{"resources":[{"name":"[parameters('vmName')]","tags":{"teamcity-profile":"profile"}},{"vm":"vm"}]}""")
    }

    fun testSetTemplateVMTags() {
        val builder = ArmTemplateBuilder("""{
        "resources": [
            {"name": "[parameters('vmName')]"},
            {"vm": "vm"}
        ]}""").setVMTags(mapOf(AzureConstants.TAG_PROFILE to "profile"))

        Assert.assertEquals(builder.toString(),
            """{"resources":[{"name":"[parameters('vmName')]","tags":{"teamcity-profile":"profile"}},{"vm":"vm"}]}""")
    }

    fun testSetTemplateVMTagsWithoutTemplateModification() {
        val builder = ArmTemplateBuilder("""{
        "resources": [
            {"name": "[parameters('vmName')]"},
            {"vm": "vm"}
        ]}""",
        true
        ).setVMTags(mapOf(AzureConstants.TAG_PROFILE to "profile"))

        Assert.assertEquals(builder.toString(),
            """{"resources":[{"name":"[parameters('vmName')]"},{"vm":"vm"}]}""")
        Assert.assertEquals(builder.serializeParameters(),
            """{"teamcity-profile":{"value":"profile"}}""")
    }

    fun testSetPublicIp() {
        val builder = ArmTemplateBuilder("""{"variables": {}, "resources": [{
      "name": "[variables('nicName')]",
      "properties": {
        "ipConfigurations": [
          {
            "properties": { }
          }
        ]
      }
    }]}""").setPublicIp()

        Assert.assertEquals(builder.toString(),
                """{"variables":{"pipName":"[concat(parameters('vmName'), '-pip')]"},"resources":[{"name":""" +
                        """"[variables('nicName')]","properties":{"ipConfigurations":[{"properties":{"publicIPAddress"""" +
                        """:{"id":"[resourceId('Microsoft.Network/publicIPAddresses', variables('pipName'))]"}}}]},""" +
                        """"dependsOn":["[concat('Microsoft.Network/publicIPAddresses/', variables('pipName'))]"]},""" +
                        """{"apiVersion":"2016-09-01","type":"Microsoft.Network/publicIPAddresses","name":""" +
                        """"[variables('pipName')]","location":"[variables('location')]","properties":{""" +
                        """"publicIPAllocationMethod":"Dynamic"}}]}""")
    }

    fun testAddParameter() {
        val builder = ArmTemplateBuilder("""{"parameters": {}}""").addParameter("name", "string", "description")

        Assert.assertEquals(builder.toString(),
                """{"parameters":{"name":{"type":"string","metadata":{"description":"description"}}}}""")
    }

    fun testSetCustomImage() {
        val builder = ArmTemplateBuilder("""{"resources": [{
      "name": "[parameters('vmName')]",
      "properties": {
        "storageProfile": {}
      }
    }]}""").setCustomImage()

        Assert.assertEquals(builder.toString(),
                """{"resources":[{"name":"[parameters('vmName')]","properties":{"storageProfile":{""" +
                        """"imageReference":{"id":"[parameters('imageId')]"}}}}]}""")
    }

    fun testSetCustomData() {
        val builder = ArmTemplateBuilder("""{"resources": [{
      "name": "[parameters('vmName')]",
      "properties": {
        "osProfile": {}
      }
    }]}""").setCustomData("test data")

        Assert.assertEquals(builder.toString(),
            """{"resources":[{"name":"[parameters('vmName')]","properties":{"osProfile":{""" +
                    """"customData":"test data"}}}]}""")
    }

    fun testSetCustomDataWithoutTemplateModification() {
        val builder = ArmTemplateBuilder("""{"resources": [{
          "name": "[parameters('vmName')]",
          "properties": {
            "osProfile": {}
          }
        }]}""",
        true).setCustomData("test data")

        Assert.assertEquals(builder.toString(),
            """{"resources":[{"name":"[parameters('vmName')]","properties":{"osProfile":{""" +
                    """}}}]}""")
        Assert.assertEquals(builder.serializeParameters(),
            """{"customData":{"value":"test data"}}""")
    }

    fun testSetVhdImage() {
        val builder = ArmTemplateBuilder("""{"resources": [{
      "name": "[parameters('vmName')]",
      "properties": {
        "storageProfile": {
          "osDisk": {}
        }
      }
    }]}""").setVhdImage()

        Assert.assertEquals(builder.toString(),
                """{"resources":[{"name":"[parameters('vmName')]","properties":{"storageProfile":{"osDisk":{""" +
                        """"image":{"uri":"[parameters('imageUrl')]"},"vhd":{"uri":"[concat('https://', """ +
                        """split(parameters('imageUrl'),'/')[2], '/vhds/', parameters('vmName'), '-os.vhd')]"}}}}}]}""")
    }

    fun testAddContainer() {
        val builder = ArmTemplateBuilder("""{"resources": [
      {
        "type": "Microsoft.ContainerInstance/containerGroups"
      }
    ]}""").addContainer("aci-1", listOf(Pair("VAR1", "VALUE1")))

        Assert.assertEquals(builder.toString(),
                """{"resources":[{"type":"Microsoft.ContainerInstance/containerGroups","properties":{"containers":[{"name":"aci-1","properties":{"image":"[parameters('imageId')]","environmentVariables":[{"name":"SERVER_URL","value":"[parameters('teamcityUrl')]"},{"name":"AGENT_NAME","value":"aci-1"},{"name":"VAR1","value":"VALUE1"}],"resources":{"requests":{"cpu":"[parameters('numberCores')]","memoryInGb":"[parameters('memory')]"}}}}]}}]}""")
    }

    fun testAddContainerVolumes() {
        val builder = ArmTemplateBuilder("""{"resources": [
      {
        "type": "Microsoft.ContainerInstance/containerGroups",
        "name": "myName",
        "properties": {
          "containers": [
            {
              "name": "myName"
            }
          ]
        }
      }
    ]}""").addContainerVolumes("myName", "aci-1")

        Assert.assertEquals(builder.toString(),
                """{"resources":[{"type":"Microsoft.ContainerInstance/containerGroups","name":"myName","properties":{"containers":[{"name":"myName","properties":{"volumeMounts":[{"name":"aci-1","mountPath":"/var/lib/waagent/","readOnly":true},{"name":"aci-1-plugins","mountPath":"/opt/buildagent/plugins/"},{"name":"aci-1-logs","mountPath":"/opt/buildagent/logs/"},{"name":"aci-1-system","mountPath":"/opt/buildagent/system/.teamcity-agent/"},{"name":"aci-1-tools","mountPath":"/opt/buildagent/tools/"}]}}],"volumes":[{"name":"aci-1","azureFile":{"shareName":"aci-1","storageAccountName":"[parameters('storageAccountName')]","storageAccountKey":"[parameters('storageAccountKey')]"}},{"name":"aci-1-logs","azureFile":{"shareName":"aci-1-logs","storageAccountName":"[parameters('storageAccountName')]","storageAccountKey":"[parameters('storageAccountKey')]"}},{"name":"aci-1-plugins","azureFile":{"shareName":"aci-1-plugins","storageAccountName":"[parameters('storageAccountName')]","storageAccountKey":"[parameters('storageAccountKey')]"}},{"name":"aci-1-system","azureFile":{"shareName":"aci-1-system","storageAccountName":"[parameters('storageAccountName')]","storageAccountKey":"[parameters('storageAccountKey')]"}},{"name":"aci-1-tools","azureFile":{"shareName":"aci-1-tools","storageAccountName":"[parameters('storageAccountName')]","storageAccountKey":"[parameters('storageAccountKey')]"}}]}}],"parameters":{"storageAccountName":{"type":"String","metadata":{"description":""}},"storageAccountKey":{"type":"SecureString","metadata":{"description":""}}}}""")
    }

    fun testAddContainerEnvironment() {
        val builder = ArmTemplateBuilder("""{"resources": [
      {
        "type": "Microsoft.ContainerInstance/containerGroups",
        "name": "myName",
        "properties": {
          "containers": [
            {
              "name": "myName"
            }
          ]
        }
      }
    ]}""").addContainerEnvironment("myName", mapOf("key" to "value"))

        Assert.assertEquals(builder.toString(),
                """{"resources":[{"type":"Microsoft.ContainerInstance/containerGroups","name":"myName","properties":{"containers":[{"name":"myName","properties":{"environmentVariables":[{"name":"key","value":"value"}]}}]}}]}""")
    }

    fun testEnableAcceleratedNetworking() {
        val builder = ArmTemplateBuilder("""{"resources": [
      {
        "type": "Microsoft.Network/networkInterfaces",
        "name": "myName",
        "properties": {
        }
      }
    ]}""").enableAcceleratedNerworking()

        Assert.assertEquals(builder.toString(),
                "{\"resources\":[{\"type\":\"Microsoft.Network/networkInterfaces\",\"name\":\"myName\",\"properties\":{\"enableAcceleratedNetworking\":true}}]}")
    }

    fun testSetupSystemIdentity() {
        val builder = ArmTemplateBuilder("""{"resources": [
        {
          "type": "Microsoft.Compute/virtualMachines",
          "name": "myName",
          "properties": {
          }
        }
      ]}""").setupIdentity(null, true)

      Assert.assertEquals(builder.toString(),
        "{\"resources\":[{\"type\":\"Microsoft.Compute/virtualMachines\",\"name\":\"myName\",\"properties\":{},\"identity\":{\"type\":\"SystemAssigned\"}}]}")
    }

    fun testSetupSystemAssignedIdentity() {
      val builder = ArmTemplateBuilder("""{"resources": [
      {
        "type": "Microsoft.Compute/virtualMachines",
        "name": "myName",
        "properties": {
        }
      }
    ]}""").setupIdentity(null, true)

    Assert.assertEquals(builder.toString(),
      "{\"resources\":[{\"type\":\"Microsoft.Compute/virtualMachines\",\"name\":\"myName\",\"properties\":{},\"identity\":{\"type\":\"SystemAssigned\"}}]}")
  }

    fun testSetupUserAssignedIdentity() {
      val builder = ArmTemplateBuilder("""{"resources": [
      {
        "type": "Microsoft.Compute/virtualMachines",
        "name": "myName",
        "properties": {
        }
      }
    ]}""").setupIdentity("someIdentity", false)

    Assert.assertEquals(builder.toString(),
    "{\"resources\":[{\"type\":\"Microsoft.Compute/virtualMachines\",\"name\":\"myName\",\"properties\":{},\"identity\":{\"type\":\"UserAssigned\",\"userAssignedIdentities\":{\"someIdentity\":{}}}}]}")  }

    fun testSetupSystemAndUserAssignedIdentity() {
      val builder = ArmTemplateBuilder("""{"resources": [
      {
        "type": "Microsoft.Compute/virtualMachines",
        "name": "myName",
        "properties": {
        }
      }
    ]}""").setupIdentity("someIdentity", true)

    Assert.assertEquals(builder.toString(),
      "{\"resources\":[{\"type\":\"Microsoft.Compute/virtualMachines\",\"name\":\"myName\",\"properties\":{},\"identity\":{\"type\":\"SystemAssigned, UserAssigned\",\"userAssignedIdentities\":{\"someIdentity\":{}}}}]}")
  }

  fun testSetupIdentityWithNoIdentities() {
    val builder = ArmTemplateBuilder("""{"resources": [
    {
      "type": "Microsoft.Compute/virtualMachines",
      "name": "myName",
      "properties": {
      }
    }
  ]}""").setupIdentity("", false)

  Assert.assertEquals(builder.toString(),
    "{\"resources\":[{\"type\":\"Microsoft.Compute/virtualMachines\",\"name\":\"myName\",\"properties\":{}}]}")
  }
}
