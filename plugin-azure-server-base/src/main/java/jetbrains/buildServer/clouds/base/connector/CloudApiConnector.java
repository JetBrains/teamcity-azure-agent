package jetbrains.buildServer.clouds.base.connector;

import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Sergey.Pak
 *         Date: 7/23/2014
 *         Time: 3:26 PM
 */
public interface CloudApiConnector<T extends AbstractCloudImage, G extends AbstractCloudInstance> {

  void test() throws CheckedCloudException;

  @NotNull
  <R extends AbstractInstance> Map<String, R> fetchInstances(@NotNull final T image) throws CheckedCloudException;

  @NotNull
  <R extends AbstractInstance> Map<T, Map<String, R>> fetchInstances(@NotNull final Collection<T> images) throws CheckedCloudException;

  @NotNull
  TypedCloudErrorInfo[] checkImage(@NotNull final T image);

  @NotNull
  TypedCloudErrorInfo[] checkInstance(@NotNull final G instance);
}
