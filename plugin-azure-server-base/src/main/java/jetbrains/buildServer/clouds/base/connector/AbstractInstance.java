package jetbrains.buildServer.clouds.base.connector;

import jetbrains.buildServer.clouds.InstanceStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class AbstractInstance {
  @NotNull
  public abstract String getName();

  public abstract Date getStartDate();

  public abstract String getIpAddress();

  public abstract InstanceStatus getInstanceStatus();

  @Nullable
  public abstract String getProperty(String name);

  @NotNull
  public abstract Map<String, String> getProperties();
}
