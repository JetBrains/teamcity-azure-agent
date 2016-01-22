/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.connector;

import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * @author Sergey.Pak
 *         Date: 8/5/2014
 *         Time: 2:14 PM
 */
public class AzureArmInstance extends AbstractInstance {

  public AzureArmInstance() {
    super("name");
  }

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Override
  public Date getStartDate() {
    return null;
  } //TODO fix, when API will allow this

  @Override
  public String getIpAddress() {
    return null;
  }

  @Override
  @NotNull
  public InstanceStatus getInstanceStatus() {
    return InstanceStatus.UNKNOWN;
  }

  @Nullable
  @Override
  public String getProperty(final String name) {
    return null;
  }
}
