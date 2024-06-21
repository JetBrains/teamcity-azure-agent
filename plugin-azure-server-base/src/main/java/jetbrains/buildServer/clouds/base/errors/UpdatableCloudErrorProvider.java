package jetbrains.buildServer.clouds.base.errors;

import jetbrains.buildServer.clouds.CloudErrorProvider;

/**
 * @author Sergey.Pak
 *         Date: 7/24/2014
 *         Time: 2:41 PM
 */
public interface UpdatableCloudErrorProvider extends CloudErrorProvider {

  void updateErrors(TypedCloudErrorInfo... errors);
}
