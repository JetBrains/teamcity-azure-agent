package jetbrains.buildServer.clouds.models;

import com.google.gson.annotations.SerializedName;
import jetbrains.buildServer.clouds.base.beans.CloudImagePasswordDetails;
import jetbrains.buildServer.clouds.base.types.CloneBehaviour;

/**
 * @author Dmitry.Tretyakov
 *         Date: 3/2/2016
 *         Time: 2:29 PM
 */
public class FakeCloudImageDetails implements CloudImagePasswordDetails {
    @SerializedName("data")
    private final String myData;
    private String myPassword = null;

    public FakeCloudImageDetails(final String data) {
        myData = data;
    }

    @Override
    public CloneBehaviour getBehaviour() {
        return null;
    }

    @Override
    public int getMaxInstances() {
        return 0;
    }

    @Override
    public String getSourceId() {
        return "name";
    }

    @Override
    public String getPassword() {
        return myPassword;
    }

    @Override
    public void setPassword(String password) {
        myPassword = password;
    }

    public String getData() {
        return myData;
    }
}
