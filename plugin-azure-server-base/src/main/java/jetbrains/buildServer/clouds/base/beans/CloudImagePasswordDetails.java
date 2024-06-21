package jetbrains.buildServer.clouds.base.beans;

/**
 * Defines cloud image details with password.
 */
public interface CloudImagePasswordDetails extends CloudImageDetails {
    String getPassword();

    void setPassword(final String password);
}
