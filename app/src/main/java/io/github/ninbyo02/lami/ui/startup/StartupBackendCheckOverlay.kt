package io.github.ninbyo02.lami.ui.startup

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS1AppHistory
import io.github.ninbyo02.lami.ui.screens.settings.LocalBackendRuntimeEvidence
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val STARTUP_CHECK_TIMEOUT_MS = 1_900L
private const val RESOLVED_DISPLAY_MS = 500L
private const val STEP_PACING_MS = 120L
private val LamiPurple80 = Color(0xFFD0BCFF)
private val LamiDark = Color(0xFF141218)

@Composable
fun StartupBackendSplash(
    context: Context,
    settingsPreferences: SettingsPreferences,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sequence by remember { mutableStateOf(StartupBackendCheckSequence.initial()) }
    LaunchedEffect(context, settingsPreferences) {
        runCatching {
            val completed = withTimeoutOrNull(STARTUP_CHECK_TIMEOUT_MS) {
                val evidence = runCatching {
                    val modelPath = settingsPreferences.getValidLocalBaseModelPathOrNull()
                    NpuStandardRouteS1AppHistory.runtimeEvidence(context, modelPath)
                }.getOrElse { LocalBackendRuntimeEvidence() }
                startupBackendAvailability(evidence).forEachIndexed { index, (backend, available) ->
                    sequence = sequence.resolve(backend, available)
                    if (index < StartupBackend.entries.lastIndex) delay(STEP_PACING_MS)
                }
            } != null
            if (!completed) sequence = sequence.timeout()
        }.onFailure { sequence = sequence.timeout() }
        delay(RESOLVED_DISPLAY_MS)
        onFinished()
    }
    Box(modifier.fillMaxSize().background(LamiDark)) {
        Text(
            text = "LAMI", color = LamiPurple80, fontSize = 42.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 5.sp,
            modifier = Modifier.align(Alignment.Center),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text("ローカル実行環境", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelMedium)
            sequence.items.forEach { BackendStatusRow(it) }
        }
    }
}

@Composable
private fun BackendStatusRow(item: StartupBackendCheckItem) {
    val color = when (item.status) {
        StartupBackendStatus.CHECKING -> LamiPurple80
        StartupBackendStatus.AVAILABLE -> Color(0xFF81C784)
        StartupBackendStatus.UNAVAILABLE -> Color.White.copy(alpha = 0.45f)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(9.dp))
        Text(item.backend.displayName, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(42.dp))
        Text(item.status.label, color = color, fontSize = 12.sp, style = MaterialTheme.typography.labelMedium)
    }
}
