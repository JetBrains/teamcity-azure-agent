

package jetbrains.buildServer.clouds.azure.connector;

import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Dmitry.Tretyakov
 *         Date: 3/18/2016
 *         Time: 5:42 PM
 */
public abstract class AzureApiConnectorBase<T extends AbstractCloudImage, G extends AbstractCloudInstance> implements CloudApiConnector<T, G> {
    @NotNull
    public <R extends AbstractInstance> Map<String, R> fetchInstances(@NotNull final T image) throws CheckedCloudException {
        final Map<T, Map<String, R>> imageMap = fetchInstances(Collections.singleton(image));
        final Map<String, R> result = imageMap.get(image);
        return result != null ? result : Collections.<String, R>emptyMap();
    }
}
