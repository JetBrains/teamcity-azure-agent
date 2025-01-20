package jetbrains.buildServer.clouds.base.connector;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Future;

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
