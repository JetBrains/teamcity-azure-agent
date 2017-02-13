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

package jetbrains.buildServer.clouds.base.errors;

/**
 * @author Sergey.Pak
 *         Date: 11/12/2014
 *         Time: 5:54 PM
 */
public class CheckedCloudException extends Exception {

  public CheckedCloudException(final Throwable cause) {
    super(cause);
  }

  public CheckedCloudException(final String message) {
    super(message);
  }

  public CheckedCloudException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
