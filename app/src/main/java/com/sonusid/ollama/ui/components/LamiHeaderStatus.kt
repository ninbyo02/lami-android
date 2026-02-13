package com.sonusid.ollama.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonusid.ollama.viewmodels.LamiState
import com.sonusid.ollama.viewmodels.LamiStatus
import com.sonusid.ollama.viewmodels.ModelInfo

private val HeaderAvatarTopSafePadding = 2.dp
private val HeaderAvatarMaxSize = 62.dp

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
    modifier: Modifier = Modifier,
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

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            // 上端の見切れを防ぐため、ヘッダー内でのみ最小限の安全余白を確保する。
            modifier = Modifier.padding(top = HeaderAvatarTopSafePadding)
        ) {
            LamiAvatar(
                baseUrl = baseUrl,
                selectedModel = selectedModel,
                lastError = lastError,
                lamiStatus = lamiStatus,
                availableModels = availableModels,
                initialAvatarSize = HeaderAvatarMaxSize,
                minAvatarSize = 48.dp,
                maxAvatarSize = HeaderAvatarMaxSize,
                onSelectModel = onSelectModel,
                onNavigateSettings = onNavigateSettings
            )
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
                    overflow = TextOverflow.Ellipsis
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
}
