package io.github.ninbyo02.lami.api

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Streaming
import okhttp3.ResponseBody

// Define the request body model
data class OllamaRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val images: List<String>? = null,
)

// Define the response model
data class OllamaResponse(
    val response: String
)

// Retrofit API interface
interface OllamaApiService {
    @Headers("Content-Type: application/json")
    @POST("api/generate")
    fun generateText(@Body request: OllamaRequest): Call<OllamaResponse>


    @Streaming
    @Headers("Content-Type: application/json")
    @POST("api/generate")
    fun generateTextStream(@Body request: OllamaRequest): Call<ResponseBody>

    @GET("/api/tags") // Adjust the path as needed
    fun getModels(): Call<List<String>> // Returns a list of strings
}
