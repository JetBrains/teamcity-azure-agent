package jetbrains.buildServer.clouds.azure;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * @author Dmitry.Tretyakov
 *         Date: 20.06.2016
 *         Time: 16:13
 */
public class FileUtilsTest {

    @Test
    public void getCreationDate(){
        File file = new File("src/test/resources/SharedConfig.xml");
        FileUtils fileUtils = new FileUtilsImpl();
        long creationDate = fileUtils.getCreationDate(file);

        Assert.assertTrue(creationDate <= file.lastModified());
    }
}
