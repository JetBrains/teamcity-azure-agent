package jetbrains.buildServer.clouds.models;

import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudImageParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FakeCloudClientParameters extends CloudClientParameters {
    private final Map<String, String> params = new HashMap<>();

    @Nullable
    @Override
    public String getParameter(@NotNull String name) {
        return params.get(name);
    }

    @NotNull
    @Override
    public Collection<String> listParameterNames() {
        return params.keySet();
    }

    @Override
    public Collection<CloudImageParameters> getCloudImages() {
        return null;
    }

    @NotNull
    @Override
    public Map<String, String> getParameters() {
        return params;
    }

    @NotNull
    @Override
    public String getProfileId() {
        return "";
    }

    @NotNull
    @Override
    public String getProfileDescription() {
        return "";
    }

    public void setParameter(String key, String value) {
        this.params.put(key, value);
    }
}
