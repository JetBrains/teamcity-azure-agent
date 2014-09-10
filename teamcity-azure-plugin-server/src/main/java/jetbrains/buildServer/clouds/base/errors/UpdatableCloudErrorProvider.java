package jetbrains.buildServer.clouds.base.errors;

import java.util.Collection;
import jetbrains.buildServer.clouds.CloudErrorProvider;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/24/2014
 *         Time: 2:41 PM
 */
public interface UpdatableCloudErrorProvider extends CloudErrorProvider {

  void updateErrors(@Nullable final Collection<TypedCloudErrorInfo> errors);
}
