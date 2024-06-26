package jetbrains.buildServer.clouds.base.connector;

import org.jetbrains.annotations.Nullable;

public class CloudTaskResult {
  private final boolean myHasErrors;
  private final String myDescription;
  private final Throwable myThrowable;



  public CloudTaskResult() {
    this(false, null, null);
  }

  public CloudTaskResult(@Nullable final String description) {
    this(false, description, null);
  }

  public CloudTaskResult(final boolean hasErrors, @Nullable final String description, @Nullable final Throwable throwable) {
    myHasErrors = hasErrors;
    myDescription = description;
    myThrowable = throwable;
  }

  public boolean isHasErrors() {
    return myHasErrors;
  }

  public String getDescription() {
    return myDescription;
  }

  public Throwable getThrowable() {
    return myThrowable;
  }
}
