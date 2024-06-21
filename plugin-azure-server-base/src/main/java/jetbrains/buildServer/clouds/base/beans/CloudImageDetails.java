package jetbrains.buildServer.clouds.base.beans;

import jetbrains.buildServer.clouds.base.types.CloneBehaviour;

public interface CloudImageDetails {

  CloneBehaviour getBehaviour();

  int getMaxInstances();

  String getSourceId();
}
