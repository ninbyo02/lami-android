package io.github.ninbyo02.lami.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
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

data class LamiHeaderStatusState(
    val baseUrl: String,
    val selectedModel: String?,
    val lastError: String?,
    val lamiStatus: LamiStatus,
    val lamiState: LamiState,
    val availableModels: List<ModelInfo>,
    val selectedInferenceTarget: InferenceTarget = InferenceTarget.SERVER,
    val localBaseModelDisplayName: String? = null,
    val localInferenceEngineState: LocalInferenceEngineState = LocalInferenceEngineState.UNINITIALIZED,
    val debugOverlayEnabled: Boolean = true,
    val syncEpochMs: Long = 0L,
    val statusTitleOverride: String? = null,
)

data class LamiHeaderStatusActions(
    val onSelectModel: (String) -> Unit,
    val onNavigateSettings: () -> Unit,
    val onSelectInferenceTarget: (InferenceTarget) -> Unit = {},
    val onOpenControl: () -> Unit = {},
)

data class LamiHeaderAvatarPresentation(
    val initialAvatarSize: Dp = 64.dp,
    val minAvatarSize: Dp = 48.dp,
    val maxAvatarSize: Dp = 64.dp,
    val showAvatar: Boolean = true,
)

@Composable
fun LamiHeaderStatus(
    state: LamiHeaderStatusState,
    actions: LamiHeaderStatusActions,
    avatarPresentation: LamiHeaderAvatarPresentation = LamiHeaderAvatarPresentation(),
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            // アバター頭頂部の見切れを防ぐため、ヘッダー行のクリップを無効化
            .graphicsLayer { clip = false },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (avatarPresentation.showAvatar) {
            HeaderAvatar(
                state = state,
                actions = actions,
                avatarPresentation = avatarPresentation,
            )
        }
        HeaderStatusText(
            selectedModel = state.selectedModel,
            selectedInferenceTarget = state.selectedInferenceTarget,
            localBaseModelDisplayName = state.localBaseModelDisplayName,
            lamiStatus = state.lamiStatus,
            lamiState = state.lamiState,
            onOpenControl = actions.onOpenControl,
            statusTitleOverride = state.statusTitleOverride,
        )
    }
}

@Composable
fun HeaderAvatar(
    state: LamiHeaderStatusState,
    actions: LamiHeaderStatusActions,
    avatarPresentation: LamiHeaderAvatarPresentation = LamiHeaderAvatarPresentation(),
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
        state = state,
        actions = actions,
        avatarPresentation = avatarPresentation,
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
            InferenceTargetIcon(
                target = selectedInferenceTarget,
                modifier = Modifier.size(16.dp),
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
