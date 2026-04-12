package io.github.ninbyo02.lami.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ninbyo02.lami.ui.common.headerAvatarModifier
import io.github.ninbyo02.lami.viewmodels.LamiState
import io.github.ninbyo02.lami.viewmodels.LamiStatus
import io.github.ninbyo02.lami.viewmodels.ModelInfo

@Composable
fun LamiHeaderStatus(
    baseUrl: String,
    selectedModel: String?,
    lastError: String?,
    lamiStatus: LamiStatus,
    lamiState: LamiState,
    availableModels: List<ModelInfo>,
    onSelectModel: (String) -> Unit,
    onNavigateSettings: () -> Unit,
    selectedInferenceTarget: InferenceTarget = InferenceTarget.SERVER,
    localBaseModelDisplayName: String? = null,
    onSelectInferenceTarget: (InferenceTarget) -> Unit = {},
    localInferenceEngineState: LocalInferenceEngineState = LocalInferenceEngineState.UNINITIALIZED,
    debugOverlayEnabled: Boolean = true,
    syncEpochMs: Long = 0L,
    initialAvatarSize: Dp = 64.dp,
    minAvatarSize: Dp = 48.dp,
    maxAvatarSize: Dp = 64.dp,
    showAvatar: Boolean = true,
    onOpenControl: () -> Unit = {},
    statusTitleOverride: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            // アバター頭頂部の見切れを防ぐため、ヘッダー行のクリップを無効化
            .graphicsLayer { clip = false },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showAvatar) {
            HeaderAvatar(
                baseUrl = baseUrl,
                selectedModel = selectedModel,
                lastError = lastError,
                lamiStatus = lamiStatus,
                lamiState = lamiState,
                availableModels = availableModels,
                onSelectModel = onSelectModel,
                onNavigateSettings = onNavigateSettings,
                selectedInferenceTarget = selectedInferenceTarget,
                onSelectInferenceTarget = onSelectInferenceTarget,
                localInferenceEngineState = localInferenceEngineState,
                debugOverlayEnabled = debugOverlayEnabled,
                syncEpochMs = syncEpochMs,
                initialAvatarSize = initialAvatarSize,
                minAvatarSize = minAvatarSize,
                maxAvatarSize = maxAvatarSize,
            )
        }
        HeaderStatusText(
            selectedModel = selectedModel,
            selectedInferenceTarget = selectedInferenceTarget,
            localBaseModelDisplayName = localBaseModelDisplayName,
            lamiStatus = lamiStatus,
            lamiState = lamiState,
            onOpenControl = onOpenControl,
            statusTitleOverride = statusTitleOverride,
        )
    }
}

@Composable
fun HeaderAvatar(
    baseUrl: String,
    selectedModel: String?,
    lastError: String?,
    lamiStatus: LamiStatus,
    lamiState: LamiState,
    availableModels: List<ModelInfo>,
    onSelectModel: (String) -> Unit,
    onNavigateSettings: () -> Unit,
    selectedInferenceTarget: InferenceTarget = InferenceTarget.SERVER,
    onSelectInferenceTarget: (InferenceTarget) -> Unit = {},
    localInferenceEngineState: LocalInferenceEngineState = LocalInferenceEngineState.UNINITIALIZED,
    debugOverlayEnabled: Boolean = true,
    syncEpochMs: Long = 0L,
    initialAvatarSize: Dp = 64.dp,
    minAvatarSize: Dp = 48.dp,
    maxAvatarSize: Dp = 64.dp,
    applyHeaderAvatarModifier: Boolean = true,
    openControlRequestKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val avatarModifier = if (applyHeaderAvatarModifier) {
        modifier.headerAvatarModifier()
    } else {
        modifier
    }

    LamiAvatar(
        baseUrl = baseUrl,
        selectedModel = selectedModel,
        lastError = lastError,
        lamiStatus = lamiStatus,
        lamiState = lamiState,
        availableModels = availableModels,
        initialAvatarSize = initialAvatarSize,
        minAvatarSize = minAvatarSize,
        maxAvatarSize = maxAvatarSize,
        onSelectModel = onSelectModel,
        onNavigateSettings = onNavigateSettings,
        selectedInferenceTarget = selectedInferenceTarget,
        onSelectInferenceTarget = onSelectInferenceTarget,
        localInferenceEngineState = localInferenceEngineState,
        debugOverlayEnabled = debugOverlayEnabled,
        syncEpochMs = syncEpochMs,
        openControlRequestKey = openControlRequestKey,
        modifier = avatarModifier
            // 上端見切れを抑えるため、アバター側で安全マージンを追加確保
            .padding(top = 3.dp),
    )
}

@Composable
fun HeaderStatusText(
    selectedModel: String?,
    selectedInferenceTarget: InferenceTarget,
    localBaseModelDisplayName: String?,
    lamiStatus: LamiStatus,
    lamiState: LamiState,
    onOpenControl: () -> Unit,
    statusTitleOverride: String? = null,
) {
    val statusUi = rememberLamiStatusUi(
        status = lamiStatus,
        lamiState = lamiState
    )
    val modelDisplayName = remember(selectedModel, selectedInferenceTarget, localBaseModelDisplayName) {
        when (selectedInferenceTarget) {
            InferenceTarget.LOCAL -> compactHeaderModelName(localBaseModelDisplayName)
            InferenceTarget.SERVER -> compactHeaderModelName(selectedModel)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            modifier = Modifier
                .semantics {
                    contentDescription = "$modelDisplayName。Lami コントロールを開く"
                }
                .clickable(role = Role.Button, onClick = onOpenControl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (selectedInferenceTarget == InferenceTarget.LOCAL) Icons.Outlined.Memory else Icons.Outlined.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = modelDisplayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = statusTitleOverride ?: statusUi.title,
                style = MaterialTheme.typography.titleSmall,
                color = statusUi.titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val subtitleText = statusUi.subtitle.orEmpty()
        val subtitleAlpha = if (statusUi.subtitle == null) 0f else 1f
        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodySmall.copy(
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 0.95f
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(subtitleAlpha)
        )
    }
}

private fun compactHeaderModelName(raw: String?): String = raw
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?.removeSuffix(".litertlm")
    ?.replace("-it-int4", "")
    ?: "—"
