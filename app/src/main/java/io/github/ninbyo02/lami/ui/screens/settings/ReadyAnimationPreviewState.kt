package io.github.ninbyo02.lami.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.ninbyo02.lami.data.SpriteSheetConfig
import io.github.ninbyo02.lami.data.boxesWithInternalIndex
import io.github.ninbyo02.lami.data.isUninitialized
import io.github.ninbyo02.lami.data.toInternalFrameIndex
import io.github.ninbyo02.lami.ui.components.SpriteFrameRegion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

internal data class ReadyAnimationState(
    val frameRegion: SpriteFrameRegion?,
    val currentFramePosition: Int,
    val totalFrames: Int,
    val currentIntervalMs: Int,
    val lastInsertionPatternIndex: Int?,
    val lastInsertionResolvedIntervalMs: Int?,
    val lastInsertionFrames: List<Int>?,
)

private data class PreviewStep(
    val frameIndex: Int,
    val intervalMs: Int,
    val isInsertion: Boolean,
)

@Composable
internal fun rememberReadyAnimationState(
    spriteSheetConfig: SpriteSheetConfig,
    summary: AnimationSummary,
    insertionSummary: AnimationSummary,
    insertionEnabled: Boolean,
    insertionPatterns: List<InsertionPattern>,
    insertionDefaultIntervalMs: Int,
    insertionDefaults: InsertionAnimationSettings,
): ReadyAnimationState {
    val normalizedConfig = remember(spriteSheetConfig) {
        val validationError = spriteSheetConfig.validate()
        val safeConfig = if (spriteSheetConfig.isUninitialized() || validationError != null) {
            SpriteSheetConfig.default3x3()
        } else {
            spriteSheetConfig
        }
        safeConfig.copy(boxes = safeConfig.boxesWithInternalIndex())
    }
    val baseFrames = remember(summary) { summary.frames.ifEmpty { listOf(0) } }
    val baseSteps = remember(baseFrames, summary.intervalMs) {
        baseFrames.map { frame ->
            PreviewStep(frameIndex = frame, intervalMs = summary.intervalMs, isInsertion = false)
        }
    }
    val insertionKey = remember(insertionEnabled, insertionSummary, insertionPatterns) {
        listOf(
            insertionEnabled,
            insertionSummary.enabled,
            insertionPatterns,
            insertionSummary.intervalMs,
            insertionSummary.everyNLoops,
            insertionSummary.probabilityPercent,
            insertionSummary.cooldownLoops,
            insertionSummary.exclusive,
        ).hashCode()
    }
    val activeInsertionSettings = remember(insertionEnabled, insertionSummary, insertionPatterns, insertionDefaultIntervalMs) {
        if (!insertionEnabled || !insertionSummary.enabled || insertionPatterns.isEmpty()) {
            null
        } else {
            InsertionAnimationSettings(
                enabled = true,
                patterns = insertionPatterns,
                intervalMs = insertionDefaultIntervalMs,
                everyNLoops = insertionSummary.everyNLoops ?: insertionDefaults.everyNLoops,
                probabilityPercent = insertionSummary.probabilityPercent
                    ?: insertionDefaults.probabilityPercent,
                cooldownLoops = insertionSummary.cooldownLoops ?: insertionDefaults.cooldownLoops,
                exclusive = insertionSummary.exclusive ?: insertionDefaults.exclusive,
            )
        }
    }
    val random = remember { Random(System.currentTimeMillis()) }
    var stepPosition by remember { mutableStateOf(0) }
    var steps by remember { mutableStateOf<List<PreviewStep>>(emptyList()) }
    var loopCount by remember { mutableStateOf(0) }
    var lastInsertionLoop by remember { mutableStateOf<Int?>(null) }
    var lastInsertionPatternIndex by remember(insertionKey) { mutableStateOf<Int?>(null) }
    var lastInsertionResolvedIntervalMs by remember(insertionKey) { mutableStateOf<Int?>(null) }
    var lastInsertionFrames by remember(insertionKey) { mutableStateOf<List<Int>?>(null) }
    val safeSteps = steps.ifEmpty { baseSteps }
    val safeStepPosition = stepPosition.coerceIn(0, safeSteps.lastIndex.coerceAtLeast(0))
    val currentStep = safeSteps.getOrNull(safeStepPosition)
        ?: PreviewStep(frameIndex = baseFrames.first(), intervalMs = summary.intervalMs, isInsertion = false)
    val totalFrames = safeSteps.size.coerceAtLeast(1)
    val currentIntervalMs = currentStep.intervalMs.coerceAtLeast(16)
    val currentFrameIndex = currentStep.frameIndex
    val frameRegion = remember(normalizedConfig, currentFrameIndex) {
        val internalIndex = normalizedConfig.toInternalFrameIndex(currentFrameIndex) ?: return@remember null
        val box = normalizedConfig.boxes.getOrNull(internalIndex) ?: return@remember null
        SpriteFrameRegion(
            srcOffset = IntOffset(box.x, box.y),
            srcSize = IntSize(box.width, box.height)
        )
    }

    LaunchedEffect(
        baseSteps,
        insertionPatterns,
        insertionDefaultIntervalMs,
        summary.intervalMs,
        insertionSummary.intervalMs,
        insertionKey,
    ) {
        loopCount = 0
        lastInsertionLoop = null
        stepPosition = 0
        steps = baseSteps
        lastInsertionPatternIndex = null
        lastInsertionResolvedIntervalMs = null
        lastInsertionFrames = null
        while (isActive) {
            loopCount += 1
            val insertionSettings = activeInsertionSettings
            val shouldInsert = insertionSettings?.shouldAttemptInsertion(
                loopCount = loopCount,
                lastInsertionLoop = lastInsertionLoop,
                random = random,
            ) == true
            val stepsForLoop = if (shouldInsert) {
                val selection = selectWeightedInsertionPattern(
                    patterns = insertionPatterns,
                    random = random
                )
                if (selection != null) {
                    val (patternIndex, pattern) = selection
                    val resolvedIntervalMs = (pattern.intervalMs ?: insertionDefaultIntervalMs).coerceAtLeast(0)
                    val insertionSteps = pattern.frames().map { frame ->
                        PreviewStep(frameIndex = frame, intervalMs = resolvedIntervalMs, isInsertion = true)
                    }
                    lastInsertionPatternIndex = patternIndex
                    lastInsertionResolvedIntervalMs = resolvedIntervalMs
                    lastInsertionFrames = pattern.frames().toList()
                    lastInsertionLoop = loopCount
                    if (insertionSettings.exclusive) {
                        insertionSteps
                    } else {
                        insertionSteps + baseSteps
                    }
                } else {
                    baseSteps
                }
            } else {
                baseSteps
            }.ifEmpty { baseSteps }
            steps = stepsForLoop
            for (index in stepsForLoop.indices) {
                stepPosition = index
                val step = stepsForLoop[index]
                delay(step.intervalMs.coerceAtLeast(16).toLong())
            }
        }
    }

    return ReadyAnimationState(
        frameRegion = frameRegion,
        currentFramePosition = safeStepPosition,
        totalFrames = totalFrames,
        currentIntervalMs = currentIntervalMs,
        lastInsertionPatternIndex = lastInsertionPatternIndex,
        lastInsertionResolvedIntervalMs = lastInsertionResolvedIntervalMs,
        lastInsertionFrames = lastInsertionFrames,
    )
}

private fun selectWeightedInsertionPattern(
    patterns: List<InsertionPattern>,
    random: Random,
): Pair<Int, InsertionPattern>? {
    val candidates = patterns.withIndex().filter { (_, pattern) ->
        pattern.weight > 0 && pattern.frames().isNotEmpty()
    }
    if (candidates.isEmpty()) return null
    val totalWeight = candidates.sumOf { (_, pattern) -> pattern.weight }
    if (totalWeight <= 0) return null
    val roll = random.nextInt(totalWeight)
    var cursor = 0
    for ((index, pattern) in candidates) {
        cursor += pattern.weight
        if (roll < cursor) return index to pattern
    }
    return candidates.lastOrNull()?.let { (index, pattern) -> index to pattern }
}
