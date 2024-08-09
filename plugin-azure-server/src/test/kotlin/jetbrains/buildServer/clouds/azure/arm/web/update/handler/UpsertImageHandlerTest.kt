package jetbrains.buildServer.clouds.azure.arm.web.update.handler

import com.google.gson.Gson
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.web.update.ImageUpdateConstants
import jetbrains.buildServer.clouds.azure.arm.web.update.UpdateImageResult
import jetbrains.buildServer.controllers.BasePropertiesBean
import org.assertj.core.api.Assertions
import org.testng.annotations.Test

class UpsertImageHandlerTest {

    companion object {
        private val gson = Gson()
    }

    @Test
    fun testUpdateValue() {
        val key = "val_key"
        val initData = mutableMapOf(Pair(key, "value_b4_update"), Pair("immutable_key", "immutable_value"))
        val ser = gson.toJson(initData)
        val expectedVal = "value_after_update"

        val props = BasePropertiesBean(mutableMapOf(Pair(ImageUpdateConstants.PASSWORDS_DATA, ser)))
        props.properties[AzureConstants.VM_NAME_PREFIX] = key
        props.properties[ImageUpdateConstants.PASSWORD] = expectedVal

        val handler = UpsertImageHandler()
        val res = handler.processUpdate(props)

        initData[key] = expectedVal
        val expected = UpdateImageResult(gson.toJson(initData))

        Assertions.assertThat(res).isEqualTo(expected)
    }

    @Test
    fun testInsertValue() {
        val key = "val_key"
        val value = "new_val"
        val initData = mutableMapOf(Pair("present_key", "present_val"))
        val ser = gson.toJson(initData)

        val props = BasePropertiesBean(mutableMapOf(Pair(ImageUpdateConstants.PASSWORDS_DATA, ser)))
        props.properties[AzureConstants.VM_NAME_PREFIX] = key
        props.properties[ImageUpdateConstants.PASSWORD] = value

        val handler = UpsertImageHandler()
        val res = handler.processUpdate(props)

        initData[key] = value
        val expected = UpdateImageResult(gson.toJson(initData))

        Assertions.assertThat(res).isEqualTo(expected)
    }
}
