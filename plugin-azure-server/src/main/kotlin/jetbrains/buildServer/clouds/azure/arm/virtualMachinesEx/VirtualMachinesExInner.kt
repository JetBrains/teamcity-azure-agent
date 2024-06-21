package jetbrains.buildServer.clouds.azure.arm.virtualMachinesEx

import com.google.common.reflect.TypeToken
import com.microsoft.azure.CloudException
import com.microsoft.azure.management.compute.InstanceViewTypes
import com.microsoft.azure.management.compute.implementation.ComputeManagementClientImpl
import com.microsoft.azure.management.resources.fluentcore.collection.InnerSupportsGet
import com.microsoft.rest.RestException
import com.microsoft.rest.ServiceCallback
import com.microsoft.rest.ServiceFuture
import com.microsoft.rest.ServiceResponse
import com.microsoft.rest.Validator
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query
import rx.Observable
import java.io.IOException

class VirtualMachinesExInner(
    private val client: ComputeManagementClientImpl
) : InnerSupportsGet<VirtualMachineExInner>{
    private val service : VirtualMachinesExService = client.retrofit().create(VirtualMachinesExService::class.java);

    fun updateAsync(resourceGroupName: String, vmName: String, parameters: VirtualMachineExUpdate): Observable<VirtualMachineExInner> {
        return this.updateWithServiceResponseAsync(resourceGroupName, vmName, parameters)
            .map { response -> response.body() }
    }

    fun updateRawAsync(resourceGroupName: String, vmName: String, raw: Map<String, Any>): Observable<Map<String, Any>> {
        return this.updateRawWithServiceResponseAsync(resourceGroupName, vmName, raw)
            .map { response -> response.body() }
    }

    fun getByResourceGroupRaw(resourceGroupName: String, name: String): Map<String, Any> =
        getByResourceGroupWithRawServiceResponseAsync(resourceGroupName, name).toBlocking().single().body()

    override fun getByResourceGroup(resourceGroupName: String, name: String): VirtualMachineExInner =
        getByResourceGroupWithServiceResponseAsync(resourceGroupName, name).toBlocking().single().body()

    override fun getByResourceGroupAsync(resourceGroupName: String, name: String): Observable<VirtualMachineExInner> =
        getByResourceGroupWithServiceResponseAsync(resourceGroupName, name).map { it.body() }

    override fun getByResourceGroupAsync(resourceGroupName: String, name: String, serviceCallback: ServiceCallback<VirtualMachineExInner>): ServiceFuture<VirtualMachineExInner> =
        ServiceFuture.fromResponse(getByResourceGroupWithServiceResponseAsync(resourceGroupName, name))

    fun updateWithServiceResponseAsync(resourceGroupName: String, vmName: String, parameters: VirtualMachineExUpdate): Observable<ServiceResponse<VirtualMachineExInner>> {
        requireNotNull(client.subscriptionId()) { "Parameter this.client.subscriptionId() is required and cannot be null." }
        Validator.validate(parameters)
        val apiVersion = "2024-03-01"
        val observable = this.service.update(
            resourceGroupName,
            vmName,
            client.subscriptionId(),
            parameters,
            apiVersion,
            client.acceptLanguage(),
            client.userAgent()
        )
        return client
            .getAzureClient()
            .getPutOrPatchResultAsync(observable, object : TypeToken<VirtualMachineExInner?>() {}.type)
    }

    fun updateRawWithServiceResponseAsync(resourceGroupName: String, vmName: String, raw: Map<String, Any>): Observable<ServiceResponse<Map<String, Any>>> {
        requireNotNull(client.subscriptionId()) { "Parameter this.client.subscriptionId() is required and cannot be null." }
        val apiVersion = "2024-03-01"
        val observable = this.service.updateRaw(
            resourceGroupName,
            vmName,
            client.subscriptionId(),
            raw,
            apiVersion,
            client.acceptLanguage(),
            client.userAgent()
        )
        return client
            .getAzureClient()
            .getPutOrPatchResultAsync(observable, object : TypeToken<Map<String, Any>>() {}.type)
    }

    fun getByResourceGroupWithServiceResponseAsync(resourceGroupName: String, vmName: String): Observable<ServiceResponse<VirtualMachineExInner>> {
        requireNotNull(resourceGroupName) { "Parameter resourceGroupName is required and cannot be null." }
        requireNotNull(vmName) { "Parameter vmName is required and cannot be null." }
        requireNotNull(client.subscriptionId()) { "Parameter this.client.subscriptionId() is required and cannot be null." }
        val apiVersion = "2024-03-01"
        return service.getByResourceGroup(
            resourceGroupName,
            vmName,
            client.subscriptionId(),
            null,
            apiVersion,
            client.acceptLanguage(),
            client.userAgent()
        ).flatMap { response ->
            try {
                val clientResponse = getByResourceGroup(response)
                Observable.just(clientResponse)
            } catch (throwable: Throwable) {
                Observable.error(throwable)
            }
        }
    }

    @Throws(CloudException::class, IOException::class, IllegalArgumentException::class)
    private fun getByResourceGroup(response: Response<ResponseBody>): ServiceResponse<VirtualMachineExInner> {
        return client
            .restClient()
            .responseBuilderFactory()
            .newInstance<VirtualMachineExInner, RestException>(client.serializerAdapter())
            .register(200, object : TypeToken<VirtualMachineExInner>() {}.type)
            .registerError(CloudException::class.java)
            .build(response)
    }

    fun getByResourceGroupWithRawServiceResponseAsync(resourceGroupName: String, vmName: String): Observable<ServiceResponse<Map<String, Any>>> {
        requireNotNull(resourceGroupName) { "Parameter resourceGroupName is required and cannot be null." }
        requireNotNull(vmName) { "Parameter vmName is required and cannot be null." }
        requireNotNull(client.subscriptionId()) { "Parameter this.client.subscriptionId() is required and cannot be null." }
        val apiVersion = "2024-03-01"
        return service.getByResourceGroup(
            resourceGroupName,
            vmName,
            client.subscriptionId(),
            null,
            apiVersion,
            client.acceptLanguage(),
            client.userAgent()
        ).flatMap { response ->
            try {
                val clientResponse = getByResourceGroupRaw(response)
                Observable.just(clientResponse)
            } catch (throwable: Throwable) {
                Observable.error(throwable)
            }
        }
    }

    @Throws(CloudException::class, IOException::class, IllegalArgumentException::class)
    private fun getByResourceGroupRaw(response: Response<ResponseBody>): ServiceResponse<Map<String, Any>> {
        return client
            .restClient()
            .responseBuilderFactory()
            .newInstance<Map<String, Any>, RestException>(client.serializerAdapter())
            .register(200, object : TypeToken<Map<String, Any>>() {}.type)
            .registerError(CloudException::class.java)
            .build(response)
    }

    interface VirtualMachinesExService {
        @Headers("Content-Type: application/json; charset=utf-8", "x-ms-logging-context: com.microsoft.azure.management.compute.VirtualMachines update")
        @PATCH("subscriptions/{subscriptionId}/resourceGroups/{resourceGroupName}/providers/Microsoft.Compute/virtualMachines/{vmName}")
        fun update(
            @Path("resourceGroupName") resourceGroupName: String,
            @Path("vmName") vmName: String,
            @Path("subscriptionId") subscriptionId: String,
            @Body update: VirtualMachineExUpdate,
            @Query("api-version") apiVersion: String,
            @Header("accept-language") acceptLanguage: String,
            @Header("User-Agent") userAgent: String): Observable<Response<ResponseBody>>

        @Headers("Content-Type: application/json; charset=utf-8", "x-ms-logging-context: com.microsoft.azure.management.compute.VirtualMachines update")
        @PATCH("subscriptions/{subscriptionId}/resourceGroups/{resourceGroupName}/providers/Microsoft.Compute/virtualMachines/{vmName}")
        fun updateRaw(
            @Path("resourceGroupName") resourceGroupName: String,
            @Path("vmName") vmName: String,
            @Path("subscriptionId") subscriptionId: String,
            @Body update: Map<@JvmSuppressWildcards String, @JvmSuppressWildcards Any>,
            @Query("api-version") apiVersion: String,
            @Header("accept-language") acceptLanguage: String,
            @Header("User-Agent") userAgent: String): Observable<Response<ResponseBody>>

        @Headers("Content-Type: application/json; charset=utf-8", "x-ms-logging-context: com.microsoft.azure.management.compute.VirtualMachines getByResourceGroup")
        @GET("subscriptions/{subscriptionId}/resourceGroups/{resourceGroupName}/providers/Microsoft.Compute/virtualMachines/{vmName}")
        fun getByResourceGroup(
            @Path("resourceGroupName") resourceGroupName: String,
            @Path("vmName") vmName: String,
            @Path("subscriptionId") subscriptionId: String,
            @Query("\$expand") expand: InstanceViewTypes?,
            @Query("api-version") apiVersion: String,
            @Header("accept-language") acceptLanguage: String,
            @Header("User-Agent") userAgent: String
        ): Observable<Response<ResponseBody>>
    }
}
