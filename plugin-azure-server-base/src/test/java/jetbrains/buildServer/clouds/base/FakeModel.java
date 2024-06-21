package jetbrains.buildServer.clouds.base;

import jetbrains.buildServer.clouds.base.connector.AbstractInstance;

import java.util.*;

public class FakeModel {
  private static final FakeModel instance = new FakeModel();

  public static FakeModel instance(){
    return instance;
  }

  public Map<String, AbstractInstance> getInstances(){
    return null;
  }

}
