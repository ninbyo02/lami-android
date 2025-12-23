package com.sonusid.ollama.api

import com.sonusid.ollama.db.repository.BaseUrlProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val DEFAULT_BASE_URL = "http://localhost:11434/" // Default URL
    private var baseUrl: String = DEFAULT_BASE_URL

    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private var retrofit: Retrofit? = null
    private val retrofitMutex = Mutex()

    suspend fun initialize(baseUrlProvider: BaseUrlProvider) {
        retrofitMutex.withLock {
            val activeUrl = baseUrlProvider.getActiveOrFirst()?.url
            val resolvedBaseUrl = normalizeBaseUrl(activeUrl)
            if (retrofit == null || baseUrl != resolvedBaseUrl) {
                baseUrl = resolvedBaseUrl
                retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            }
        }
    }

    suspend fun refreshBaseUrl(baseUrlProvider: BaseUrlProvider) {
        initialize(baseUrlProvider)
    }

    fun currentBaseUrl(): String = baseUrl

    val instance: OllamaApiService
        get() = retrofit?.create(OllamaApiService::class.java)
            ?: error("RetrofitClient must be initialized!")

    private fun normalizeBaseUrl(activeUrl: String?): String {
        val cleanedUrl = activeUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BASE_URL.trimEnd('/')
        val withScheme = if (cleanedUrl.startsWith("http://") || cleanedUrl.startsWith("https://")) {
            cleanedUrl
        } else {
            "http://$cleanedUrl"
        }
        return "$withScheme/"
    }
}
