

package jetbrains.buildServer.clouds.base;

import jetbrains.buildServer.clouds.base.connector.AbstractInstance;

import java.util.*;

/**
 * @author Sergey.Pak
 *         Date: 10/27/2014
 *         Time: 3:57 PM
 */
public class FakeModel {
  private static final FakeModel instance = new FakeModel();

  public static FakeModel instance(){
    return instance;
  }

  public Map<String, AbstractInstance> getInstances(){
    return null;
  }

}
