package jetbrains.buildServer.clouds.azure.arm.connector;

import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Credentials request authenticator.
 */
public class CredentialsAuthenticator implements Authenticator {

    private final String myCredentials;

    public CredentialsAuthenticator(@NotNull final String username, @Nullable final String password) {
        myCredentials = Credentials.basic(username, password);
    }

    @Override
    public Request authenticate(Route route, Response response) throws IOException {
        return response.request().newBuilder()
                .header("Proxy-Authorization", myCredentials)
                .build();
    }
}
