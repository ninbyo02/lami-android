package io.github.ninbyo02.lami.ui.screens.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.ninbyo02.lamigeniexprobe.integration.GenieXBridgeClient
import io.github.ninbyo02.lamigeniexprobe.integration.LamiComputeChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class GenieXChatRoute(
    val compute: LamiComputeChoice,
    val modelReady: Boolean?,
    val backend: GenieXChatBackend,
)

@Composable
internal fun rememberGenieXChatRoute(
    context: Context,
    computeSetting: String,
): GenieXChatRoute? {
    val compute = LamiComputeChoice.entries.firstOrNull { it.wireName == computeSetting }
        ?: return null
    val appContext = context.applicationContext
    val client = remember(appContext) { GenieXBridgeClient(appContext) }
    val backend = remember(client) { GenieXChatBackend(client) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumeCount by remember(client) { mutableStateOf(0) }
    var modelReady by remember(client) { mutableStateOf<Boolean?>(null) }

    DisposableEffect(client) {
        onDispose { client.close() }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeCount++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(client, resumeCount) {
        modelReady = try {
            withContext(Dispatchers.IO) { client.checkModelReady() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }
    return GenieXChatRoute(compute, modelReady, backend)
}
