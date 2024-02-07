

package jetbrains.buildServer.clouds.base;

import java.util.Collection;

import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.clouds.base.beans.CloudImageDetails;
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Pak
 *         Date: 7/22/2014
 *         Time: 1:51 PM
 */
public abstract class AbstractCloudClientFactory<D extends CloudImageDetails, C extends AbstractCloudClient>
  implements CloudClientFactory {

  private static final String AZURE_POLLING_INTERVAL_MS = "teamcity.azure.cloud.pollingIntervalMs";

  public AbstractCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar) {
    cloudRegistrar.registerCloudFactory(this);
  }

  @NotNull
  public C createNewClient(@NotNull final CloudState state, @NotNull final CloudClientParameters params) {
    try {
      final TypedCloudErrorInfo[] profileErrors = checkClientParams(params);
      if (profileErrors != null && profileErrors.length > 0) {
        return createNewClient(state, params, profileErrors);
      }
      final Collection<D> imageDetailsList = parseImageData(params);
      final C newClient = createNewClient(state, imageDetailsList, params);
      final long pollingInterval = TeamCityProperties.getLong(AZURE_POLLING_INTERVAL_MS, 60 * 1000);
      newClient.populateImagesData(imageDetailsList, pollingInterval, pollingInterval);
      return newClient;
    } catch (Exception ex) {
      return createNewClient(state, params, new TypedCloudErrorInfo[]{TypedCloudErrorInfo.fromException(ex)});
    }
  }

  public abstract C createNewClient(
    @NotNull final CloudState state, @NotNull final Collection<D> images, @NotNull final CloudClientParameters params);

  public abstract C createNewClient(
    @NotNull final CloudState state, @NotNull final CloudClientParameters params, final TypedCloudErrorInfo[] profileErrors);

  public abstract Collection<D> parseImageData(CloudClientParameters params);

  @Nullable
  protected abstract TypedCloudErrorInfo[] checkClientParams(@NotNull final CloudClientParameters params);

}
