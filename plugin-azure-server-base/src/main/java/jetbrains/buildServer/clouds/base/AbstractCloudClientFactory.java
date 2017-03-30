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

package jetbrains.buildServer.clouds.base;

import java.util.Collection;

import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.beans.CloudImageDetails;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:51 PM
 */
public abstract class AbstractCloudClientFactory<D extends CloudImageDetails, C extends AbstractCloudClient>

  implements CloudClientFactory {

  public AbstractCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar) {
    cloudRegistrar.registerCloudFactory(this);
  }

  @NotNull
  public C createNewClient(@NotNull final CloudState state, @NotNull final CloudClientParameters params) {
    try {
      final TypedCloudErrorInfo[] profileErrors = checkClientParams(params);
      if (profileErrors != null && profileErrors.length > 0) {
        return createNewClient(state, params, profileErrors);
      }
      final Collection<D> imageDetailsList = parseImageData(params);
      final C newClient = createNewClient(state, imageDetailsList, params);
      newClient.populateImagesData(imageDetailsList);
      return newClient;
    } catch (Exception ex) {
      return createNewClient(state, params, new TypedCloudErrorInfo[]{TypedCloudErrorInfo.fromException(ex)});
    }
  }

  public abstract C createNewClient(
    @NotNull final CloudState state, @NotNull final Collection<D> images, @NotNull final CloudClientParameters params);

  public abstract C createNewClient(
    @NotNull final CloudState state, @NotNull final CloudClientParameters params, final TypedCloudErrorInfo[] profileErrors);

  public abstract Collection<D> parseImageData(CloudClientParameters params);

  @Nullable
  protected abstract TypedCloudErrorInfo[] checkClientParams(@NotNull final CloudClientParameters params);


}
