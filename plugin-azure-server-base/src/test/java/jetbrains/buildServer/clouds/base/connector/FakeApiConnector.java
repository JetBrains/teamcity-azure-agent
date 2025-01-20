package jetbrains.buildServer.clouds.base.connector;

import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class FakeApiConnector implements CloudApiConnector {

  @Override
  public void test() throws CheckedCloudException {
  }

  @NotNull
  @Override
  public TypedCloudErrorInfo[] checkImage(@NotNull AbstractCloudImage image) {
    return new TypedCloudErrorInfo[0];
  }

  @NotNull
  @Override
  public TypedCloudErrorInfo[] checkInstance(@NotNull AbstractCloudInstance instance) {
    return new TypedCloudErrorInfo[0];
  }

  @NotNull
  @Override
  public Map fetchInstances(@NotNull Collection images) throws CheckedCloudException {
    return null;
  }

  @NotNull
  @Override
  public Map fetchInstances(@NotNull AbstractCloudImage image) throws CheckedCloudException {
    return null;
  }
}
