

package jetbrains.buildServer.clouds.azure;

import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.models.FakeCloudClientParameters;
import jetbrains.buildServer.clouds.models.FakeCloudImageDetails;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author Dmitry.Tretyakov
 * Date: 3/2/2016
 * Time: 2:27 PM
 */
public class AzureUtilsTest {
    @Test
    public void parseImageDataTest() {
        final FakeCloudClientParameters parameters = new FakeCloudClientParameters();
        parameters.setParameter("images_data", "[{\"data\":\"data\"}]");
        parameters.setParameter("secure:passwords_data", "{\"name\":\"password\"}");

        final Collection<FakeCloudImageDetails> images = AzureUtils.parseImageData(FakeCloudImageDetails.class, parameters);
        Assert.assertNotNull(images);

        final Iterator<FakeCloudImageDetails> iterator = images.iterator();
        Assert.assertTrue(iterator.hasNext());
        final FakeCloudImageDetails details = iterator.next();
        Assert.assertFalse(iterator.hasNext());
        Assert.assertNotNull(details);
        Assert.assertEquals(details.getData(), "data");
        Assert.assertEquals(details.getPassword(), "password");
    }

    @Test
    public void parseNewImageDataTest() {
        final FakeCloudClientParameters parameters = new FakeCloudClientParameters();
        parameters.setParameter(CloudImageParameters.SOURCE_IMAGES_JSON, "[{\"data\":\"data\"}]");
        parameters.setParameter("secure:passwords_data", "{\"name\":\"password\"}");

        final Collection<FakeCloudImageDetails> images = AzureUtils.parseImageData(FakeCloudImageDetails.class, parameters);
        Assert.assertNotNull(images);

        final Iterator<FakeCloudImageDetails> iterator = images.iterator();
        Assert.assertTrue(iterator.hasNext());
        final FakeCloudImageDetails details = iterator.next();
        Assert.assertFalse(iterator.hasNext());
        Assert.assertNotNull(details);
        Assert.assertEquals(details.getData(), "data");
        Assert.assertEquals(details.getPassword(), "password");
    }

}
