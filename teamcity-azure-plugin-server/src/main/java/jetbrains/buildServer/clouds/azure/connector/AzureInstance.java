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

package jetbrains.buildServer.clouds.azure.connector;

import com.microsoft.windowsazure.management.compute.models.RoleInstance;
import java.net.InetAddress;
import java.util.Date;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 8/5/2014
 *         Time: 2:14 PM
 */
public class AzureInstance extends AbstractInstance {

  @NotNull private final RoleInstance myInstance;

  public AzureInstance(@NotNull final RoleInstance instance) {
    super(instance.getInstanceName());
    myInstance = instance;
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
    final InetAddress ipAddress = myInstance.getIPAddress();
    return ipAddress != null ? ipAddress.toString() : null;
  }

  @Override
  public InstanceStatus getInstanceStatus() {
    switch (myInstance.getPowerState()){
      case Started:
        return InstanceStatus.RUNNING;
      case Starting:
        return InstanceStatus.STARTING;
      case Stopped:
        return InstanceStatus.STOPPED;
      case Stopping:
        return InstanceStatus.STOPPING;
      case Unknown:
        return InstanceStatus.UNKNOWN;
    }
    return null;
  }

  @Nullable
  @Override
  public String getProperty(final String name) {
    return null;
  }
}
