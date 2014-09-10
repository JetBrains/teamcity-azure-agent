package jetbrains.buildServer.clouds.azure.connector;

import com.microsoft.windowsazure.management.compute.models.RoleInstance;
import java.util.Date;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 8/5/2014
 *         Time: 2:14 PM
 */
public class AzureInstance extends AbstractInstance {

  @NotNull private final RoleInstance myInstance;

  public AzureInstance(@NotNull final RoleInstance instance) {
    super(instance.getInstanceName());
    myInstance = instance;
  }

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Override
  public Date getStartDate() {
    return new Date();
  } //TODO fix, when API will allow this

  @Override
  public String getIpAddress() {
    return myInstance.getIPAddress().toString();
  }

  @Override
  public InstanceStatus getInstanceStatus() {
    switch (myInstance.getPowerState()){
      case Started:
        return InstanceStatus.RUNNING;
      case Starting:
        return InstanceStatus.STARTING;
      case Stopped:
        return InstanceStatus.STOPPED;
      case Stopping:
        return InstanceStatus.STOPPING;
      case Unknown:
        return InstanceStatus.UNKNOWN;
    }
    return null;
  }

  @Nullable
  @Override
  public String getProperty(final String name) {
    return null;
  }
}
