package jetbrains.buildServer.clouds.azure.arm.web.update.handler

import com.google.gson.Gson
import jetbrains.buildServer.clouds.azure.arm.AzureConstants
import jetbrains.buildServer.clouds.azure.arm.web.update.ImageUpdateConstants
import jetbrains.buildServer.clouds.azure.arm.web.update.UpdateImageResult
import jetbrains.buildServer.controllers.BasePropertiesBean
import org.assertj.core.api.Assertions
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

class DeleteImageHandlerTest {

    companion object {
        private val gson = Gson()
    }

    @Test(dataProvider = "testData")
    fun testDeletion(data: Array<String>) {
        val props = BasePropertiesBean(mutableMapOf(Pair(ImageUpdateConstants.PASSWORDS_DATA, data[0])))
        props.properties[AzureConstants.VM_NAME_PREFIX] = "toDelete"

        val handler = DeleteImageHandler()
        val res = handler.processUpdate(props)
        val expected = UpdateImageResult(data[1])
        Assertions.assertThat(res).isEqualTo(expected)
    }

    @DataProvider(name = "testData")
    fun dataProvider(): Array<Array<String>> {
        val map = mutableMapOf(Pair("useful", "dont delete"))
        val afterDelete = gson.toJson(map)
        map["toDelete"] = "someVal"
        val b4Delete = gson.toJson(map)

        return arrayOf(arrayOf("{}", "{}"), arrayOf(b4Delete, afterDelete))
    }
}
