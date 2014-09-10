package jetbrains.buildServer.clouds.base.connector;

import java.util.Date;
import java.util.Map;
import jetbrains.buildServer.clouds.InstanceStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/25/2014
 *         Time: 5:17 PM
 */
public abstract class AbstractInstance {
  private final String myName;


  public AbstractInstance(@NotNull final String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }


  public abstract boolean isInitialized();

  public abstract Date getStartDate();

  public abstract String getIpAddress();

  public abstract InstanceStatus getInstanceStatus();

  @Nullable
  public abstract String getProperty(String name);
}
