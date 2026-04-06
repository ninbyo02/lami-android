package io.github.ninbyo02.lami.ui.screens.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal interface LocalStreamingRunner<T> {
    suspend fun run(
        prompt: String,
        localBaseModelFilePath: String?,
        localBaseModelDisplayName: String?,
        onPartial: (String) -> Unit = {},
    ): T?
}

internal class DefaultLocalStreamingRunner<T>(
    private val timeoutMs: Long,
    private val runInference: suspend (
        prompt: String,
        localBaseModelFilePath: String?,
        localBaseModelDisplayName: String?,
        onPartial: (String) -> Unit,
    ) -> T,
) : LocalStreamingRunner<T> {
    override suspend fun run(
        prompt: String,
        localBaseModelFilePath: String?,
        localBaseModelDisplayName: String?,
        onPartial: (String) -> Unit,
    ): T? = withContext(Dispatchers.IO) {
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<T> {
            runBlocking {
                runInference(
                    prompt = prompt,
                    localBaseModelFilePath = localBaseModelFilePath,
                    localBaseModelDisplayName = localBaseModelDisplayName,
                    onPartial = onPartial,
                )
            }
        }
        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            null
        } finally {
            future.cancel(true)
            executor.shutdownNow()
        }
    }
}
