package io.github.ninbyo02.lami.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NpuPersistentHolderCreateCloseDevSection(
    state: NpuPersistentHolderCreateCloseProbeState,
    running: Boolean,
    blockedByGeneration: Boolean,
    onStart: () -> Unit,
    onCopySummary: (() -> Unit)? = null,
    onCopyFullDump: (() -> Unit)? = null,
) {
    val diagnostics = state.latestDiagnostics
    val warningText = if (diagnostics?.holderFatalLatch == true || diagnostics?.restartAppRecommended == true) {
        "holder_fatal_latch=true: アプリ再起動推奨"
    } else {
        null
    }
    InferenceStatsSection(title = NPU_PERSISTENT_HOLDER_CREATE_CLOSE_UI_TITLE) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onStart,
                enabled = !running && !blockedByGeneration,
            ) {
                Text(NPU_PERSISTENT_HOLDER_CREATE_CLOSE_RUN_LABEL)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onCopySummary != null) {
                TextButton(onClick = onCopySummary) {
                    Text(NPU_PERSISTENT_HOLDER_CREATE_CLOSE_COPY_SUMMARY_LABEL)
                }
            }
            if (onCopyFullDump != null) {
                TextButton(onClick = onCopyFullDump) {
                    Text(NPU_PERSISTENT_HOLDER_CREATE_CLOSE_COPY_FULL_DUMP_LABEL)
                }
            }
        }
        Text(
            text = if (blockedByGeneration) {
                "他の生成またはDEV診断完了後に実行してください"
            } else {
                "DEV専用診断です。create/close のみを実行し、run/decode/generate は実行しません。通常チャット経路には接続しません。fatal latch が立った場合はアプリ再起動推奨です。npu_decode_called=false / generate_called=false をsummaryで確認してください。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (warningText != null) {
            Text(
                text = warningText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        InferenceStatRow(
            label = "Holder Create/Close Summary",
            value = formatNpuPersistentHolderCreateCloseSummaryForCopy(state),
            emphasizeValue = diagnostics?.holderFatalLatch == true,
        )
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NpuPersistentHolderRunOnceDevSection(
    state: NpuPersistentHolderRunOnceProbeState,
    running: Boolean,
    blockedByGeneration: Boolean,
    onStart: () -> Unit,
    onCopySummary: (() -> Unit)? = null,
    onCopyFullDump: (() -> Unit)? = null,
) {
    val diagnostics = state.latestDiagnostics
    val warningText = if (diagnostics?.holderFatalLatch == true || diagnostics?.restartAppRecommended == true) {
        "holder_fatal_latch=true: アプリ再起動推奨"
    } else {
        null
    }
    InferenceStatsSection(title = NPU_PERSISTENT_HOLDER_RUN_ONCE_UI_TITLE) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onStart,
                enabled = !running && !blockedByGeneration,
            ) {
                Text(NPU_PERSISTENT_HOLDER_RUN_ONCE_RUN_LABEL)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onCopySummary != null) {
                TextButton(onClick = onCopySummary) {
                    Text(NPU_PERSISTENT_HOLDER_RUN_ONCE_COPY_SUMMARY_LABEL)
                }
            }
            if (onCopyFullDump != null) {
                TextButton(onClick = onCopyFullDump) {
                    Text(NPU_PERSISTENT_HOLDER_RUN_ONCE_COPY_FULL_DUMP_LABEL)
                }
            }
        }
        Text(
            text = if (blockedByGeneration) {
                "他の生成またはDEV診断完了後に実行してください"
            } else {
                "DEV専用診断です。holder create → run once → close を1回だけ実行します。multi-turnではなく、通常チャット経路には接続しません。10回連続はまだ禁止で、engine_reuse_observed は unavailable のままです。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (warningText != null) {
            Text(
                text = warningText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        InferenceStatRow(
            label = "Holder Run Once Summary",
            value = formatNpuPersistentHolderRunOnceSummaryForCopy(state),
            emphasizeValue = diagnostics?.holderFatalLatch == true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NpuPersistentHolderTwoTurnDevSection(
    state: NpuPersistentHolderTwoTurnProbeState,
    running: Boolean,
    blockedByGeneration: Boolean,
    onStart: () -> Unit,
    onCopySummary: (() -> Unit)? = null,
    onCopyFullDump: (() -> Unit)? = null,
) {
    val diagnostics = state.latestDiagnostics
    val warningText = if (diagnostics?.holderFatalLatch == true || diagnostics?.restartAppRecommended == true) {
        "holder_fatal_latch=true: アプリ再起動推奨"
    } else {
        null
    }
    InferenceStatsSection(title = NPU_PERSISTENT_HOLDER_TWO_TURN_UI_TITLE) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onStart,
                enabled = !running && !blockedByGeneration,
            ) {
                Text(NPU_PERSISTENT_HOLDER_TWO_TURN_RUN_LABEL)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onCopySummary != null) {
                TextButton(onClick = onCopySummary) {
                    Text(NPU_PERSISTENT_HOLDER_TWO_TURN_COPY_SUMMARY_LABEL)
                }
            }
            if (onCopyFullDump != null) {
                TextButton(onClick = onCopyFullDump) {
                    Text(NPU_PERSISTENT_HOLDER_TWO_TURN_COPY_FULL_DUMP_LABEL)
                }
            }
        }
        Text(
            text = if (blockedByGeneration) {
                "他の生成またはDEV診断完了後に実行してください"
            } else {
                "DEV専用診断です。create 1回、decode 2回、close 1回だけを確認します。10-turnではなく、通常チャット経路には接続しません。persistent reuse証明ではなく、engine_reuse_observed は unavailable のままです。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (warningText != null) {
            Text(
                text = warningText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        InferenceStatRow(
            label = "Holder Two-Turn Summary",
            value = formatNpuPersistentHolderTwoTurnSummaryForCopy(state),
            emphasizeValue = diagnostics?.holderFatalLatch == true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NpuPersistentHolderFiveTurnDevSection(
    state: NpuPersistentHolderFiveTurnProbeState,
    running: Boolean,
    blockedByGeneration: Boolean,
    onStart: () -> Unit,
    onCopySummary: (() -> Unit)? = null,
    onCopyFullDump: (() -> Unit)? = null,
) {
    val diagnostics = state.latestDiagnostics
    val warningText = if (diagnostics?.holderFatalLatch == true || diagnostics?.restartAppRecommended == true) {
        "holder_fatal_latch=true: アプリ再起動推奨"
    } else {
        null
    }
    InferenceStatsSection(title = NPU_PERSISTENT_HOLDER_FIVE_TURN_UI_TITLE) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onStart,
                enabled = !running && !blockedByGeneration,
            ) {
                Text(NPU_PERSISTENT_HOLDER_FIVE_TURN_RUN_LABEL)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onCopySummary != null) {
                TextButton(onClick = onCopySummary) {
                    Text(NPU_PERSISTENT_HOLDER_FIVE_TURN_COPY_SUMMARY_LABEL)
                }
            }
            if (onCopyFullDump != null) {
                TextButton(onClick = onCopyFullDump) {
                    Text(NPU_PERSISTENT_HOLDER_FIVE_TURN_COPY_FULL_DUMP_LABEL)
                }
            }
        }
        Text(
            text = if (blockedByGeneration) {
                "他の生成またはDEV診断完了後に実行してください"
            } else {
                "DEV専用診断です。create 1回、decode 5回、close 1回だけを確認します。10-turnではなく、通常チャット経路には接続しません。persistent reuse証明ではなく、engine_reuse_observed は unavailable のままです。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (warningText != null) {
            Text(
                text = warningText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        InferenceStatRow(
            label = "Holder Five-Turn Summary",
            value = formatNpuPersistentHolderFiveTurnSummaryForCopy(state),
            emphasizeValue = diagnostics?.holderFatalLatch == true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NpuPersistentHolderTenTurnDevSection(
    state: NpuPersistentHolderTenTurnProbeState,
    running: Boolean,
    blockedByGeneration: Boolean,
    onStart: () -> Unit,
    onCopySummary: (() -> Unit)? = null,
    onCopyFullDump: (() -> Unit)? = null,
) {
    val diagnostics = state.latestDiagnostics
    val warningText = if (diagnostics?.holderFatalLatch == true || diagnostics?.restartAppRecommended == true) {
        "holder_fatal_latch=true: アプリ再起動推奨"
    } else {
        null
    }
    InferenceStatsSection(title = NPU_PERSISTENT_HOLDER_TEN_TURN_UI_TITLE) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onStart,
                enabled = !running && !blockedByGeneration,
            ) {
                Text(NPU_PERSISTENT_HOLDER_TEN_TURN_RUN_LABEL)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onCopySummary != null) {
                TextButton(onClick = onCopySummary) {
                    Text(NPU_PERSISTENT_HOLDER_TEN_TURN_COPY_SUMMARY_LABEL)
                }
            }
            if (onCopyFullDump != null) {
                TextButton(onClick = onCopyFullDump) {
                    Text(NPU_PERSISTENT_HOLDER_TEN_TURN_COPY_FULL_DUMP_LABEL)
                }
            }
        }
        Text(
            text = if (blockedByGeneration) {
                "他の生成またはDEV診断完了後に実行してください"
            } else {
                "DEV専用診断です。create 1回、decode 10回、close 1回だけを確認します。通常チャット経路には接続しません。true Engine persistent reuse証明ではなく、engine_reuse_observed は unavailable のままです。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (warningText != null) {
            Text(
                text = warningText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        InferenceStatRow(
            label = "Holder Ten-Turn Summary",
            value = formatNpuPersistentHolderTenTurnSummaryForCopy(state),
            emphasizeValue = diagnostics?.holderFatalLatch == true,
        )
    }
}
