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

package jetbrains.buildServer.clouds.base;

import com.intellij.openapi.diagnostic.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.beans.CloudImageDetails;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.connector.CloudAsyncTaskExecutor;
import jetbrains.buildServer.clouds.base.errors.CloudErrorMap;
import jetbrains.buildServer.clouds.base.errors.DefaultErrorMessageUpdater;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.clouds.base.errors.UpdatableCloudErrorProvider;
import jetbrains.buildServer.clouds.base.tasks.UpdateInstancesTask;
import jetbrains.buildServer.serverSide.AgentDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:49 PM
 */
public abstract class AbstractCloudClient<G extends AbstractCloudInstance<T>, T extends AbstractCloudImage<G, D>, D extends CloudImageDetails>
  implements CloudClientEx, UpdatableCloudErrorProvider {

  private static final Logger LOG = Logger.getInstance(AbstractCloudClient.class.getName());
  protected final Map<String, T> myImageMap;
  protected final UpdatableCloudErrorProvider myErrorProvider;
  protected final CloudAsyncTaskExecutor myAsyncTaskExecutor;
  @NotNull
  protected final CloudApiConnector myApiConnector;
  protected final CloudClientParameters myParameters;
  private volatile boolean myIsInitialized = false;

  public AbstractCloudClient(@NotNull final CloudClientParameters params, @NotNull final CloudApiConnector apiConnector) {
    myParameters = params;
    myAsyncTaskExecutor = new CloudAsyncTaskExecutor("Async tasks for cloud " + params.getProfileDescription());
    myImageMap = new HashMap<String, T>();
    myErrorProvider = new CloudErrorMap(new DefaultErrorMessageUpdater());
    myApiConnector = apiConnector;
  }

  public boolean isInitialized() {
    return myIsInitialized;
  }


  public void dispose() {
    myAsyncTaskExecutor.dispose();
  }

  @NotNull
  public G startNewInstance(@NotNull final CloudImage baseImage, @NotNull final CloudInstanceUserData tag) throws QuotaException {
    final T image = (T) baseImage;
    return image.startNewInstance(tag);
  }

  public void restartInstance(@NotNull final CloudInstance baseInstance) {
    final G instance = (G) baseInstance;
    instance.getImage().restartInstance(instance);
  }

  public void terminateInstance(@NotNull final CloudInstance baseInstance) {
    final G instance = (G) baseInstance;
    instance.getImage().terminateInstance(instance);
  }

  public boolean canStartNewInstance(@NotNull final CloudImage baseImage) {
    final T image = (T) baseImage;
    return image.canStartNewInstance();
  }

  public void populateImagesData(@NotNull final Collection<D> imageDetails) {
    populateImagesData(imageDetails, 60 * 1000, 60 * 1000);
  }

  public void populateImagesData(@NotNull final Collection<D> imageDetails, final long initialDelayMs, final long delayMs) {
    for (D details : imageDetails) {
      T image = checkAndCreateImage(details);
      myImageMap.put(image.getName(), image);
    }
    final UpdateInstancesTask<G, T, ?> updateInstancesTask = createUpdateInstancesTask();
    if (updateInstancesTask == null) {
      return;
    }
    myAsyncTaskExecutor.submit("Populate images data", new Runnable() {
      public void run() {
        try {
          updateInstancesTask.run();
          myAsyncTaskExecutor.scheduleWithFixedDelay("Update instances", updateInstancesTask, initialDelayMs, delayMs, TimeUnit.MILLISECONDS);
        } finally {
          myIsInitialized = true;
          LOG.info("Cloud profile '" + myParameters.getProfileDescription() + "' initialized");
        }
      }
    });
  }

  protected abstract T checkAndCreateImage(@NotNull final D imageDetails);

  @NotNull
  protected abstract UpdateInstancesTask<G, T, ?> createUpdateInstancesTask();

  @Nullable
  public abstract G findInstanceByAgent(@NotNull final AgentDescription agent);

  @Nullable
  public T findImageById(@NotNull final String imageId) throws CloudException {
    return myImageMap.get(imageId);
  }

  @NotNull
  public Collection<T> getImages() throws CloudException {
    return Collections.unmodifiableCollection(myImageMap.values());
  }

  public void updateErrors(final TypedCloudErrorInfo... errors) {
    myErrorProvider.updateErrors(errors);
  }

  @Nullable
  public CloudErrorInfo getErrorInfo() {
    return myErrorProvider.getErrorInfo();
  }
}
