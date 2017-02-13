/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/29/2014
 *         Time: 6:41 PM
 */
public class CloudTaskResult {
  private final boolean myHasErrors;
  private final String myDescription;
  private final Throwable myThrowable;



  public CloudTaskResult() {
    this(false, null, null);
  }

  public CloudTaskResult(@Nullable final String description) {
    this(false, description, null);
  }

  public CloudTaskResult(final boolean hasErrors, @Nullable final String description, @Nullable final Throwable throwable) {
    myHasErrors = hasErrors;
    myDescription = description;
    myThrowable = throwable;
  }

  public boolean isHasErrors() {
    return myHasErrors;
  }

  public String getDescription() {
    return myDescription;
  }

  public Throwable getThrowable() {
    return myThrowable;
  }
}
