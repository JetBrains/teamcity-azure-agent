

package jetbrains.buildServer.clouds.base.connector;

import java.util.concurrent.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/29/2014
 *         Time: 3:27 PM
 */
public interface AsyncCloudTask {

  /**
   * Consecutive execution of this method will makes no effect. Only first call of this method starts the executing.
   * All next calls just return the result's future
   * @return result's future
   */
  Future<CloudTaskResult> executeOrGetResultAsync();

  @NotNull
  String getName();

  @Nullable
  long getStartTime();
}
