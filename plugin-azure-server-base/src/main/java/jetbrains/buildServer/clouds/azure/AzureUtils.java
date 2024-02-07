

package jetbrains.buildServer.clouds.azure;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.base.beans.CloudImageDetails;
import jetbrains.buildServer.clouds.base.beans.CloudImagePasswordDetails;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Provides utils for azure services.
 */
public final class AzureUtils {
    private static final Gson myGson = new Gson();
    private static final Type stringStringMapType = new TypeToken<Map<String, String>>() {
    }.getType();

    public static <T extends CloudImageDetails> Collection<T> parseImageData(Class<T> clazz, final CloudClientParameters params) {

        final String imageData = StringUtil.notEmpty(
                params.getParameter(CloudImageParameters.SOURCE_IMAGES_JSON),
                StringUtil.emptyIfNull(params.getParameter("images_data")));
        if (StringUtil.isEmpty(imageData)) {
            return Collections.emptyList();
        }

        final ListParameterizedType listType = new ListParameterizedType(clazz);
        final List<T> images = myGson.fromJson(imageData, listType);
        setPasswords(clazz, params, images);

        return new ArrayList<>(images);
    }

    public static <T extends CloudImageDetails> void setPasswords(Class<T> clazz, CloudClientParameters params, List<T> images) {
        if (CloudImagePasswordDetails.class.isAssignableFrom(clazz)) {
            final String passwordData = params.getParameter("secure:passwords_data");
            final Map<String, String> data = myGson.fromJson(passwordData, stringStringMapType);
            if (data != null) {
                for (T image : images) {
                    final CloudImagePasswordDetails userImage = (CloudImagePasswordDetails) image;
                    if (data.get(image.getSourceId()) != null) {
                        userImage.setPassword(data.get(image.getSourceId()));
                    }
                }
            }
        }
    }

    private static class ListParameterizedType implements ParameterizedType {

        private Type type;

        private ListParameterizedType(Type type) {
            this.type = type;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{type};
        }

        @Override
        public Type getRawType() {
            return ArrayList.class;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        // implement equals method too! (as per javadoc)
    }

    /**
     * Updates tag data.
     *
     * @param tag    original tag.
     * @param vmName virtual machine name.
     * @return updated tag.
     */
    public static CloudInstanceUserData setVmNameForTag(@NotNull final CloudInstanceUserData tag, @NotNull final String vmName) {
        return new CloudInstanceUserData(vmName,
                tag.getAuthToken(),
                tag.getServerAddress(),
                tag.getIdleTimeout(),
                tag.getProfileId(),
                tag.getProfileDescription(),
                tag.getCustomAgentConfigurationParameters());
    }
}
