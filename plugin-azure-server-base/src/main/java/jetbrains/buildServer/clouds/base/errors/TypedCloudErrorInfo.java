package jetbrains.buildServer.clouds.base.errors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TypedCloudErrorInfo{
  @NotNull private final String myType;
  @NotNull private final String myMessage;
  @NotNull private final String myDetails;
  @Nullable private final Throwable myThrowable;

  public static TypedCloudErrorInfo fromException(@NotNull Throwable th){
    return new TypedCloudErrorInfo(th.getMessage(), th.getMessage(), th.toString(), th);
  }

  public TypedCloudErrorInfo(@NotNull final String message) {
    this(message, message, null, null);
  }

  public TypedCloudErrorInfo(@NotNull final String type, @NotNull final String message) {
    this(type, message, null, null);
  }

  public TypedCloudErrorInfo(@NotNull final String type, @NotNull final String message, @Nullable final String details) {
    this(type, message, details, null);
  }

  public TypedCloudErrorInfo(@Nullable final String type,
                             @Nullable final String message,
                             @Nullable final String details,
                             @Nullable final Throwable throwable) {
    myType = String.valueOf(type);
    myMessage = String.valueOf(message);
    myDetails = String.valueOf(details);
    myThrowable = throwable;
  }

  @NotNull
  public String getType() {
    return myType;
  }

  @NotNull
  public String getMessage() {
    return myMessage;
  }

  @NotNull
  public String getDetails() {
    return myDetails;
  }

  @Nullable
  public Throwable getThrowable() {
    return myThrowable;
  }


}
