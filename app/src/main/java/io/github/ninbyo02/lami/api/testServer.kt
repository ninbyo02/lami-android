package io.github.ninbyo02.lami.api

import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

fun generateOllamaText(prompt: String) {
    val request = OllamaRequest(
        model = "qwen2.5-coder:0.5b",
        messages = listOf(OllamaChatMessage(role = "user", content = prompt)),
    )

    RetrofitClient.instance.generateText(request).enqueue(object : Callback<OllamaResponse> {
        override fun onResponse(call: Call<OllamaResponse>, response: Response<OllamaResponse>) {
            if (response.isSuccessful) {
                println("Generated: ${response.body()?.message?.content}")

            } else {
                println("Failed: ${response.errorBody()?.string()}")
            }
        }

        override fun onFailure(call: Call<OllamaResponse>, t: Throwable) {
            Log.e("OllamaError", "Request failed: ${t.message}")
        }
    })
}

fun main(){
    generateOllamaText("Heyyy")
}