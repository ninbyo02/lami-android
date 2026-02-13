package com.sonusid.ollama.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonusid.ollama.ui.common.headerAvatarModifier
import com.sonusid.ollama.viewmodels.LamiState
import com.sonusid.ollama.viewmodels.LamiStatus
import com.sonusid.ollama.viewmodels.ModelInfo

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
    debugOverlayEnabled: Boolean = true,
    syncEpochMs: Long = 0L,
    showAvatar: Boolean = true,
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
                debugOverlayEnabled = debugOverlayEnabled,
                syncEpochMs = syncEpochMs,
            )
        }
        HeaderStatusText(
            selectedModel = selectedModel,
            lamiStatus = lamiStatus,
            lamiState = lamiState,
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
    debugOverlayEnabled: Boolean = true,
    syncEpochMs: Long = 0L,
    initialAvatarSize: Dp = 64.dp,
    minAvatarSize: Dp = 48.dp,
    maxAvatarSize: Dp = 64.dp,
    applyHeaderAvatarModifier: Boolean = true,
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
        debugOverlayEnabled = debugOverlayEnabled,
            syncEpochMs = syncEpochMs,
            modifier = avatarModifier
                // 上端見切れを抑えるため、アバター側で安全マージンを追加確保
                .padding(top = 3.dp),
    )
}

@Composable
fun HeaderStatusText(
    selectedModel: String?,
    lamiStatus: LamiStatus,
    lamiState: LamiState,
) {
    val statusUi = rememberLamiStatusUi(
        status = lamiStatus,
        lamiState = lamiState
    )
    val modelLabel = remember(selectedModel) {
        selectedModel
            ?.takeIf { it.isNotBlank() }
            ?.let { modelName -> "Model: $modelName" }
    }

    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = modelLabel ?: "Model",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val subtitleText = statusUi.subtitle.orEmpty()
        val subtitleAlpha = if (statusUi.subtitle == null) 0f else 1f
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = statusUi.title,
                style = MaterialTheme.typography.titleSmall,
                color = statusUi.titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
