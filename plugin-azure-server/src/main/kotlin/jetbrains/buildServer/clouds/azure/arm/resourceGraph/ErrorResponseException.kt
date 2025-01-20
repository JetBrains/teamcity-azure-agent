package jetbrains.buildServer.clouds.azure.arm.resourceGraph

import com.fasterxml.jackson.annotation.JsonProperty
import com.microsoft.rest.RestException
import okhttp3.ResponseBody
import retrofit2.Response

class ErrorResponseException : RestException {
    constructor(message: String, response: Response<ResponseBody>) : super(message, response)
    constructor(message: String, response: Response<ResponseBody>, body: ErrorResponse) : super(message, response, body)

    override fun body(): ErrorResponse {
        return super.body() as ErrorResponse
    }
}

class ErrorResponse(
    @JsonProperty(value = "error", required = true)
    val error: Error
)

class Error(
    @JsonProperty(value = "code", required = true)
    val code: String,

    @JsonProperty(value = "message", required = true)
    val message: String,

    @JsonProperty(value = "details", required = true)
    val details: List<ErrorDetails>?
)

class ErrorDetails(
    @JsonProperty(value = "")
    val additionalProperties: Map<String, Any>,

    @JsonProperty(value = "code", required = true)
    val code: String,

    @JsonProperty(value = "message", required = true)
    val message: String
)
