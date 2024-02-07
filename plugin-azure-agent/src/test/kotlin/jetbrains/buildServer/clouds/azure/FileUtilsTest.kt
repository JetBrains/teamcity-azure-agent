

package jetbrains.buildServer.clouds.azure

import org.testng.Assert
import org.testng.annotations.Test

import java.io.File

/**
 * @author Dmitry.Tretyakov
 * Date: 20.06.2016
 * Time: 16:13
 */
@Test
class FileUtilsTest {

    fun getCreationDate() {
        val file = File("src/test/resources/SharedConfig.xml")
        val fileUtils = FileUtilsImpl()
        val creationDate = fileUtils.getCreationDate(file)

        Assert.assertTrue(creationDate <= file.lastModified())
    }
}
