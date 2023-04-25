package jetbrains.buildServer.clouds.azure.arm.resourceGraph

import com.google.common.reflect.TypeToken
import com.microsoft.rest.ServiceCallback
import com.microsoft.rest.ServiceFuture
import com.microsoft.rest.ServiceResponse
import com.microsoft.rest.Validator
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*
import rx.Observable
import java.io.IOException

class ResourceProvidersInner(
    retrofit: Retrofit,
    private val client: ResourceGraphClientImpl
) {
    private val service = retrofit.create(ResourceProvidersService::class.java)

    interface ResourceProvidersService {
        @Headers("Content-Type: application/json; charset=utf-8", "x-ms-logging-context: JetBrains.ResourceProviders resources")
        @POST("providers/Microsoft.ResourceGraph/resources")
        fun resources(
            @Query("api-version") apiVersion: String,
            @Body query: QueryRequest,
            @Header("accept-language") acceptLanguage: String,
            @Header("User-Agent") userAgent: String
        ): Observable<Response<ResponseBody>>
    }

    fun resources(query: QueryRequest): QueryResponseInner {
        return resourcesWithServiceResponseAsync(query).toBlocking().single().body()
    }

    fun resourcesAsync(query: QueryRequest, serviceCallback: ServiceCallback<QueryResponseInner>): ServiceFuture<QueryResponseInner> {
        return ServiceFuture.fromResponse(resourcesWithServiceResponseAsync(query), serviceCallback)
    }

    fun resourcesAsync(query: QueryRequest): Observable<QueryResponseInner> {
        return resourcesWithServiceResponseAsync(query).map { response -> response.body() }
    }

    fun resourcesWithServiceResponseAsync(query: QueryRequest): Observable<ServiceResponse<QueryResponseInner>> {
        val queryWithSubscriptions = if (query.subscriptions?.isNotEmpty() == true || client.subscriptionId == null) query else query.withSubscriptions(listOf(client.subscriptionId!!))
        Validator.validate(queryWithSubscriptions)
        return service
            .resources(client.apiVersion, queryWithSubscriptions, client.acceptLanguage, client.userAgent())
            .flatMap { response ->
                try {
                    val clientResponse = getResources(response)
                    Observable.just(clientResponse)
                } catch (t: Throwable) {
                    Observable.error<ServiceResponse<QueryResponseInner>>(t)
                }
            }
    }

    @Throws(ErrorResponseException::class, IOException::class, IllegalArgumentException::class)
    private fun getResources(response: Response<ResponseBody>): ServiceResponse<QueryResponseInner> {
        return client
            .restClient()
            .responseBuilderFactory()
            .newInstance<QueryResponseInner, ErrorResponseException>(client.serializerAdapter())
            .register(200, object : TypeToken<QueryResponseInner>() {}.type)
            .registerError(ErrorResponseException::class.java)
            .build(response)
    }
}
