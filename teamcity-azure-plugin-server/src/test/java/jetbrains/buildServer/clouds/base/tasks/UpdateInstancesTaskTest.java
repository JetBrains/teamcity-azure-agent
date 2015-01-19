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

package jetbrains.buildServer.clouds.base.tasks;

import java.util.Collection;
import java.util.Map;
import jetbrains.buildServer.clouds.CloudException;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Sergey.Pak
 *         Date: 10/27/2014
 *         Time: 3:52 PM
 */
@Test
public class UpdateInstancesTaskTest {

  private UpdateInstancesTask myTask;
  private CloudApiConnector myApiConnector;
  private AbstractCloudClient myClient;

  @BeforeMethod
  public void setUp(){
    myApiConnector = new CloudApiConnector() {
      public void ping() throws CloudException {

      }

      public InstanceStatus getInstanceStatus(@NotNull final AbstractCloudInstance instance) {
        return null;
      }

      public Map<String, ? extends AbstractInstance> listImageInstances(@NotNull final AbstractCloudImage image) throws CloudException {
        return null;
      }

      public Collection<TypedCloudErrorInfo> checkImage(@NotNull final AbstractCloudImage image) {
        return null;
      }

      public Collection<TypedCloudErrorInfo> checkInstance(@NotNull final AbstractCloudInstance instance) {
        return null;
      }
    };
    myTask = new UpdateInstancesTask(myApiConnector, myClient);
  }

  public void test(){
  }


  @AfterMethod
  public void tearDown(){

  }

}
