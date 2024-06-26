package jetbrains.buildServer.clouds.base.types;

public enum CloneBehaviour {
  START_STOP(false, true),
  FRESH_CLONE(true, false),
  ON_DEMAND_CLONE(false, false);
  private final boolean myDeleteAfterStop;
  private final boolean myUseOriginal;

  CloneBehaviour(final boolean deleteAfterStop, final boolean useOriginal) {
    myDeleteAfterStop = deleteAfterStop;
    myUseOriginal = useOriginal;
  }

  public boolean isDeleteAfterStop() {
    return myDeleteAfterStop;
  }

  public boolean isUseOriginal() {
    return myUseOriginal;
  }
}
