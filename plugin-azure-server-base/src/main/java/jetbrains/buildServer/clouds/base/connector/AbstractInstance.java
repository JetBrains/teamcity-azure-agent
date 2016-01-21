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

package jetbrains.buildServer.clouds.base.connector;

import java.util.Date;
import java.util.Map;
import jetbrains.buildServer.clouds.InstanceStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/25/2014
 *         Time: 5:17 PM
 */
public abstract class AbstractInstance {
  private final String myName;


  public AbstractInstance(@NotNull final String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }


  public abstract boolean isInitialized();

  public abstract Date getStartDate();

  public abstract String getIpAddress();

  public abstract InstanceStatus getInstanceStatus();

  @Nullable
  public abstract String getProperty(String name);
}
