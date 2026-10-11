@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.ninbyo02.lami.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ninbyo02.lami.R

@Composable
internal fun ReadyBaseAnimationSettingsSection(
    selectedAnimation: AnimationType,
    selectionState: AnimationSelectionState,
    baseState: BaseAnimationUiState,
    insertionState: InsertionAnimationUiState,
    baseFramesBringIntoViewRequester: BringIntoViewRequester,
    baseIntervalBringIntoViewRequester: BringIntoViewRequester,
    onFieldFocus: (String, Boolean, BringIntoViewRequester) -> Unit,
) {
    Column(
        modifier = Modifier
            // [非dp] 横: リスト の fillMaxWidth(制約)に関係
            .fillMaxWidth()
            // [dp] 上: 「アニメ設定」ヘッダーをプレビュー直下から 12dp 離して視認性を確保
            .padding(top = 12.dp),
        // [dp] 縦: リスト の間隔(間隔)に関係
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                // [非dp] 横: リスト の fillMaxWidth(制約)に関係
                .fillMaxWidth()
                // [dp] 下: リスト の余白(余白)に関係
                .padding(bottom = 4.dp),
            // [非dp] 横: リスト の SpaceBetween(間隔)に関係
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.sprite_animation_settings_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(
                    R.string.sprite_animation_settings_selected,
                    selectedAnimation.displayLabel
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AnimationDropdown(
            items = selectionState.animationOptions,
            selectedItem = selectedAnimation,
            onSelectedItemChange = selectionState.onSelectedAnimationChange,
            // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
            modifier = Modifier.fillMaxWidth(),
            anchorTestTag = "spriteAnimTypeDropdownAnchor",
        )
        OutlinedTextField(
            value = baseState.frameInput,
            onValueChange = baseState.onFrameInputChange,
            modifier = Modifier
                // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                .fillMaxWidth()
                .bringIntoViewRequester(baseFramesBringIntoViewRequester)
                .onFocusEvent { focusState ->
                    onFieldFocus(
                        "baseFrames",
                        focusState.isFocused,
                        baseFramesBringIntoViewRequester,
                    )
                }
                .testTag("spriteBaseFramesInput"),
            label = { Text("フレーム列 (例: 1,2,3)") },
            singleLine = true,
            isError = baseState.framesError != null,
            supportingText = baseState.framesError?.let { errorText ->
                { Text(errorText, color = Color.Red) }
            }
        )
        OutlinedTextField(
            value = baseState.intervalInput,
            onValueChange = baseState.onIntervalInputChange,
            modifier = Modifier
                // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                .fillMaxWidth()
                .bringIntoViewRequester(baseIntervalBringIntoViewRequester)
                .onFocusEvent { focusState ->
                    onFieldFocus(
                        "baseInterval",
                        focusState.isFocused,
                        baseIntervalBringIntoViewRequester,
                    )
                }
                .testTag("spriteBaseIntervalInput"),
            label = { Text("周期 (ms)") },
            singleLine = true,
            isError = baseState.intervalError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = baseState.intervalError?.let { errorText ->
                { Text(errorText, color = Color.Red) }
            }
        )
        Row(
            // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                // [非dp] 横: 入力欄 の weight(制約)に関係
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "挿入設定",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "挿入を使う",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = insertionState.enabled,
                onCheckedChange = insertionState.onEnabledChange
            )
        }
    }
}
