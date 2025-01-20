package jetbrains.buildServer.clouds.base.errors;

import jetbrains.buildServer.clouds.CloudErrorProvider;

public interface UpdatableCloudErrorProvider extends CloudErrorProvider {

  void updateErrors(TypedCloudErrorInfo... errors);
}
