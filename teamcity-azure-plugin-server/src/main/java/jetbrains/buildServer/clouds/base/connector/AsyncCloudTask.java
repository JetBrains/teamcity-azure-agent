package jetbrains.buildServer.clouds.base.connector;

import java.util.concurrent.*;

/**
 * @author Sergey.Pak
 *         Date: 7/29/2014
 *         Time: 3:27 PM
 */
public interface AsyncCloudTask {

  Future<CloudTaskResult> executeAsync();

}
