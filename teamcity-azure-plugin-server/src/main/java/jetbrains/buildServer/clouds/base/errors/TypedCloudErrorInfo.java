/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package jetbrains.buildServer.clouds.base.errors;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 2:40 PM
 */
public class TypedCloudErrorInfo{
  private final String myType;
  private final String myMessage;
  private final String myDetails;
  private final Throwable myThrowable;

  public TypedCloudErrorInfo(@NotNull final String type, @NotNull final String message) {
    this(type, message, null, null);
  }

  public TypedCloudErrorInfo(@NotNull final String type, @NotNull final String message, @Nullable final String details) {
    this(type, message, details, null);
  }

  public TypedCloudErrorInfo(@NotNull final String type,
                             @NotNull final String message,
                             @Nullable final String details,
                             @Nullable final Throwable throwable) {
    myType = type;
    myMessage = message;
    myDetails = details;
    myThrowable = throwable;
  }

  public String getType() {
    return myType;
  }

  public String getMessage() {
    return myMessage;
  }

  public String getDetails() {
    return myDetails;
  }

  public Throwable getThrowable() {
    return myThrowable;
  }
}
