

package jetbrains.buildServer.clouds.azure.arm.connector

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Credentials request authenticator.
 */
class CredentialsAuthenticator(username: String, password: String) : Authenticator {

    private val myCredentials: String = Credentials.basic(username, password)

    override fun authenticate(route: Route?, response: Response): Request {
        return response.request().newBuilder()
                .header("Proxy-Authorization", myCredentials)
                .build()
    }
}
