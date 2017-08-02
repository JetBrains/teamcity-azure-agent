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
        ]}""").setTags(mapOf(AzureConstants.TAG_PROFILE to "profile"))

        Assert.assertEquals(builder.toString(),
                """{"resources":[{"name":"[parameters('vmName')]","tags":{"teamcity-profile":"profile"}},{"vm":"vm"}]}""")
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
}
