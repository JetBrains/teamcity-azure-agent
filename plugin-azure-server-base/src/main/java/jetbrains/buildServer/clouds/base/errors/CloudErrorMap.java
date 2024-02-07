

package jetbrains.buildServer.clouds.base.errors;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/23/2014
 *         Time: 4:51 PM
 */
public class CloudErrorMap implements UpdatableCloudErrorProvider {
  private final ErrorMessageUpdater myMessageUpdater;
  private final AtomicReference<CloudErrorInfo> myErrorInfo = new AtomicReference<>();

  public CloudErrorMap(final ErrorMessageUpdater messageUpdater) {
    myMessageUpdater = messageUpdater;
  }

  public void updateErrors(@Nullable final TypedCloudErrorInfo... errors){
    final Map<String, TypedCloudErrorInfo> errorInfoMap = mapFromArray(errors);
    if (errors != null && errorInfoMap.size() > 0) {
      if (errorInfoMap.size() == 1) {
        final TypedCloudErrorInfo err = errorInfoMap.values().iterator().next();
        final String message = err.getMessage();
        final String friendlyErrorMessage = myMessageUpdater.getFriendlyErrorMessage(message);
        final String details;
        if (!friendlyErrorMessage.equals(message)){
          details = message + "\n" + err.getDetails();
        } else {
          details = err.getDetails();
        }
        if (err.getThrowable() != null) {
          myErrorInfo.set(new CloudErrorInfo(friendlyErrorMessage, details, err.getThrowable()));
        } else {
          myErrorInfo.set(new CloudErrorInfo(friendlyErrorMessage, details));
        }
      } else {
        final StringBuilder msgBuilder = new StringBuilder();
        final StringBuilder detailsBuilder = new StringBuilder();
        for (TypedCloudErrorInfo errorInfo : errorInfoMap.values()) {
          msgBuilder.append(",").append(myMessageUpdater.getFriendlyErrorMessage(errorInfo.getMessage()));
          detailsBuilder.append(",\n[").append(errorInfo.getDetails()).append("]");
        }
        myErrorInfo.set(new CloudErrorInfo(msgBuilder.substring(1), detailsBuilder.substring(2)));
      }
    } else {
      myErrorInfo.set(null);
    }

  }

  private static Map<String, TypedCloudErrorInfo> mapFromArray(@Nullable  final TypedCloudErrorInfo[] array){
    final Map<String, TypedCloudErrorInfo> map = new HashMap<String, TypedCloudErrorInfo>();
    if (array != null) {
      for (TypedCloudErrorInfo errorInfo : array) {
        map.put(errorInfo.getType(), errorInfo);
      }
    }
    return map;
  }

  public CloudErrorInfo getErrorInfo(){
    return myErrorInfo.get();
  }

}
