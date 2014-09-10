package jetbrains.buildServer.clouds.base.errors;

import jetbrains.buildServer.clouds.CloudErrorInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 2:40 PM
 */
public class TypedCloudErrorInfo{
  private final String myType;
  private final String myMessage;
  private final String myDetails;
  private final Throwable myThrowable;

  public TypedCloudErrorInfo(@NotNull final String type, @NotNull final String message) {
    this(type, message, null, null);
  }

  public TypedCloudErrorInfo(@NotNull final String type, @NotNull final String message, @Nullable final String details) {
    this(type, message, details, null);
  }

  public TypedCloudErrorInfo(@NotNull final String type,
                             @NotNull final String message,
                             @Nullable final String details,
                             @Nullable final Throwable throwable) {
    myType = type;
    myMessage = message;
    myDetails = details;
    myThrowable = throwable;
  }

  public String getType() {
    return myType;
  }

  public String getMessage() {
    return myMessage;
  }

  public String getDetails() {
    return myDetails;
  }

  public Throwable getThrowable() {
    return myThrowable;
  }
}
