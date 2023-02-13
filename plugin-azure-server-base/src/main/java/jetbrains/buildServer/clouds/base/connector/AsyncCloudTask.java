/*
 * Copyright 2000-2021 JetBrains s.r.o.
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
