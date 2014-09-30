package jetbrains.buildServer.clouds.azure.connector;


import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey.Pak
 *         Date: 9/26/2014
 *         Time: 6:51 PM
 */
public interface ActionIdChecker {

  boolean isActionFinished(@NotNull String actionId);
}
