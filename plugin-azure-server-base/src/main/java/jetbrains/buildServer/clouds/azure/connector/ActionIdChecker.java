package jetbrains.buildServer.clouds.azure.connector;

import org.jetbrains.annotations.NotNull;

public interface ActionIdChecker {

  boolean isActionFinished(@NotNull String actionId);
}
