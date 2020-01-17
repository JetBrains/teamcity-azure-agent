/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure;

import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.models.FakeCloudImageDetails;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author Dmitry.Tretyakov
 *         Date: 3/2/2016
 *         Time: 2:27 PM
 */
public class AzureUtilsTest {
    @Test
    public void parseImageDataTest() {
        final CloudClientParameters parameters = new CloudClientParameters();
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
        final CloudClientParameters parameters = new CloudClientParameters();
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
