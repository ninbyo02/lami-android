package io.github.ninbyo02.lami.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import io.github.ninbyo02.lami.ui.screens.settings.PreferredBackendDryRunSetting

internal data class NpuS1RepeatedRunUi(
    val state: NpuS1RepeatedRunState,
    val preferredBackendSetting: PreferredBackendDryRunSetting,
    val npuStandardRouteMode: NpuStandardRouteMode,
    val selectedMode: NpuS1RepeatedRunMode,
    val selectedPrompt: String,
    val selectedRunCount: Int,
    val selectedWaitMs: Long,
    val running: Boolean,
    val blockedByGeneration: Boolean,
)

internal data class NpuS1RepeatedRunActions(
    val onModeChange: (NpuS1RepeatedRunMode) -> Unit,
    val onPromptChange: (String) -> Unit,
    val onRunCountChange: (Int) -> Unit,
    val onWaitMsChange: (Long) -> Unit,
    val onStart: () -> Unit,
    val onCancel: () -> Unit,
    val onCopySummary: (() -> Unit)? = null,
    val onCopyFullDump: (() -> Unit)? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NpuS1RepeatedRunDevSection(
    ui: NpuS1RepeatedRunUi,
    actions: NpuS1RepeatedRunActions,
) {
    val state = ui.state
    val preferredBackendSetting = ui.preferredBackendSetting
    val npuStandardRouteMode = ui.npuStandardRouteMode
    val selectedMode = ui.selectedMode
    val selectedPrompt = ui.selectedPrompt
    val selectedRunCount = ui.selectedRunCount
    val selectedWaitMs = ui.selectedWaitMs
    val running = ui.running
    val blockedByGeneration = ui.blockedByGeneration
    val onModeChange = actions.onModeChange
    val onPromptChange = actions.onPromptChange
    val onRunCountChange = actions.onRunCountChange
    val onWaitMsChange = actions.onWaitMsChange
    val onStart = actions.onStart
    val onCancel = actions.onCancel
    val onCopySummary = actions.onCopySummary
    val onCopyFullDump = actions.onCopyFullDump
    val startGate = npuS1RepeatedRunStartGate(
        preferredBackendSetting = preferredBackendSetting,
        npuStandardRouteMode = npuStandardRouteMode,
        mode = selectedMode,
        runCount = selectedRunCount,
        waitMs = selectedWaitMs,
    )
    val backendDiagnostics = npuS1BackendDiagnosticsForPreferredSetting(
        setting = preferredBackendSetting,
        npuStandardRouteMode = npuStandardRouteMode,
        backendEvidence = if (
            isNpuS1RepeatedRunBackendAllowed(
                npuS1BackendFromPreferredSetting(
                    setting = preferredBackendSetting,
                    npuStandardRouteMode = npuStandardRouteMode,
                ),
            )
        ) {
            NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE
        } else {
            NPU_S1_BACKEND_EVIDENCE_UNAVAILABLE
        },
    )
    val blockedByBackend = startGate.blockedReason == NPU_S1_REPEATED_RUN_BLOCKED_SELECTED_BACKEND_NOT_NPU
    val controlsEnabled = !running && !blockedByGeneration
    val startEnabled = controlsEnabled && startGate.allowed
    InferenceStatsSection(title = "NPU ローカル Stability Test") {
        Text(
            text = "selected_backend=${backendDiagnostics.selectedBackend} " +
                "requested_backend=${backendDiagnostics.requestedBackend} " +
                "effective_backend=${backendDiagnostics.effectiveBackend} " +
                "route_family=${backendDiagnostics.routeFamily}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (blockedByBackend) {
            Text(
                text = "NPU ローカル Stability Test は NPU ローカル / NPU standard route 選択時のみ実行可能",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (!startGate.allowed) {
            Text(
                text = "Safety policy: Reuse or Recreate / 10 runs / wait 500ms以上のみ実行可能",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = "prompt:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NPU_S1_REPEATED_RUN_PROMPT_OPTIONS.forEach { prompt ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedPrompt == prompt,
                    onClick = { onPromptChange(prompt) },
                    enabled = controlsEnabled,
                )
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            text = "run count:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NPU_S1_REPEATED_RUN_COUNT_OPTIONS.forEach { count ->
                FilterChip(
                    selected = selectedRunCount == count,
                    onClick = { onRunCountChange(count) },
                    label = { Text(count.toString()) },
                    enabled = controlsEnabled && count in NPU_S1_REPEATED_RUN_SAFE_COUNT_OPTIONS,
                )
            }
        }
        Text(
            text = "wait:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NPU_S1_REPEATED_RUN_WAIT_MS_OPTIONS.forEach { waitMs ->
                FilterChip(
                    selected = selectedWaitMs == waitMs,
                    onClick = { onWaitMsChange(waitMs) },
                    label = { Text("${waitMs}ms") },
                    enabled = controlsEnabled && waitMs in NPU_S1_REPEATED_RUN_SAFE_WAIT_MS_OPTIONS,
                )
            }
        }
        Text(
            text = "実行モード:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(NpuS1RepeatedRunMode.REUSE, NpuS1RepeatedRunMode.RECREATE).forEach { mode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selectedMode == mode,
                    onClick = { onModeChange(mode) },
                    enabled = controlsEnabled && mode in NPU_S1_REPEATED_RUN_SAFE_MODE_OPTIONS,
                )
                Text(
                    text = mode.displayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onStart,
                enabled = startEnabled,
            ) {
                Text("NPU ローカル安定性テスト開始")
            }
            TextButton(
                onClick = onCancel,
                enabled = running,
            ) {
                Text("キャンセル")
            }
        }
        if (onCopySummary != null || onCopyFullDump != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (onCopySummary != null) {
                    TextButton(onClick = onCopySummary) {
                        Text("Copy Stability Summary")
                    }
                }
                if (onCopyFullDump != null) {
                    TextButton(onClick = onCopyFullDump) {
                        Text("Copy Stability Full Dump")
                    }
                }
            }
        }
        Text(
            text = if (blockedByGeneration) {
                "生成完了後に実行してください"
            } else {
                "DEV専用の直列テストです。Reuse は既存NPUエンジン再利用の安定性検証用です。失敗時は停止します。通常チャット履歴、TTS、DB保存には使いません。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InferenceStatRow(
            label = "NPU ローカル Stability Test",
            value = formatNpuS1RepeatedRunDiagnosticsForDev(state),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NpuLongGenerationDevSection(
    state: NpuLongGenerationState,
    preferredBackendSetting: PreferredBackendDryRunSetting,
    npuStandardRouteMode: NpuStandardRouteMode,
    running: Boolean,
    blockedByGeneration: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onCopySummary: (() -> Unit)? = null,
    onCopyFullDump: (() -> Unit)? = null,
) {
    val startGate = npuLongGenerationStartGate(
        preferredBackendSetting = preferredBackendSetting,
        npuStandardRouteMode = npuStandardRouteMode,
    )
    val backendDiagnostics = npuS1BackendDiagnosticsForPreferredSetting(
        setting = preferredBackendSetting,
        npuStandardRouteMode = npuStandardRouteMode,
        backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
    )
    val controlsEnabled = !running && !blockedByGeneration
    val startEnabled = controlsEnabled && startGate.allowed
    InferenceStatsSection(title = "NPU ローカル Long Generation Test") {
        Text(
            text = "selected_backend=${backendDiagnostics.selectedBackend} requested_backend=${backendDiagnostics.requestedBackend}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "token_plan=${NPU_LONG_GENERATION_TOKEN_PLAN.joinToString(",")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!startGate.allowed) {
            Text(
                text = "NPU ローカル Long Generation Test は NPU ローカル / DEV NPU 選択時のみ実行可能",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onStart,
                enabled = startEnabled,
            ) {
                Text("NPU ローカル長文生成テスト開始")
            }
            TextButton(
                onClick = onCancel,
                enabled = running,
            ) {
                Text("キャンセル")
            }
        }
        if (onCopySummary != null || onCopyFullDump != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (onCopySummary != null) {
                    TextButton(onClick = onCopySummary) {
                        Text("Copy Long Summary")
                    }
                }
                if (onCopyFullDump != null) {
                    TextButton(onClick = onCopyFullDump) {
                        Text("Copy Long Full Dump")
                    }
                }
            }
        }
        Text(
            text = if (blockedByGeneration) {
                "生成完了後に実行してください"
            } else {
                "DEV専用の長文生成比較です。32/128/512 tokens を順に実行し、UI/TTS/DB保存には使いません。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InferenceStatRow(
            label = "NPU ローカル Long Generation Test",
            value = formatNpuLongGenerationDiagnosticsForDev(state),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NpuNonStreamingRepeatedStabilityDevSection(
    state: NpuNonStreamingRepeatedStabilityState,
    preferredBackendSetting: PreferredBackendDryRunSetting,
    npuStandardRouteMode: NpuStandardRouteMode,
    running: Boolean,
    blockedByGeneration: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onCopySummary: (() -> Unit)? = null,
    onCopyFullDump: (() -> Unit)? = null,
) {
    val startGate = npuLongGenerationStartGate(
        preferredBackendSetting = preferredBackendSetting,
        npuStandardRouteMode = npuStandardRouteMode,
    )
    val backendDiagnostics = npuS1BackendDiagnosticsForPreferredSetting(
        setting = preferredBackendSetting,
        npuStandardRouteMode = npuStandardRouteMode,
        backendEvidence = NpuStandardRouteS1Contract.NPU_BACKEND_EVIDENCE,
    )
    val controlsEnabled = !running && !blockedByGeneration
    val startEnabled = controlsEnabled && startGate.allowed
    InferenceStatsSection(title = NPU_NON_STREAMING_REPEATED_STABILITY_TEST_NAME) {
        Text(
            text = "selected_backend=${backendDiagnostics.selectedBackend} " +
                "requested_backend=${backendDiagnostics.requestedBackend} " +
                "route_type=$NPU_NON_STREAMING_REPEATED_STABILITY_ROUTE_TYPE",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "streaming=false pseudo_streaming=false tts=false db=false markdown=false fallback_allowed=false",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!startGate.allowed) {
            Text(
                text = "NPU Non-Streaming Repeat Test は NPU ローカル / DEV NPU 選択時のみ実行可能",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onStart,
                enabled = startEnabled,
            ) {
                Text(NPU_NON_STREAMING_REPEATED_STABILITY_RUN_LABEL)
            }
            TextButton(
                onClick = onCancel,
                enabled = running,
            ) {
                Text("キャンセル")
            }
        }
        if (onCopySummary != null || onCopyFullDump != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (onCopySummary != null) {
                    TextButton(onClick = onCopySummary) {
                        Text(NPU_NON_STREAMING_REPEATED_STABILITY_COPY_SUMMARY_LABEL)
                    }
                }
                if (onCopyFullDump != null) {
                    TextButton(onClick = onCopyFullDump) {
                        Text(NPU_NON_STREAMING_REPEATED_STABILITY_COPY_FULL_DUMP_LABEL)
                    }
                }
            }
        }
        Text(
            text = if (blockedByGeneration) {
                "生成完了後に実行してください"
            } else {
                "DEV専用の one-shot NPU decode 繰り返しテストです。通常チャット履歴、疑似ストリーミング、TTS、DB保存、markdown には接続しません。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InferenceStatRow(
            label = NPU_NON_STREAMING_REPEATED_STABILITY_TEST_NAME,
            value = formatNpuNonStreamingRepeatedStabilityDiagnosticsForDev(state),
        )
    }
}
