@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.ninbyo02.lami.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ReadyInsertionAnimationSettings(
    insertionState: InsertionAnimationUiState,
    onFieldFocus: (String, Boolean, BringIntoViewRequester) -> Unit,
) {
    val pattern1FramesBringIntoViewRequester = remember { BringIntoViewRequester() }
    val pattern1WeightBringIntoViewRequester = remember { BringIntoViewRequester() }
    val pattern1IntervalBringIntoViewRequester = remember { BringIntoViewRequester() }
    val pattern2FramesBringIntoViewRequester = remember { BringIntoViewRequester() }
    val pattern2WeightBringIntoViewRequester = remember { BringIntoViewRequester() }
    val pattern2IntervalBringIntoViewRequester = remember { BringIntoViewRequester() }
    val insertionIntervalBringIntoViewRequester = remember { BringIntoViewRequester() }
    val everyNLoopsBringIntoViewRequester = remember { BringIntoViewRequester() }
    val probabilityPercentBringIntoViewRequester = remember { BringIntoViewRequester() }
    val cooldownLoopsBringIntoViewRequester = remember { BringIntoViewRequester() }

    AnimatedVisibility(visible = insertionState.enabled) {
                    @OptIn(ExperimentalFoundationApi::class)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReadyInsertionPatternOneSettings(
                            insertionState = insertionState,
                            framesBringIntoViewRequester = pattern1FramesBringIntoViewRequester,
                            weightBringIntoViewRequester = pattern1WeightBringIntoViewRequester,
                            intervalBringIntoViewRequester = pattern1IntervalBringIntoViewRequester,
                            onFieldFocus = onFieldFocus,
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            // [dp] 縦: パターン2入力 の間隔(間隔)に関係
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "パターン2",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            OutlinedTextField(
                                value = insertionState.pattern2FramesInput,
                                onValueChange = insertionState.onPattern2FramesInputChange,
                                modifier = Modifier
                                    // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(pattern2FramesBringIntoViewRequester)
                                    .onFocusEvent { focusState ->
                                        onFieldFocus(
                                            "pattern2Frames",
                                            focusState.isFocused,
                                            pattern2FramesBringIntoViewRequester,
                                        )
                                    },
                                label = { Text("パターン2 フレーム列（任意）") },
                                singleLine = true,
                                isError = insertionState.pattern2FramesError != null,
                                supportingText = insertionState.pattern2FramesError?.let { errorText ->
                                    { Text(errorText, color = Color.Red) }
                                }
                            )
                            OutlinedTextField(
                                value = insertionState.pattern2WeightInput,
                                onValueChange = insertionState.onPattern2WeightInputChange,
                                modifier = Modifier
                                    // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(pattern2WeightBringIntoViewRequester)
                                    .onFocusEvent { focusState ->
                                        onFieldFocus(
                                            "pattern2Weight",
                                            focusState.isFocused,
                                            pattern2WeightBringIntoViewRequester,
                                        )
                                    },
                                label = { Text("パターン2 重み（比率）") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = insertionState.pattern2WeightError != null,
                                supportingText = insertionState.pattern2WeightError?.let { errorText ->
                                    { Text(errorText, color = Color.Red) }
                                }
                            )
                            OutlinedTextField(
                                value = insertionState.pattern2IntervalInput,
                                onValueChange = insertionState.onPattern2IntervalInputChange,
                                modifier = Modifier
                                    // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(pattern2IntervalBringIntoViewRequester)
                                    .onFocusEvent { focusState ->
                                        onFieldFocus(
                                            "pattern2Interval",
                                            focusState.isFocused,
                                            pattern2IntervalBringIntoViewRequester,
                                        )
                                    },
                                label = { Text("パターン2 周期（ms）") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = insertionState.pattern2IntervalError != null,
                                supportingText = insertionState.pattern2IntervalError?.let { errorText ->
                                    { Text(errorText, color = Color.Red) }
                                }
                            )
                        }
                        val hasOptionalDefaultInterval = insertionState.enabled && insertionState.patterns.isNotEmpty()
                        OutlinedTextField(
                            value = insertionState.intervalInput,
                            onValueChange = insertionState.onIntervalInputChange,
                            modifier = Modifier
                                // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                                .fillMaxWidth()
                                .bringIntoViewRequester(insertionIntervalBringIntoViewRequester)
                                .onFocusEvent { focusState ->
                                    onFieldFocus(
                                        "insertionInterval",
                                        focusState.isFocused,
                                        insertionIntervalBringIntoViewRequester,
                                    )
                                }
                                .testTag("spriteInsertionIntervalInput"),
                            label = {
                                Text(
                                    if (hasOptionalDefaultInterval) {
                                        "デフォルト周期（ms）（任意）"
                                    } else {
                                        "デフォルト周期（ms）"
                                    }
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = !hasOptionalDefaultInterval && insertionState.intervalError != null,
                            supportingText = if (hasOptionalDefaultInterval) {
                                {
                                    Column {
                                        Text("未入力の場合はパターンの周期を使用します")
                                        insertionState.intervalError?.let { errorText ->
                                            Text(errorText, color = Color.Red)
                                        }
                                    }
                                }
                            } else {
                                insertionState.intervalError?.let { errorText ->
                                    { Text(errorText, color = Color.Red) }
                                }
                            }
                        )
                        OutlinedTextField(
                            value = insertionState.everyNInput,
                            onValueChange = insertionState.onEveryNInputChange,
                            modifier = Modifier
                                // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                                .fillMaxWidth()
                                .bringIntoViewRequester(everyNLoopsBringIntoViewRequester)
                                .onFocusEvent { focusState ->
                                    onFieldFocus(
                                        "everyNLoops",
                                        focusState.isFocused,
                                        everyNLoopsBringIntoViewRequester,
                                    )
                                },
                            label = { Text("毎 N ループ") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = insertionState.everyNError != null,
                            supportingText = insertionState.everyNError?.let { errorText ->
                                { Text(errorText, color = Color.Red) }
                            }
                        )
                        OutlinedTextField(
                            value = insertionState.probabilityInput,
                            onValueChange = insertionState.onProbabilityInputChange,
                            modifier = Modifier
                                // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                                .fillMaxWidth()
                                .bringIntoViewRequester(probabilityPercentBringIntoViewRequester)
                                .onFocusEvent { focusState ->
                                    onFieldFocus(
                                        "probabilityPercent",
                                        focusState.isFocused,
                                        probabilityPercentBringIntoViewRequester,
                                    )
                                },
                            label = { Text("確率（%）") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = insertionState.probabilityError != null,
                            supportingText = insertionState.probabilityError?.let { errorText ->
                                { Text(errorText, color = Color.Red) }
                            }
                        )
                        OutlinedTextField(
                            value = insertionState.cooldownInput,
                            onValueChange = insertionState.onCooldownInputChange,
                            modifier = Modifier
                                // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                                .fillMaxWidth()
                                .bringIntoViewRequester(cooldownLoopsBringIntoViewRequester)
                                .onFocusEvent { focusState ->
                                    onFieldFocus(
                                        "cooldownLoops",
                                        focusState.isFocused,
                                        cooldownLoopsBringIntoViewRequester,
                                    )
                                },
                            label = { Text("クールダウン（ループ）") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = insertionState.cooldownError != null,
                            supportingText = insertionState.cooldownError?.let { errorText ->
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
                                Text("Exclusive（挿入位置の固定）")
                                Text(
                                    text = "ONにすると挿入ループでは Base を再生せず、挿入フレームで置き換えます",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = insertionState.exclusive,
                                onCheckedChange = insertionState.onExclusiveChange
                            )
                        }
                    }
                }
}

@Composable
private fun ReadyInsertionPatternOneSettings(
    insertionState: InsertionAnimationUiState,
    framesBringIntoViewRequester: BringIntoViewRequester,
    weightBringIntoViewRequester: BringIntoViewRequester,
    intervalBringIntoViewRequester: BringIntoViewRequester,
    onFieldFocus: (String, Boolean, BringIntoViewRequester) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        // [dp] 縦: パターン1入力 の間隔(間隔)に関係
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "パターン1",
            style = MaterialTheme.typography.labelMedium,
        )
        OutlinedTextField(
            value = insertionState.pattern1FramesInput,
            onValueChange = insertionState.onPattern1FramesInputChange,
            modifier = Modifier
                // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                .fillMaxWidth()
                .bringIntoViewRequester(framesBringIntoViewRequester)
                .onFocusEvent { focusState ->
                    onFieldFocus(
                        "pattern1Frames",
                        focusState.isFocused,
                        framesBringIntoViewRequester,
                    )
                },
            label = { Text("パターン1 フレーム列（例: 4,5,6）") },
            singleLine = true,
            isError = insertionState.pattern1FramesError != null,
            supportingText = insertionState.pattern1FramesError?.let { errorText ->
                { Text(errorText, color = Color.Red) }
            }
        )
        OutlinedTextField(
            value = insertionState.pattern1WeightInput,
            onValueChange = insertionState.onPattern1WeightInputChange,
            modifier = Modifier
                // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                .fillMaxWidth()
                .bringIntoViewRequester(weightBringIntoViewRequester)
                .onFocusEvent { focusState ->
                    onFieldFocus(
                        "pattern1Weight",
                        focusState.isFocused,
                        weightBringIntoViewRequester,
                    )
                },
            label = { Text("パターン1 重み（比率）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = insertionState.pattern1WeightError != null,
            supportingText = insertionState.pattern1WeightError?.let { errorText ->
                { Text(errorText, color = Color.Red) }
            }
        )
        OutlinedTextField(
            value = insertionState.pattern1IntervalInput,
            onValueChange = insertionState.onPattern1IntervalInputChange,
            modifier = Modifier
                // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                .fillMaxWidth()
                .bringIntoViewRequester(intervalBringIntoViewRequester)
                .onFocusEvent { focusState ->
                    onFieldFocus(
                        "pattern1Interval",
                        focusState.isFocused,
                        intervalBringIntoViewRequester,
                    )
                },
            label = { Text("パターン1 周期（ms）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = insertionState.pattern1IntervalError != null,
            supportingText = insertionState.pattern1IntervalError?.let { errorText ->
                { Text(errorText, color = Color.Red) }
            }
        )
    }
}
