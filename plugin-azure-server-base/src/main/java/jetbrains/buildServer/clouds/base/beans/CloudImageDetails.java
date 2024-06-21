package jetbrains.buildServer.clouds.base.beans;

import jetbrains.buildServer.clouds.base.types.CloneBehaviour;

/**
 * @author Sergey.Pak
 *         Date: 8/1/2014
 *         Time: 4:45 PM
 */
public interface CloudImageDetails {

  CloneBehaviour getBehaviour();

  int getMaxInstances();

  String getSourceId();
}
