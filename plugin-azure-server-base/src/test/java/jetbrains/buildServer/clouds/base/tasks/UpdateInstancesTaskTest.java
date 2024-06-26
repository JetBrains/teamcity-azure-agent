package jetbrains.buildServer.clouds.base.tasks;

import jetbrains.buildServer.clouds.base.AbstractCloudClient;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import jetbrains.buildServer.clouds.base.AbstractCloudInstance;
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector;
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

@Test
public class UpdateInstancesTaskTest {

  private UpdateInstancesTask myTask;
  private CloudApiConnector myApiConnector;
  private AbstractCloudClient myClient;

  @BeforeMethod
  public void setUp(){
    myApiConnector = new CloudApiConnector() {
      @Override
      public void test() throws CheckedCloudException {
      }

      @NotNull
      @Override
      public TypedCloudErrorInfo[] checkImage(@NotNull AbstractCloudImage image) {
        return new TypedCloudErrorInfo[0];
      }

      @NotNull
      @Override
      public TypedCloudErrorInfo[] checkInstance(@NotNull AbstractCloudInstance instance) {
        return new TypedCloudErrorInfo[0];
      }

      @NotNull
      @Override
      public Map fetchInstances(@NotNull Collection images) throws CheckedCloudException {
        return null;
      }

      @NotNull
      @Override
      public Map fetchInstances(@NotNull AbstractCloudImage image) throws CheckedCloudException {
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
