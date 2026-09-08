package io.github.ninbyo02.lami.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Streaming
import okhttp3.ResponseBody

// Ollama owns chat-template evaluation. The app supplies structured messages only.
data class OllamaChatMessage(
    val role: String,
    val content: String,
    val images: List<String>? = null,
)

data class OllamaOptions(
    @SerializedName("num_predict")
    val numPredict: Int,
)

data class OllamaRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null,
)

data class OllamaResponse(
    val message: OllamaChatMessage,
)

// Retrofit API interface
interface OllamaApiService {
    @Headers("Content-Type: application/json")
    @POST("api/chat")
    fun generateText(@Body request: OllamaRequest): Call<OllamaResponse>


    @Streaming
    @Headers("Content-Type: application/json")
    @POST("api/chat")
    fun generateTextStream(@Body request: OllamaRequest): Call<ResponseBody>

    @GET("/api/tags") // Adjust the path as needed
    fun getModels(): Call<List<String>> // Returns a list of strings
}
