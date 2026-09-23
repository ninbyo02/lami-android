package io.github.ninbyo02.lami.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import io.github.ninbyo02.lami.data.SpriteSheetConfig
import io.github.ninbyo02.lami.ui.components.ReadyPreviewSlot
import io.github.ninbyo02.lami.ui.components.rememberLamiEditorSpriteBackdropColor
import kotlin.math.abs

internal data class ReadyAnimationPreviewModel(
    val imageBitmap: ImageBitmap?,
    val spriteSheetConfig: SpriteSheetConfig,
    val baseSummary: AnimationSummary,
    val insertionSummary: AnimationSummary,
    val insertionPreviewValues: InsertionPreviewValues,
    val insertionEnabled: Boolean,
    val insertionPatterns: List<InsertionPattern>,
    val insertionDefaultIntervalMs: Int,
    val shouldShowDefaultInterval: Boolean,
    val insertionDefaults: InsertionAnimationSettings,
)

@Composable
internal fun ReadyAnimationPreviewPane(
    model: ReadyAnimationPreviewModel,
    previewUiState: ReadyPreviewUiState,
    isImeVisible: Boolean,
    modifier: Modifier = Modifier,
    devMenuContent: (@Composable () -> Unit)? = null,
) {
    val imageBitmap = model.imageBitmap
    val spriteSheetConfig = model.spriteSheetConfig
    val baseSummary = model.baseSummary
    val insertionSummary = model.insertionSummary
    val insertionPreviewValues = model.insertionPreviewValues
    val insertionEnabled = model.insertionEnabled
    val insertionPatterns = model.insertionPatterns
    val insertionDefaultIntervalMs = model.insertionDefaultIntervalMs
    val shouldShowDefaultInterval = model.shouldShowDefaultInterval
    val insertionDefaults = model.insertionDefaults
    Column(modifier = modifier) {
        val outerPaddingColor = if (previewUiState.outerBottomDp >= 0) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
        }
        val outerPaddingStroke = if (previewUiState.outerBottomDp >= 0) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        }

        Box(
            modifier = Modifier
                // [非dp] 横: プレビュー の fillMaxWidth(制約)に関係
                .fillMaxWidth()
                // [dp] 下: プレビュー の余白(余白)に関係
                .padding(bottom = previewUiState.outerBottomDp.dp)
                .drawBehind {
                    val indicatorHeight = abs(previewUiState.outerBottomDp).dp.toPx().coerceAtMost(size.height)
                    if (indicatorHeight > 0f) {
                        val top = size.height - indicatorHeight
                        drawRect(
                            color = outerPaddingColor,
                            topLeft = Offset(x = 0f, y = top),
                            size = Size(width = size.width, height = indicatorHeight)
                        )
                        drawLine(
                            color = outerPaddingStroke,
                            start = Offset(x = 0f, y = top),
                            end = Offset(x = size.width, y = top),
                            strokeWidth = 2f
                        )
                    }
                }
        ) {
            // [非dp] 横: カード の fillMaxWidth(制約)に関係
            val baseCardModifier = Modifier.fillMaxWidth()
            val cardHeightModifier = if (previewUiState.effectiveCardMaxH != null) {
                baseCardModifier.heightIn(
                    // [dp] 縦: カード の最小サイズ(最小サイズ)に関係
                    min = previewUiState.effectiveMinHeightDp.dp,
                    // [dp] 縦: カード の制約(制約)に関係
                    max = previewUiState.effectiveCardMaxH.dp
                )
            } else {
                baseCardModifier.heightIn(min = previewUiState.effectiveMinHeightDp.dp)
            }
            BoxWithConstraints(
                modifier = Modifier
                    // [非dp] 横: プレビュー の fillMaxWidth(制約)に関係
                    .fillMaxWidth()
            ) {
                // [非dp] 縦横: プレビュー の制約(制約)に関係
                val rawSpriteSize = (maxWidth * 0.30f).coerceAtLeast(1.dp)
                // [dp] 縦横: プレビュー の表示サイズ(サイズ)に関係
                val spriteSize = rawSpriteSize.coerceIn(72.dp, 120.dp)
                val previewState = rememberReadyAnimationState(
                    spriteSheetConfig = spriteSheetConfig,
                    summary = baseSummary,
                    insertionSummary = insertionSummary,
                    insertionEnabled = insertionEnabled,
                    insertionPatterns = insertionPatterns,
                    insertionDefaultIntervalMs = insertionDefaultIntervalMs,
                    insertionDefaults = insertionDefaults,
                )
                // [dp] 左右: プレビュー の余白(余白)に関係
                val contentHorizontalPadding = maxOf(8.dp, minOf(12.dp, maxWidth * 0.035f))

                val innerPaddingColor = if (previewUiState.innerBottomDp >= 0) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
                }
                val innerPaddingStroke = if (previewUiState.innerBottomDp >= 0) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                }
                val editorBackdropColor = rememberLamiEditorSpriteBackdropColor()

                ReadyPreviewSlot(
                    cardHeightModifier = cardHeightModifier,
                    contentHorizontalPadding = contentHorizontalPadding,
                    effectiveInnerVPadDp = previewUiState.innerVPadDp,
                    innerBottomDp = previewUiState.innerBottomDp,
                    effectiveInnerBottomDp = previewUiState.innerBottomDp,
                    innerPaddingColor = innerPaddingColor,
                    innerPaddingStroke = innerPaddingStroke,
                    sprite = {
                        ReadyAnimationCharacter(
                            imageBitmap = imageBitmap,
                            frameRegion = previewState.frameRegion,
                            spriteSizeDp = spriteSize,
                            charXOffsetDp = previewUiState.charXOffsetDp,
                            charYOffsetDp = previewUiState.charYOffsetDp,
                            backgroundColor = editorBackdropColor,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                // [dp] 四方向: プレビュー の余白を均等にし、ダーク時の見え方ズレを抑える
                                .padding(all = contentHorizontalPadding)
                        )
                    },
                    info = {
                        ReadyAnimationInfo(
                            state = previewState,
                            summary = baseSummary,
                            insertionSummary = insertionSummary,
                            insertionPreviewValues = insertionPreviewValues,
                            insertionEnabled = insertionEnabled,
                            insertionPatterns = insertionPatterns,
                            insertionDefaultIntervalMs = insertionDefaultIntervalMs,
                            shouldShowDefaultInterval = shouldShowDefaultInterval,
                            infoYOffsetDp = previewUiState.infoYOffsetDp,
                            modifier = Modifier
                                // [非dp] 横: プレビュー の fillMaxWidth(制約)に関係
                                .fillMaxWidth()
                                .offset(
                                    previewUiState.headerLeftXOffsetDp.dp,
                                    previewUiState.headerLeftYOffsetDp.dp
                                )
                                // [dp] 左: プレビュー の余白(余白)に関係
                                .padding(
                                    start = spriteSize + (spriteSize * 0.08f).coerceIn(4.dp, 6.dp)
                                )
                                // [dp] 左右: プレビュー の余白(余白)に関係
                                .offset(previewUiState.infoXOffsetDp.dp)
                        )
                    }
                )
            }
        }
        devMenuContent?.let { content ->
            // [dp] 縦: 開発メニュー の間隔(間隔)に関係
            if (!isImeVisible) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            content()
        }
    }
}
