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

import jetbrains.buildServer.clouds.CloudException;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.azure.AzureArmCloudImage;
import jetbrains.buildServer.clouds.azure.AzureArmCloudInstance;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * @author Sergey.Pak
 *         Date: 8/5/2014
 *         Time: 2:13 PM
 */
public class AzureArmApiConnector implements CloudApiConnector<AzureArmCloudImage, AzureArmCloudInstance>, ActionIdChecker {
  @Override
  public void ping() throws CloudException {
  }

  @Override
  public InstanceStatus getInstanceStatus(@NotNull AzureArmCloudInstance instance) {
    return null;
  }

  @Override
  public Map<String, AzureArmInstance> listImageInstances(@NotNull AzureArmCloudImage image) throws CloudException {
    return null;
  }

  @Override
  public Collection<TypedCloudErrorInfo> checkImage(@NotNull AzureArmCloudImage image) {
    return null;
  }

  @Override
  public Collection<TypedCloudErrorInfo> checkInstance(@NotNull AzureArmCloudInstance instance) {
    return null;
  }

  @Override
  public boolean isActionFinished(@NotNull String actionId) {
    return false;
  }
}
