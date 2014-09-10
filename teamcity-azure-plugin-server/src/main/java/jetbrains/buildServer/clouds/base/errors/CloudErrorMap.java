package jetbrains.buildServer.clouds.base.errors;

import java.util.*;
import jetbrains.buildServer.clouds.CloudErrorInfo;
import jetbrains.buildServer.clouds.CloudErrorProvider;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/23/2014
 *         Time: 4:51 PM
 */
public class CloudErrorMap implements UpdatableCloudErrorProvider {
  protected final Map<String, TypedCloudErrorInfo> myErrors;
  private CloudErrorInfo myErrorInfo;

  public CloudErrorMap() {
    myErrors = new HashMap<String, TypedCloudErrorInfo>();
  }

  public void updateErrors(@Nullable final Collection<TypedCloudErrorInfo> errors){
    myErrorInfo = null;
    if (errors != null) {
      myErrors.clear();
      myErrors.putAll(mapFromCollection(errors));
      if (myErrors.size() == 0)
        return;
      if (myErrors.size() == 1) {
        final TypedCloudErrorInfo err = myErrors.values().iterator().next();
        if (err.getThrowable() != null) {
          myErrorInfo = new CloudErrorInfo(err.getMessage(), err.getDetails(), err.getThrowable());
        } else {
          myErrorInfo = new CloudErrorInfo(err.getMessage(), err.getDetails());
        }
      } else {
        final StringBuilder msgBuilder = new StringBuilder();
        final StringBuilder detailsBuilder = new StringBuilder();
        for (TypedCloudErrorInfo errorInfo : myErrors.values()) {
          msgBuilder.append(",").append(errorInfo.getMessage());
          detailsBuilder.append(",\n[").append(errorInfo.getDetails()).append("]");
        }
        myErrorInfo = new CloudErrorInfo(msgBuilder.substring(1), detailsBuilder.substring(2));
      }
    }
  }

  private static Map<String, TypedCloudErrorInfo> mapFromCollection(final Collection<TypedCloudErrorInfo> collection){
    final Map<String, TypedCloudErrorInfo> map = new HashMap<String, TypedCloudErrorInfo>();
    for (TypedCloudErrorInfo errorInfo : collection) {
      map.put(errorInfo.getType(), errorInfo);
    }
    return map;
  }

  public CloudErrorInfo getErrorInfo(){
    return myErrorInfo;
  }

}
