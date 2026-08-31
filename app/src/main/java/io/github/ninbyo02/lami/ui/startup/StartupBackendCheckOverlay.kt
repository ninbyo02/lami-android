package io.github.ninbyo02.lami.ui.startup

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteS1AppHistory
import io.github.ninbyo02.lami.ui.screens.settings.LocalBackendRuntimeEvidence
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val STARTUP_CHECK_TIMEOUT_MS = 1_900L
private const val RESOLVED_DISPLAY_MS = 650L
private const val STEP_PACING_MS = 120L
private const val ROW_REVEAL_STAGGER_MS = 80L
private val LamiPurple80 = Color(0xFFD0BCFF)
private val LamiDark = Color(0xFF141218)

internal fun startupBackendRevealOrder(): List<StartupBackend> =
    listOf(StartupBackend.NPU, StartupBackend.GPU, StartupBackend.CPU)

internal suspend fun runStartupBackendChecks(
    timeoutMillis: Long,
    resolvedDisplayMillis: Long,
    npuModelConfigured: Boolean = false,
    genericModelConfigured: Boolean = false,
    loadEvidence: suspend () -> LocalBackendRuntimeEvidence,
    onSequenceChanged: (StartupBackendCheckSequence) -> Unit,
    onFinished: () -> Unit,
) {
    var sequence = StartupBackendCheckSequence.initial()
    try {
        val evidence = withTimeoutOrNull(timeoutMillis) { loadEvidence() }
        if (evidence == null) {
            sequence = sequence.timeout()
            onSequenceChanged(sequence)
        } else {
            startupBackendAvailability(
                evidence = evidence,
                npuModelConfigured = npuModelConfigured,
                genericModelConfigured = genericModelConfigured,
            ).forEachIndexed { index, (backend, available) ->
                sequence = sequence.resolve(backend, available)
                onSequenceChanged(sequence)
                if (index < StartupBackend.entries.lastIndex) delay(STEP_PACING_MS)
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        sequence = sequence.timeout()
        onSequenceChanged(sequence)
    }
    delay(resolvedDisplayMillis)
    onFinished()
}

@Composable
fun StartupBackendSplash(
    context: Context,
    settingsPreferences: SettingsPreferences,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sequence by remember { mutableStateOf(StartupBackendCheckSequence.initial()) }
    var visibleBackendCount by remember { mutableStateOf(0) }
    LaunchedEffect(context, settingsPreferences) {
        startupBackendRevealOrder().forEachIndexed { index, _ ->
            visibleBackendCount = index + 1
            if (index < StartupBackend.entries.lastIndex) {
                delay(ROW_REVEAL_STAGGER_MS)
            }
        }
        val (npuModelPath, genericModelPath) = withContext(Dispatchers.IO) {
            settingsPreferences.getValidLocalBaseModelPathOrNull() to
                settingsPreferences.getValidLocalGenericModelPathOrNull()
        }
        runStartupBackendChecks(
            timeoutMillis = STARTUP_CHECK_TIMEOUT_MS,
            resolvedDisplayMillis = RESOLVED_DISPLAY_MS,
            npuModelConfigured = npuModelPath != null,
            genericModelConfigured = genericModelPath != null,
            loadEvidence = {
                withContext(Dispatchers.IO) {
                    NpuStandardRouteS1AppHistory.runtimeEvidence(context, npuModelPath)
                }
            },
            onSequenceChanged = { sequence = it },
            onFinished = onFinished,
        )
    }
    Box(modifier.fillMaxSize().background(LamiDark)) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "LAMI",
                color = LamiPurple80,
                fontSize = 42.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 5.sp,
            )
            Spacer(Modifier.height(40.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "ローカル実行環境",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelMedium,
                )
                sequence.items.forEachIndexed { index, item ->
                    AnimatedVisibility(
                        visible = index < visibleBackendCount,
                        enter = fadeIn() + slideInHorizontally(
                            initialOffsetX = { fullWidth -> -fullWidth / 3 },
                        ),
                    ) {
                        BackendStatusRow(item)
                    }
                }
                Spacer(Modifier.height(14.dp))
                AnimatedVisibility(
                    visible = sequence.canContinue,
                    enter = fadeIn() + scaleIn(initialScale = 0.94f),
                ) {
                    Text(
                        text = "✓  ${startupCompletionLabelFor(sequence)}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackendStatusRow(item: StartupBackendCheckItem) {
    val statusColor = when (item.status) {
        StartupBackendStatus.CHECKING -> LamiPurple80
        StartupBackendStatus.AVAILABLE -> Color(0xFF81C784)
        StartupBackendStatus.UNAVAILABLE -> Color.White.copy(alpha = 0.45f)
    }
    Row(
        modifier = Modifier.width(220.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.backend.displayName,
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodyMedium,
        )
        when (item.status) {
            StartupBackendStatus.CHECKING -> CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = statusColor,
                strokeWidth = 1.5.dp,
            )
            StartupBackendStatus.AVAILABLE -> Text("✓", color = statusColor, fontSize = 16.sp)
            StartupBackendStatus.UNAVAILABLE -> Text("—", color = statusColor, fontSize = 16.sp)
        }
    }
}
