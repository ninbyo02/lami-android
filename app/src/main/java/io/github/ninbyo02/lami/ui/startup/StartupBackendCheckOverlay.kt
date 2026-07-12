package io.github.ninbyo02.lami.ui.startup

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS1AppHistory
import io.github.ninbyo02.lami.ui.screens.settings.LocalBackendRuntimeEvidence
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val STARTUP_CHECK_TIMEOUT_MS = 2_500L
private const val RESOLVED_DISPLAY_MS = 900L
private const val STEP_PACING_MS = 120L

@Composable
fun StartupBackendCheckOverlay(
    context: Context,
    settingsPreferences: SettingsPreferences,
    modifier: Modifier = Modifier,
) {
    var sequence by remember { mutableStateOf(StartupBackendCheckSequence.initial()) }
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(context, settingsPreferences) {
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
        delay(RESOLVED_DISPLAY_MS)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.shadow(8.dp, RoundedCornerShape(18.dp)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = "ローカル実行環境",
                    style = MaterialTheme.typography.labelLarge,
                )
                sequence.items.forEach { item ->
                    BackendStatusRow(item)
                }
            }
        }
    }
}

@Composable
private fun BackendStatusRow(item: StartupBackendCheckItem) {
    val color = when (item.status) {
        StartupBackendStatus.CHECKING -> MaterialTheme.colorScheme.primary
        StartupBackendStatus.AVAILABLE -> Color(0xFF2E7D32)
        StartupBackendStatus.UNAVAILABLE -> MaterialTheme.colorScheme.outline
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = item.backend.displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(42.dp),
        )
        Text(
            text = item.status.label,
            color = color,
            fontSize = 12.sp,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
