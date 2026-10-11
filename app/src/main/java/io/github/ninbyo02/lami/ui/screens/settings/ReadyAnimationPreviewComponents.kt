package io.github.ninbyo02.lami.ui.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.ninbyo02.lami.ui.components.SpriteFrameRegion
import io.github.ninbyo02.lami.ui.components.drawFramePlaceholder
import io.github.ninbyo02.lami.ui.components.drawFrameRegion
import io.github.ninbyo02.lami.ui.components.rememberNightSpriteColorFilterForDarkTheme
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun ReadyAnimationCharacter(
    imageBitmap: ImageBitmap?,
    frameRegion: SpriteFrameRegion?,
    spriteSizeDp: Dp,
    charXOffsetDp: Int,
    charYOffsetDp: Int,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val spriteColorFilter = rememberNightSpriteColorFilterForDarkTheme()
    val characterBackgroundShape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            // [dp] 縦横: プレビュー の最小サイズ(最小サイズ)に関係
            .size(spriteSizeDp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                // [非dp] 横: キャラ背景の基準位置を左端に固定する
                .align(Alignment.CenterStart)
                .background(backgroundColor, characterBackgroundShape)
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // [dp] 上下: プレビュー の余白(余白)に関係
                .offset(x = charXOffsetDp.dp, y = charYOffsetDp.dp)
        ) {
            val dstW = size.width.roundToInt().coerceAtLeast(1)
            val dstH = size.height.roundToInt().coerceAtLeast(1)
            val side = min(dstW, dstH).coerceAtLeast(1)
            val squareSize = IntSize(side, side)
            val offset = IntOffset((dstW - side) / 2, (dstH - side) / 2)

            if (imageBitmap == null) {
                // 画像未ロード時でも白抜けにしないため、プレースホルダーを描画する
                drawFramePlaceholder(offset = offset, size = squareSize)
            } else {
                drawFrameRegion(
                    sheet = imageBitmap,
                    region = frameRegion,
                    dstSize = squareSize,
                    dstOffset = offset,
                    colorFilter = spriteColorFilter,
                    placeholder = { placeholderOffset, placeholderSize ->
                        drawFramePlaceholder(offset = placeholderOffset, size = placeholderSize)
                    }
                )
            }
        }
    }
}

@Composable
internal fun ReadyAnimationInfo(
    state: ReadyAnimationState,
    summary: AnimationSummary,
    insertionSummary: AnimationSummary,
    insertionPreviewValues: InsertionPreviewValues,
    insertionEnabled: Boolean,
    insertionPatterns: List<InsertionPattern>,
    insertionDefaultIntervalMs: Int,
    shouldShowDefaultInterval: Boolean,
    infoYOffsetDp: Int,
    modifier: Modifier = Modifier,
) {
    val paramYOffsetDp = 2
    // [dp] 縦: プレビュー の間隔(間隔)に関係
    val lineSpacing = 4.dp
    val baseFramesText = summary.frames.ifEmpty { listOf(0) }
        .joinToString(",") { value -> (value + 1).toString() }
    val insertionLine = if (insertionEnabled && insertionSummary.enabled) {
        val lastPatternIndex = state.lastInsertionPatternIndex
        val lastIntervalMs = state.lastInsertionResolvedIntervalMs
        val lastFrames = state.lastInsertionFrames
        if (lastPatternIndex != null && lastIntervalMs != null && lastFrames != null) {
            buildString {
                append("挿入: P")
                append(lastPatternIndex + 1)
                append(" ")
                append(lastIntervalMs)
                append("ms")
                if (shouldShowDefaultInterval && lastIntervalMs != insertionDefaultIntervalMs) {
                    append(" (D")
                    append(insertionDefaultIntervalMs)
                    append(")")
                }
                append(" F")
                append(lastFrames.size)
            }
        } else {
            val effectiveIntervalText = buildEffectiveInsertionIntervalText(
                patterns = insertionPatterns,
                defaultIntervalMs = insertionDefaultIntervalMs,
            )
            val defaultIntervalText = "${insertionDefaultIntervalMs}ms"
            val insertionFramesCount = insertionSummary.frames.ifEmpty { listOf(0) }.size
            // 実効周期のみを短縮表記で表示し、必要時のみデフォルト周期を併記する
            buildString {
                append("挿入: ")
                append(effectiveIntervalText)
                if (shouldShowDefaultInterval && effectiveIntervalText != defaultIntervalText) {
                    append(" (D")
                    append(insertionDefaultIntervalMs)
                    append(")")
                }
                append(" F")
                append(insertionFramesCount)
            }
        }
    } else {
        "挿入: OFF"
    }
    val everyNText = insertionPreviewValues.everyNText.ifBlank { "-" }
    val probabilityText = insertionPreviewValues.probabilityText.ifBlank { "-" }
    val cooldownText = insertionPreviewValues.cooldownText.ifBlank { "-" }
    val exclusiveText = insertionPreviewValues.exclusiveText.ifBlank { "-" }
    val pattern1WeightText = insertionPreviewValues.pattern1WeightText.ifBlank { "1" }
    val pattern2WeightText = insertionPreviewValues.pattern2WeightText.ifBlank { "0" }
    Column(
        modifier = modifier
            // [dp] 縦: プレビュー の余白(余白)に関係
            .offset(y = (paramYOffsetDp + infoYOffsetDp).dp),
        // [dp] 縦: プレビュー の間隔(間隔)に関係
        verticalArrangement = Arrangement.spacedBy(lineSpacing, Alignment.Top)
    ) {
        Row(
            // [非dp] 横: プレビュー の fillMaxWidth(制約)に関係
            modifier = Modifier.fillMaxWidth(),
            // [非dp] 横: プレビュー の SpaceBetween(間隔)に関係
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "State:${summary.label}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "プレビュー",
                modifier = Modifier.previewHeaderNudge(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            // [非dp] 横: プレビュー の fillMaxWidth(制約)に関係
            modifier = Modifier.fillMaxWidth(),
            // [非dp] 横: プレビュー の SpaceBetween(間隔)に関係
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "フレーム: ${state.currentFramePosition + 1}/${state.totalFrames}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "周期: ${state.currentIntervalMs}ms",
                modifier = Modifier.previewHeaderNudge(),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = "Base: ${summary.intervalMs}ms/$baseFramesText",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = insertionLine,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "N:$everyNText    P:$probabilityText    CD:$cooldownText    Excl:$exclusiveText    W:$pattern1WeightText/$pattern2WeightText",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
