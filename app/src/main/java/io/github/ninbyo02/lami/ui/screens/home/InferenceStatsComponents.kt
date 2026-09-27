package io.github.ninbyo02.lami.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ninbyo02.lami.ui.screens.settings.InferenceStatsDisplayMode
import io.github.ninbyo02.lami.ui.model.InferenceStats
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.ninbyo02.lami.ui.components.InferenceTarget
import io.github.ninbyo02.lami.ui.components.InferenceTargetIcon
import io.github.ninbyo02.lami.ui.util.formatModelName

@Composable
internal fun InferenceStatRow(
    label: String,
    value: String,
    emphasizeValue: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasizeValue) FontWeight.SemiBold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun InferenceStatsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
internal fun InferenceStatsModeSelector(
    selectedMode: InferenceStatsDisplayMode,
    onModeSelected: (InferenceStatsDisplayMode) -> Unit,
) {
    val modeButtons = listOf(
        Triple(
            InferenceStatsDisplayMode.SIMPLE,
            Icons.Outlined.ViewAgenda,
            "シンプル表示",
        ),
        Triple(
            InferenceStatsDisplayMode.DETAILED,
            Icons.AutoMirrored.Outlined.ViewList,
            "詳細表示",
        ),
        Triple(
            InferenceStatsDisplayMode.DEVELOPER,
            Icons.Outlined.Code,
            "開発者表示",
        ),
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modeButtons.forEach { (mode, icon, description) ->
            InferenceStatsModeIconButton(
                icon = icon,
                contentDescription = description,
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
            )
        }
    }
}

@Composable
private fun InferenceStatsModeIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
internal fun InferenceTimingBreakdownSection(stats: InferenceStats) {
    val breakdown = buildInferenceTimeBreakdown(stats) ?: return
    val barColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    InferenceStatsSection(title = "推論時間内訳") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            breakdown.segments.forEach { segment ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InferenceStatRow(
                        label = segment.label,
                        value = "${segment.durationText} / ${segment.percent}%",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(
                                color = trackColor,
                                shape = RoundedCornerShape(999.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(segment.ratio.toFloat().coerceIn(0f, 1f))
                                .height(8.dp)
                                .background(
                                    color = barColor,
                                    shape = RoundedCornerShape(999.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun InferenceContextUsageSection(stats: InferenceStats) {
    val usage = buildContextUsageUi(stats) ?: return
    InferenceStatsSection(title = "コンテキスト使用量") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (usage) {
                is ContextUsageUi.WithMax -> {
                    if (usage.ratio in 0.0..1.0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(999.dp),
                                ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(usage.ratio.toFloat().coerceIn(0f, 1f))
                                    .height(8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(999.dp),
                                    ),
                            )
                        }
                    }
                    Text(
                        text = "${usage.used} / ${usage.max} tokens (${usage.percent}%)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                is ContextUsageUi.Loading -> {
                    Text(
                        text = "使用トークン ${usage.used}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "上限取得中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is ContextUsageUi.WithoutMax -> {
                    Text(
                        text = "使用トークン ${usage.used}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "上限未取得",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun InferenceModelInfoRow(
    stats: InferenceStats,
    inferenceTarget: InferenceTarget,
    onCopyInferenceStats: () -> Unit,
    onCopyGpuDiagnosticKeys: (() -> Unit)? = null,
    onCopyGpuInternalSurfaceKeys: (() -> Unit)? = null,
    onCopyNpuDiagnosticKeys: (() -> Unit)? = null,
) {
    val modelName = formatModelName(stats)
    InferenceStatsSection(title = "モデル情報") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "使用モデル",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InferenceTargetIcon(
                        target = inferenceTarget,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = modelName ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            IconButton(
                onClick = onCopyInferenceStats,
                modifier = Modifier.semantics { contentDescription = "推論統計をコピー" },
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "推論統計をコピー",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onCopyGpuDiagnosticKeys != null) {
            TextButton(
                onClick = onCopyGpuDiagnosticKeys,
                modifier = Modifier.semantics { contentDescription = GPU_DIAGNOSTIC_COPY_BUTTON_LABEL },
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(GPU_DIAGNOSTIC_COPY_BUTTON_LABEL)
            }
        }
        if (onCopyGpuInternalSurfaceKeys != null) {
            TextButton(
                onClick = onCopyGpuInternalSurfaceKeys,
                modifier = Modifier.semantics { contentDescription = GPU_INTERNAL_SURFACE_COPY_BUTTON_LABEL },
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(GPU_INTERNAL_SURFACE_COPY_BUTTON_LABEL)
            }
        }
        if (onCopyNpuDiagnosticKeys != null) {
            TextButton(
                onClick = onCopyNpuDiagnosticKeys,
                modifier = Modifier.semantics { contentDescription = NPU_DIAGNOSTIC_COPY_BUTTON_LABEL },
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(NPU_DIAGNOSTIC_COPY_BUTTON_LABEL)
            }
        }
    }
}

@Composable
internal fun CopyableDebugBlock(
    text: String,
    title: String? = null,
    onCopy: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Red,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Red,
            )
        }
        IconButton(
            onClick = onCopy,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .semantics { contentDescription = "デバッグテキストをコピー" },
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "デバッグテキストをコピー",
                tint = Color.Red,
            )
        }
    }
}
