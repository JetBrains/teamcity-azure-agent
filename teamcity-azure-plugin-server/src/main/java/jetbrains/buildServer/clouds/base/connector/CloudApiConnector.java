package jetbrains.buildServer.clouds.base.connector;

import java.util.Collection;
import java.util.Map;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 7/23/2014
 *         Time: 3:26 PM
 */
public interface CloudApiConnector<T extends AbstractCloudImage, G extends AbstractCloudInstance> {

  InstanceStatus getInstanceStatus(@NotNull final G instance);

  Map<String, ? extends AbstractInstance> listImageInstances(@NotNull final T image);

  Collection<TypedCloudErrorInfo> checkImage(@NotNull final T image);

  Collection<TypedCloudErrorInfo> checkInstance(@NotNull final G instance);
}
