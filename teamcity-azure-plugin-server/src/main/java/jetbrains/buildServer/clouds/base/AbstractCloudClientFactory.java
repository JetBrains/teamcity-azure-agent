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

package jetbrains.buildServer.clouds.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
public abstract class AbstractCloudClientFactory <D extends CloudImageDetails,C extends AbstractCloudClient>

  implements CloudClientFactory {

  public AbstractCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar) {
    cloudRegistrar.registerCloudFactory(this);
  }

  @NotNull
  public CloudClientEx createNewClient(@NotNull final CloudState state, @NotNull final CloudClientParameters params) {
    try {
      final String imagesData = params.getParameter("images_data");
      final TypedCloudErrorInfo[] profileErrors = checkClientParams(params);
      if (profileErrors != null && profileErrors.length > 0) {
        return createNewClient(state, params, profileErrors);
      }
      final Collection<D> imageDetailsList = parseImageData(imagesData);
      return createNewClient(state, imageDetailsList, params);
    } catch (Exception ex){
      return createNewClient(state, params, new TypedCloudErrorInfo[]{new TypedCloudErrorInfo(ex.getMessage(), ex.getMessage())});
    }
  }

  public abstract C createNewClient(
    @NotNull final CloudState state, @NotNull final Collection<D> images, @NotNull final CloudClientParameters params);

  public abstract C createNewClient(
    @NotNull final CloudState state, @NotNull final CloudClientParameters params, TypedCloudErrorInfo[] profileErrors);

  public abstract Collection<D> parseImageData(String imageData);

  @Nullable
  protected abstract TypedCloudErrorInfo[] checkClientParams(@NotNull final CloudClientParameters params);


}
