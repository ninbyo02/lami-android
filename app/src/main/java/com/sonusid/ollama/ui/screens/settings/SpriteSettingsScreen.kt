package com.sonusid.ollama.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import androidx.navigation.NavController
import com.sonusid.ollama.R
import com.sonusid.ollama.data.SpriteSheetConfig
import com.sonusid.ollama.data.boxesWithInternalIndex
import com.sonusid.ollama.data.isUninitialized
import com.sonusid.ollama.data.toInternalFrameIndex
import com.sonusid.ollama.data.BoxPosition as SpriteSheetBoxPosition
import com.sonusid.ollama.ui.components.SpriteFrameRegion
import com.sonusid.ollama.ui.components.drawFramePlaceholder
import com.sonusid.ollama.ui.components.drawFrameRegion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class BoxPosition(val x: Int, val y: Int)

private enum class AnimationType(val label: String) {
    READY("Ready"),
    TALKING("Talking（ロング）");

    companion object {
        val options = values().toList()
    }
}

private data class AnimationSummary(
    val label: String,
    val frames: List<Int>,
    val intervalMs: Int,
    val everyNLoops: Int? = null,
    val probabilityPercent: Int? = null,
    val cooldownLoops: Int? = null,
    val exclusive: Boolean? = null,
    val enabled: Boolean = false,
)

private data class InsertionPreviewValues(
    val framesText: String,
    val intervalText: String,
    val everyNText: String,
    val probabilityText: String,
    val cooldownText: String,
    val exclusiveText: String,
)

private fun buildInsertionPreviewSummary(
    label: String,
    enabled: Boolean,
    frameInput: String,
    intervalInput: String,
    everyNInput: String,
    probabilityInput: String,
    cooldownInput: String,
    exclusive: Boolean,
    frameCount: Int
): Pair<AnimationSummary, InsertionPreviewValues> {
    val framesText = frameInput.trim()
    val intervalText = intervalInput.trim()
    val everyNText = everyNInput.trim()
    val probabilityText = probabilityInput.trim()
    val cooldownText = cooldownInput.trim()

    val previewValues = InsertionPreviewValues(
        framesText = framesText.ifEmpty { "-" },
        intervalText = intervalText.ifEmpty { "-" },
        everyNText = everyNText.ifEmpty { "-" },
        probabilityText = probabilityText.ifEmpty { "-" },
        cooldownText = cooldownText.ifEmpty { "-" },
        exclusiveText = if (exclusive) "ON" else "OFF"
    )

    val parsedFrames = framesText
        .split(",")
        .map { token -> token.trim() }
        .filter { token -> token.isNotEmpty() }
        .mapNotNull { token ->
            token.toIntOrNull()
                ?.takeIf { value -> value in 1..frameCount }
                ?.minus(1)
        }
    val frames = parsedFrames.ifEmpty { listOf(0) }
    val intervalMs = intervalText.toIntOrNull()?.takeIf { it > 0 } ?: InsertionAnimationSettings.DEFAULT.intervalMs
    val everyNLoops = everyNText.toIntOrNull()
    val probabilityPercent = probabilityText.toIntOrNull()
    val cooldownLoops = cooldownText.toIntOrNull()

    val summary = AnimationSummary(
        label = label,
        frames = frames,
        intervalMs = intervalMs,
        everyNLoops = everyNLoops,
        probabilityPercent = probabilityPercent,
        cooldownLoops = cooldownLoops,
        exclusive = exclusive,
        enabled = enabled,
    )

    return summary to previewValues
}

private const val DEFAULT_BOX_SIZE_PX = 88

private fun clampPosition(
    position: BoxPosition,
    boxSizePx: Int,
    sheetWidth: Int,
    sheetHeight: Int
): BoxPosition {
    val maxX = (sheetWidth - boxSizePx).coerceAtLeast(0)
    val maxY = (sheetHeight - boxSizePx).coerceAtLeast(0)
    return BoxPosition(
        x = position.x.coerceIn(0, maxX),
        y = position.y.coerceIn(0, maxY)
    )
}

private fun boxPositionsSaver() = androidx.compose.runtime.saveable.listSaver<List<BoxPosition>, Int>(
    save = { list -> list.flatMap { position -> listOf(position.x, position.y) } },
    restore = { flat ->
        flat.chunked(2).map { (x, y) ->
            BoxPosition(x = x, y = y)
        }
    }
)

private fun defaultBoxPositions(): List<BoxPosition> =
    SpriteSheetConfig.default3x3()
        .boxesWithInternalIndex()
        .sortedBy { it.frameIndex }
        .map { box -> BoxPosition(box.x, box.y) }

@Composable
fun SpriteSettingsScreen(navController: NavController) {
    val imageBitmap: ImageBitmap =
        ImageBitmap.imageResource(LocalContext.current.resources, R.drawable.lami_sprite_3x3_288)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val settingsPreferences = remember(context.applicationContext) {
        SettingsPreferences(context.applicationContext)
    }
    val spriteSheetConfig by settingsPreferences.spriteSheetConfig.collectAsState(initial = SpriteSheetConfig.default3x3())
    val readyAnimationSettings by settingsPreferences.readyAnimationSettings.collectAsState(initial = ReadyAnimationSettings.DEFAULT)
    val talkingAnimationSettings by settingsPreferences.talkingAnimationSettings.collectAsState(initial = ReadyAnimationSettings.DEFAULT)
    val readyInsertionAnimationSettings by settingsPreferences.readyInsertionAnimationSettings.collectAsState(initial = InsertionAnimationSettings.DEFAULT)
    val talkingInsertionAnimationSettings by settingsPreferences.talkingInsertionAnimationSettings.collectAsState(initial = InsertionAnimationSettings.DEFAULT)

    var selectedNumber by rememberSaveable { mutableStateOf(1) }
    var boxSizePx by rememberSaveable { mutableStateOf(DEFAULT_BOX_SIZE_PX) }
    var boxPositions by rememberSaveable(stateSaver = boxPositionsSaver()) { mutableStateOf(defaultBoxPositions()) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var displayScale by remember { mutableStateOf(1f) }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var readyFrameInput by rememberSaveable { mutableStateOf("1,2,3,2") }
    var readyIntervalInput by rememberSaveable { mutableStateOf("700") }
    var appliedReadyFrames by rememberSaveable { mutableStateOf(listOf(0, 1, 2, 1)) }
    var appliedReadyIntervalMs by rememberSaveable { mutableStateOf(700) }
    var readyFramesError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyIntervalError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingFrameInput by rememberSaveable { mutableStateOf("1,2,3,2") }
    var talkingIntervalInput by rememberSaveable { mutableStateOf("700") }
    var appliedTalkingFrames by rememberSaveable { mutableStateOf(listOf(0, 1, 2, 1)) }
    var appliedTalkingIntervalMs by rememberSaveable { mutableStateOf(700) }
    var talkingFramesError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingIntervalError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyInsertionFrameInput by rememberSaveable { mutableStateOf("4,5,6") }
    var readyInsertionIntervalInput by rememberSaveable { mutableStateOf("200") }
    var readyInsertionEveryNInput by rememberSaveable { mutableStateOf("1") }
    var readyInsertionProbabilityInput by rememberSaveable { mutableStateOf("50") }
    var readyInsertionCooldownInput by rememberSaveable { mutableStateOf("0") }
    var readyInsertionEnabled by rememberSaveable { mutableStateOf(false) }
    var readyInsertionExclusive by rememberSaveable { mutableStateOf(false) }
    var appliedReadyInsertionFrames by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.frameSequence) }
    var appliedReadyInsertionIntervalMs by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.intervalMs) }
    var appliedReadyInsertionEveryNLoops by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.everyNLoops) }
    var appliedReadyInsertionProbabilityPercent by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.probabilityPercent) }
    var appliedReadyInsertionCooldownLoops by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.cooldownLoops) }
    var appliedReadyInsertionEnabled by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.enabled) }
    var appliedReadyInsertionExclusive by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.exclusive) }
    var readyInsertionFramesError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyInsertionIntervalError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyInsertionEveryNError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyInsertionProbabilityError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyInsertionCooldownError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingInsertionFrameInput by rememberSaveable { mutableStateOf("4,5,6") }
    var talkingInsertionIntervalInput by rememberSaveable { mutableStateOf("200") }
    var talkingInsertionEveryNInput by rememberSaveable { mutableStateOf("1") }
    var talkingInsertionProbabilityInput by rememberSaveable { mutableStateOf("50") }
    var talkingInsertionCooldownInput by rememberSaveable { mutableStateOf("0") }
    var talkingInsertionEnabled by rememberSaveable { mutableStateOf(false) }
    var talkingInsertionExclusive by rememberSaveable { mutableStateOf(false) }
    var appliedTalkingInsertionFrames by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.frameSequence) }
    var appliedTalkingInsertionIntervalMs by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.intervalMs) }
    var appliedTalkingInsertionEveryNLoops by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.everyNLoops) }
    var appliedTalkingInsertionProbabilityPercent by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.probabilityPercent) }
    var appliedTalkingInsertionCooldownLoops by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.cooldownLoops) }
    var appliedTalkingInsertionEnabled by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.enabled) }
    var appliedTalkingInsertionExclusive by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.exclusive) }
    var talkingInsertionFramesError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingInsertionIntervalError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingInsertionEveryNError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingInsertionProbabilityError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingInsertionCooldownError by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAnimation by rememberSaveable { mutableStateOf(AnimationType.READY) }

    LaunchedEffect(spriteSheetConfig) {
        val validConfig = spriteSheetConfig
            .takeIf { it.isUninitialized().not() && it.validate() == null }
            ?.copy(boxes = spriteSheetConfig.boxesWithInternalIndex())
            ?: SpriteSheetConfig.default3x3()

        val resolvedBoxes = validConfig.boxes
        boxSizePx = validConfig.frameWidth.coerceAtLeast(1)
        boxPositions = resolvedBoxes
            .sortedBy { it.frameIndex }
            .map { position -> BoxPosition(position.x, position.y) }
        selectedNumber = selectedNumber.coerceIn(1, boxPositions.size.coerceAtLeast(1))
    }

    LaunchedEffect(readyAnimationSettings) {
        val normalizedFrames = readyAnimationSettings.frameSequence.ifEmpty { listOf(0) }
        appliedReadyFrames = normalizedFrames
        appliedReadyIntervalMs = readyAnimationSettings.intervalMs
        readyFrameInput = normalizedFrames.joinToString(separator = ",") { value -> (value + 1).toString() }
        readyIntervalInput = readyAnimationSettings.intervalMs.toString()
    }

    LaunchedEffect(talkingAnimationSettings) {
        val normalizedFrames = talkingAnimationSettings.frameSequence.ifEmpty { listOf(0) }
        appliedTalkingFrames = normalizedFrames
        appliedTalkingIntervalMs = talkingAnimationSettings.intervalMs
        talkingFrameInput = normalizedFrames.joinToString(separator = ",") { value -> (value + 1).toString() }
        talkingIntervalInput = talkingAnimationSettings.intervalMs.toString()
    }

    LaunchedEffect(readyInsertionAnimationSettings) {
        val normalizedFrames = readyInsertionAnimationSettings.frameSequence.ifEmpty { listOf(0) }
        appliedReadyInsertionFrames = normalizedFrames
        appliedReadyInsertionIntervalMs = readyInsertionAnimationSettings.intervalMs
        appliedReadyInsertionEveryNLoops = readyInsertionAnimationSettings.everyNLoops
        appliedReadyInsertionProbabilityPercent = readyInsertionAnimationSettings.probabilityPercent
        appliedReadyInsertionCooldownLoops = readyInsertionAnimationSettings.cooldownLoops
        appliedReadyInsertionEnabled = readyInsertionAnimationSettings.enabled
        appliedReadyInsertionExclusive = readyInsertionAnimationSettings.exclusive
        readyInsertionFrameInput = normalizedFrames.joinToString(separator = ",") { value -> (value + 1).toString() }
        readyInsertionIntervalInput = readyInsertionAnimationSettings.intervalMs.toString()
        readyInsertionEveryNInput = readyInsertionAnimationSettings.everyNLoops.toString()
        readyInsertionProbabilityInput = readyInsertionAnimationSettings.probabilityPercent.toString()
        readyInsertionCooldownInput = readyInsertionAnimationSettings.cooldownLoops.toString()
        readyInsertionEnabled = readyInsertionAnimationSettings.enabled
        readyInsertionExclusive = readyInsertionAnimationSettings.exclusive
    }

    LaunchedEffect(talkingInsertionAnimationSettings) {
        val normalizedFrames = talkingInsertionAnimationSettings.frameSequence.ifEmpty { listOf(0) }
        appliedTalkingInsertionFrames = normalizedFrames
        appliedTalkingInsertionIntervalMs = talkingInsertionAnimationSettings.intervalMs
        appliedTalkingInsertionEveryNLoops = talkingInsertionAnimationSettings.everyNLoops
        appliedTalkingInsertionProbabilityPercent = talkingInsertionAnimationSettings.probabilityPercent
        appliedTalkingInsertionCooldownLoops = talkingInsertionAnimationSettings.cooldownLoops
        appliedTalkingInsertionEnabled = talkingInsertionAnimationSettings.enabled
        appliedTalkingInsertionExclusive = talkingInsertionAnimationSettings.exclusive
        talkingInsertionFrameInput = normalizedFrames.joinToString(separator = ",") { value -> (value + 1).toString() }
        talkingInsertionIntervalInput = talkingInsertionAnimationSettings.intervalMs.toString()
        talkingInsertionEveryNInput = talkingInsertionAnimationSettings.everyNLoops.toString()
        talkingInsertionProbabilityInput = talkingInsertionAnimationSettings.probabilityPercent.toString()
        talkingInsertionCooldownInput = talkingInsertionAnimationSettings.cooldownLoops.toString()
        talkingInsertionEnabled = talkingInsertionAnimationSettings.enabled
        talkingInsertionExclusive = talkingInsertionAnimationSettings.exclusive
    }

    val selectedIndex = selectedNumber - 1
    val selectedPosition = boxPositions.getOrNull(selectedIndex)

    fun clampAllPositions(newBoxSizePx: Int): List<BoxPosition> =
        boxPositions.map { position ->
            clampPosition(position, newBoxSizePx, imageBitmap.width, imageBitmap.height)
        }

    fun updateBoxSize(delta: Int) {
        val maxSize = minOf(imageBitmap.width, imageBitmap.height).coerceAtLeast(1)
        val desiredSize = (boxSizePx + delta).coerceIn(1, maxSize)
        if (desiredSize != boxSizePx) {
            boxSizePx = desiredSize
            boxPositions = clampAllPositions(desiredSize)
        }
    }

    fun updateSelectedPosition(deltaX: Int, deltaY: Int) {
        if (selectedIndex !in boxPositions.indices) return
        val current = boxPositions[selectedIndex]
        val updated = clampPosition(
            position = BoxPosition(
                x = current.x + deltaX,
                y = current.y + deltaY
            ),
            boxSizePx = boxSizePx,
            sheetWidth = imageBitmap.width,
            sheetHeight = imageBitmap.height
        )
        if (updated != current) {
            boxPositions = boxPositions.toMutableList().also { positions ->
                positions[selectedIndex] = updated
            }
        }
    }

    fun buildSpriteSheetConfig(): SpriteSheetConfig {
        val spriteSheetConfig = SpriteSheetConfig(
            rows = 3,
            cols = 3,
            frameWidth = boxSizePx,
            frameHeight = boxSizePx,
            boxes = boxPositions.mapIndexed { index, position ->
                SpriteSheetBoxPosition(
                    frameIndex = index,
                    x = position.x,
                    y = position.y,
                    width = boxSizePx,
                    height = boxSizePx
                )
            }
        )
        return spriteSheetConfig
    }

    data class ValidationResult<T>(val value: T?, val error: String?)

    fun parseFrameSequenceInput(
        input: String,
        frameCount: Int,
        allowDuplicates: Boolean = false,
        duplicateErrorMessage: String = "重複しないように入力してください",
    ): ValidationResult<List<Int>> {
        val normalized = input
            .replace("，", ",")
            .replace("、", ",")
            .split(",")
            .map { token -> token.trim() }
            .filter { token -> token.isNotEmpty() }
        val maxFrameIndex = frameCount.coerceAtLeast(1)
        if (normalized.isEmpty()) return ValidationResult(null, "1〜${maxFrameIndex}のカンマ区切りで入力してください")
        val parsed = normalized.mapNotNull { token -> token.toIntOrNull() }
        if (parsed.size != normalized.size) return ValidationResult(null, "数値で入力してください")
        if (parsed.any { value -> value !in 1..maxFrameIndex }) {
            return ValidationResult(null, "1〜${maxFrameIndex}の範囲で入力してください")
        }
        if (!allowDuplicates && parsed.size != parsed.distinct().size) {
            return ValidationResult(null, duplicateErrorMessage)
        }
        return ValidationResult(parsed.map { value -> value - 1 }, null)
    }

    fun parseIntervalMsInput(input: String): ValidationResult<Int> {
        val rawValue = input.trim().toIntOrNull() ?: return ValidationResult(null, "数値を入力してください")
        if (rawValue < ReadyAnimationSettings.MIN_INTERVAL_MS) {
            return ValidationResult(null, "${ReadyAnimationSettings.MIN_INTERVAL_MS}以上で入力してください")
        }
        if (rawValue > ReadyAnimationSettings.MAX_INTERVAL_MS) {
            return ValidationResult(
                null,
                "${ReadyAnimationSettings.MIN_INTERVAL_MS}〜${ReadyAnimationSettings.MAX_INTERVAL_MS}の範囲で入力してください"
            )
        }
        return ValidationResult(rawValue, null)
    }

    fun parseEveryNLoopsInput(input: String): ValidationResult<Int> {
        val rawValue = input.trim().toIntOrNull() ?: return ValidationResult(null, "数値を入力してください")
        if (rawValue < InsertionAnimationSettings.MIN_EVERY_N_LOOPS) {
            return ValidationResult(null, "${InsertionAnimationSettings.MIN_EVERY_N_LOOPS}以上で入力してください")
        }
        return ValidationResult(rawValue, null)
    }

    fun parseProbabilityPercentInput(input: String): ValidationResult<Int> {
        val rawValue = input.trim().toIntOrNull() ?: return ValidationResult(null, "数値を入力してください")
        if (rawValue !in InsertionAnimationSettings.MIN_PROBABILITY_PERCENT..InsertionAnimationSettings.MAX_PROBABILITY_PERCENT) {
            return ValidationResult(null, "0〜100の範囲で入力してください")
        }
        return ValidationResult(rawValue, null)
    }

    fun parseCooldownLoopsInput(input: String): ValidationResult<Int> {
        val rawValue = input.trim().toIntOrNull() ?: return ValidationResult(null, "数値を入力してください")
        if (rawValue < InsertionAnimationSettings.MIN_COOLDOWN_LOOPS) {
            return ValidationResult(null, "0以上で入力してください")
        }
        return ValidationResult(rawValue, null)
    }

    fun validateBaseInputs(target: AnimationType): ReadyAnimationSettings? {
        val (frameInput, intervalInput) = when (target) {
            AnimationType.READY -> readyFrameInput to readyIntervalInput
            AnimationType.TALKING -> talkingFrameInput to talkingIntervalInput
        }
        val framesResult = parseFrameSequenceInput(
            input = frameInput,
            frameCount = spriteSheetConfig.frameCount,
            allowDuplicates = true
        )
        val intervalResult = parseIntervalMsInput(intervalInput)

        when (target) {
            AnimationType.READY -> {
                readyFramesError = framesResult.error
                readyIntervalError = intervalResult.error
            }

            AnimationType.TALKING -> {
                talkingFramesError = framesResult.error
                talkingIntervalError = intervalResult.error
            }
        }

        val frames = framesResult.value
        val interval = intervalResult.value
        if (frames != null && interval != null) {
            return ReadyAnimationSettings(
                frameSequence = frames,
                intervalMs = interval,
            )
        }
        return null
    }

    fun validateInsertionInputs(target: AnimationType): InsertionAnimationSettings? {
        val frameInput: String
        val intervalInput: String
        val everyNInput: String
        val probabilityInput: String
        val cooldownInput: String
        val exclusive: Boolean
        when (target) {
            AnimationType.READY -> {
                frameInput = readyInsertionFrameInput
                intervalInput = readyInsertionIntervalInput
                everyNInput = readyInsertionEveryNInput
                probabilityInput = readyInsertionProbabilityInput
                cooldownInput = readyInsertionCooldownInput
                exclusive = readyInsertionExclusive
            }

            AnimationType.TALKING -> {
                frameInput = talkingInsertionFrameInput
                intervalInput = talkingInsertionIntervalInput
                everyNInput = talkingInsertionEveryNInput
                probabilityInput = talkingInsertionProbabilityInput
                cooldownInput = talkingInsertionCooldownInput
                exclusive = talkingInsertionExclusive
            }
        }
        val framesResult = parseFrameSequenceInput(
            input = frameInput,
            frameCount = spriteSheetConfig.frameCount,
            duplicateErrorMessage = "挿入フレームは重複しないように入力してください"
        )
        val intervalResult = parseIntervalMsInput(intervalInput)
        val everyNResult = parseEveryNLoopsInput(everyNInput)
        val probabilityResult = parseProbabilityPercentInput(probabilityInput)
        val cooldownResult = parseCooldownLoopsInput(cooldownInput)

        when (target) {
            AnimationType.READY -> {
                readyInsertionFramesError = framesResult.error
                readyInsertionIntervalError = intervalResult.error
                readyInsertionEveryNError = everyNResult.error
                readyInsertionProbabilityError = probabilityResult.error
                readyInsertionCooldownError = cooldownResult.error
            }

            AnimationType.TALKING -> {
                talkingInsertionFramesError = framesResult.error
                talkingInsertionIntervalError = intervalResult.error
                talkingInsertionEveryNError = everyNResult.error
                talkingInsertionProbabilityError = probabilityResult.error
                talkingInsertionCooldownError = cooldownResult.error
            }
        }

        val frames = framesResult.value
        val interval = intervalResult.value
        val everyN = everyNResult.value
        val probability = probabilityResult.value
        val cooldown = cooldownResult.value

        if (frames != null && interval != null && everyN != null && probability != null && cooldown != null) {
            return InsertionAnimationSettings(
                enabled = true,
                frameSequence = frames,
                intervalMs = interval,
                everyNLoops = everyN,
                probabilityPercent = probability,
                cooldownLoops = cooldown,
                exclusive = exclusive,
            )
        }
        return null
    }

    fun saveSpriteSheetConfig() {
        coroutineScope.launch {
            runCatching {
                val config = buildSpriteSheetConfig()
                val error = config.validate()
                if (error != null) {
                    throw IllegalArgumentException(error)
                }
                settingsPreferences.saveSpriteSheetConfig(config)
            }.onSuccess {
                snackbarHostState.showSnackbar("保存しました")
            }.onFailure { throwable ->
                snackbarHostState.showSnackbar("保存に失敗しました: ${throwable.message}")
            }
        }
    }

    fun copySpriteSheetConfig() {
        coroutineScope.launch {
            runCatching {
                val config = buildSpriteSheetConfig()
                val error = config.validate()
                if (error != null) {
                    throw IllegalArgumentException(error)
                }
                val jsonString = config.toJson()
                clipboardManager.setText(AnnotatedString(jsonString))
            }.onSuccess {
                snackbarHostState.showSnackbar("JSONをコピーしました")
            }.onFailure { throwable ->
                snackbarHostState.showSnackbar("コピーに失敗しました: ${throwable.message}")
            }
        }
    }

    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val footerHeight = 40.dp

    val onAnimationApply: () -> Unit = onAnimationApply@{
        val validatedBase = validateBaseInputs(selectedAnimation) ?: run {
            coroutineScope.launch { snackbarHostState.showSnackbar("入力が不正です") }
            return@onAnimationApply
        }
        val validatedInsertion = if (
            (selectedAnimation == AnimationType.READY && readyInsertionEnabled) ||
            (selectedAnimation == AnimationType.TALKING && talkingInsertionEnabled)
        ) {
            validateInsertionInputs(selectedAnimation) ?: run {
                coroutineScope.launch { snackbarHostState.showSnackbar("入力が不正です") }
                return@onAnimationApply
            }
        } else null
        when (selectedAnimation) {
            AnimationType.READY -> {
                appliedReadyFrames = validatedBase.frameSequence
                appliedReadyIntervalMs = validatedBase.intervalMs
                val insertion = validatedInsertion ?: InsertionAnimationSettings(
                    enabled = false,
                    frameSequence = appliedReadyInsertionFrames,
                    intervalMs = appliedReadyInsertionIntervalMs,
                    everyNLoops = appliedReadyInsertionEveryNLoops,
                    probabilityPercent = appliedReadyInsertionProbabilityPercent,
                    cooldownLoops = appliedReadyInsertionCooldownLoops,
                    exclusive = appliedReadyInsertionExclusive,
                )
                appliedReadyInsertionEnabled = insertion.enabled
                appliedReadyInsertionFrames = insertion.frameSequence
                appliedReadyInsertionIntervalMs = insertion.intervalMs
                appliedReadyInsertionEveryNLoops = insertion.everyNLoops
                appliedReadyInsertionProbabilityPercent = insertion.probabilityPercent
                appliedReadyInsertionCooldownLoops = insertion.cooldownLoops
                appliedReadyInsertionExclusive = insertion.exclusive
            }

            AnimationType.TALKING -> {
                appliedTalkingFrames = validatedBase.frameSequence
                appliedTalkingIntervalMs = validatedBase.intervalMs
                val insertion = validatedInsertion ?: InsertionAnimationSettings(
                    enabled = false,
                    frameSequence = appliedTalkingInsertionFrames,
                    intervalMs = appliedTalkingInsertionIntervalMs,
                    everyNLoops = appliedTalkingInsertionEveryNLoops,
                    probabilityPercent = appliedTalkingInsertionProbabilityPercent,
                    cooldownLoops = appliedTalkingInsertionCooldownLoops,
                    exclusive = appliedTalkingInsertionExclusive,
                )
                appliedTalkingInsertionEnabled = insertion.enabled
                appliedTalkingInsertionFrames = insertion.frameSequence
                appliedTalkingInsertionIntervalMs = insertion.intervalMs
                appliedTalkingInsertionEveryNLoops = insertion.everyNLoops
                appliedTalkingInsertionProbabilityPercent = insertion.probabilityPercent
                appliedTalkingInsertionCooldownLoops = insertion.cooldownLoops
                appliedTalkingInsertionExclusive = insertion.exclusive
            }
        }
        coroutineScope.launch {
            snackbarHostState.showSnackbar("プレビューに適用しました")
        }
    }

    val onAnimationSave: () -> Unit = onAnimationSave@{
        val validatedBase = validateBaseInputs(selectedAnimation) ?: run {
            coroutineScope.launch { snackbarHostState.showSnackbar("入力が不正です") }
            return@onAnimationSave
        }
        val validatedInsertion = if (
            (selectedAnimation == AnimationType.READY && readyInsertionEnabled) ||
            (selectedAnimation == AnimationType.TALKING && talkingInsertionEnabled)
        ) {
            validateInsertionInputs(selectedAnimation) ?: run {
                coroutineScope.launch { snackbarHostState.showSnackbar("入力が不正です") }
                return@onAnimationSave
            }
        } else null
        when (selectedAnimation) {
            AnimationType.READY -> {
                appliedReadyFrames = validatedBase.frameSequence
                appliedReadyIntervalMs = validatedBase.intervalMs
                val insertion = validatedInsertion ?: InsertionAnimationSettings(
                    enabled = false,
                    frameSequence = appliedReadyInsertionFrames,
                    intervalMs = appliedReadyInsertionIntervalMs,
                    everyNLoops = appliedReadyInsertionEveryNLoops,
                    probabilityPercent = appliedReadyInsertionProbabilityPercent,
                    cooldownLoops = appliedReadyInsertionCooldownLoops,
                    exclusive = appliedReadyInsertionExclusive,
                )
                appliedReadyInsertionEnabled = insertion.enabled
                appliedReadyInsertionFrames = insertion.frameSequence
                appliedReadyInsertionIntervalMs = insertion.intervalMs
                appliedReadyInsertionEveryNLoops = insertion.everyNLoops
                appliedReadyInsertionProbabilityPercent = insertion.probabilityPercent
                appliedReadyInsertionCooldownLoops = insertion.cooldownLoops
                appliedReadyInsertionExclusive = insertion.exclusive
                coroutineScope.launch {
                    settingsPreferences.saveReadyAnimationSettings(validatedBase)
                    settingsPreferences.saveReadyInsertionAnimationSettings(insertion)
                    snackbarHostState.showSnackbar("Readyアニメを保存しました")
                }
            }

            AnimationType.TALKING -> {
                appliedTalkingFrames = validatedBase.frameSequence
                appliedTalkingIntervalMs = validatedBase.intervalMs
                val insertion = validatedInsertion ?: InsertionAnimationSettings(
                    enabled = false,
                    frameSequence = appliedTalkingInsertionFrames,
                    intervalMs = appliedTalkingInsertionIntervalMs,
                    everyNLoops = appliedTalkingInsertionEveryNLoops,
                    probabilityPercent = appliedTalkingInsertionProbabilityPercent,
                    cooldownLoops = appliedTalkingInsertionCooldownLoops,
                    exclusive = appliedTalkingInsertionExclusive,
                )
                appliedTalkingInsertionEnabled = insertion.enabled
                appliedTalkingInsertionFrames = insertion.frameSequence
                appliedTalkingInsertionIntervalMs = insertion.intervalMs
                appliedTalkingInsertionEveryNLoops = insertion.everyNLoops
                appliedTalkingInsertionProbabilityPercent = insertion.probabilityPercent
                appliedTalkingInsertionCooldownLoops = insertion.cooldownLoops
                appliedTalkingInsertionExclusive = insertion.exclusive
                coroutineScope.launch {
                    settingsPreferences.saveTalkingAnimationSettings(validatedBase)
                    settingsPreferences.saveTalkingInsertionAnimationSettings(insertion)
                    snackbarHostState.showSnackbar("Talkingアニメを保存しました")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection),
            top = innerPadding.calculateTopPadding(),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 8.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.back),
                            contentDescription = "戻る"
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Text("Sprite Settings")
                        Spacer(modifier = Modifier.height(8.dp))
                        TabRow(selectedTabIndex = tabIndex) {
                            Tab(
                                selected = tabIndex == 0,
                                onClick = { tabIndex = 0 },
                                text = { Text("調整") }
                            )
                            Tab(
                                selected = tabIndex == 1,
                                onClick = { tabIndex = 1 },
                                text = { Text("アニメ") }
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = true)
                        ) {
                            when (tabIndex) {
                                0 -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("元画像解像度: ${imageBitmap.width} x ${imageBitmap.height} px")
                                            Text("表示倍率: ${"%.2f".format(displayScale)}x")
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .padding(top = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.lami_sprite_3x3_288),
                                                contentDescription = "Sprite Preview",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .onSizeChanged { newContainerSize: IntSize ->
                                                        containerSize = newContainerSize
                                                        if (imageBitmap.width != 0) {
                                                            displayScale = newContainerSize.width / imageBitmap.width.toFloat()
                                                        }
                                                    },
                                                contentScale = ContentScale.Fit
                                            )
                                            if (selectedPosition != null && containerSize.width > 0 && containerSize.height > 0) {
                                                Canvas(modifier = Modifier.fillMaxSize()) {
                                                    val scaleX = this.size.width / imageBitmap.width
                                                    val scaleY = this.size.height / imageBitmap.height
                                                    drawRect(
                                                        color = Color.Red,
                                                        topLeft = Offset(
                                                            x = selectedPosition.x * scaleX,
                                                            y = selectedPosition.y * scaleY
                                                        ),
                                                        size = Size(
                                                            width = boxSizePx * scaleX,
                                                            height = boxSizePx * scaleY
                                                        ),
                                                        style = Stroke(width = 2.dp.toPx())
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("操作バーは下部に固定されています")
                                            Text("選択中: ${selectedNumber}/9")
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        SpriteSettingsControls(
                                            selectedNumber = selectedNumber,
                                            selectedPosition = selectedPosition,
                                            boxSizePx = boxSizePx,
                                            onPrev = { selectedNumber = if (selectedNumber <= 1) 9 else selectedNumber - 1 },
                                            onNext = { selectedNumber = if (selectedNumber >= 9) 1 else selectedNumber + 1 },
                                            onMoveXNegative = { updateSelectedPosition(deltaX = -1, deltaY = 0) },
                                            onMoveXPositive = { updateSelectedPosition(deltaX = 1, deltaY = 0) },
                                            onMoveYNegative = { updateSelectedPosition(deltaX = 0, deltaY = -1) },
                                            onMoveYPositive = { updateSelectedPosition(deltaX = 0, deltaY = 1) },
                                            onSizeDecrease = { updateBoxSize(-4) },
                                            onSizeIncrease = { updateBoxSize(4) },
                                            onCopy = { copySpriteSheetConfig() }
                                        )
                                    }
                                }

                                1 -> {
                                    val animationOptions = remember { AnimationType.options }
                                    val selectedFrameInput: String
                                    val selectedIntervalInput: String
                                    val selectedFramesError: String?
                                    val selectedIntervalError: String?
                                    val selectedInsertionFrameInput: String
                                    val selectedInsertionIntervalInput: String
                                    val selectedInsertionEveryNInput: String
                                    val selectedInsertionProbabilityInput: String
                                    val selectedInsertionCooldownInput: String
                                    val selectedInsertionEnabled: Boolean
                                    val selectedInsertionExclusive: Boolean
                                    val selectedInsertionFramesError: String?
                                    val selectedInsertionIntervalError: String?
                                    val selectedInsertionEveryNError: String?
                                    val selectedInsertionProbabilityError: String?
                                    val selectedInsertionCooldownError: String?
                                    when (selectedAnimation) {
                                        AnimationType.READY -> {
                                            selectedFrameInput = readyFrameInput
                                            selectedIntervalInput = readyIntervalInput
                                            selectedFramesError = readyFramesError
                                            selectedIntervalError = readyIntervalError
                                            selectedInsertionFrameInput = readyInsertionFrameInput
                                            selectedInsertionIntervalInput = readyInsertionIntervalInput
                                            selectedInsertionEveryNInput = readyInsertionEveryNInput
                                            selectedInsertionProbabilityInput = readyInsertionProbabilityInput
                                            selectedInsertionCooldownInput = readyInsertionCooldownInput
                                            selectedInsertionEnabled = readyInsertionEnabled
                                            selectedInsertionExclusive = readyInsertionExclusive
                                            selectedInsertionFramesError = readyInsertionFramesError
                                            selectedInsertionIntervalError = readyInsertionIntervalError
                                            selectedInsertionEveryNError = readyInsertionEveryNError
                                            selectedInsertionProbabilityError = readyInsertionProbabilityError
                                            selectedInsertionCooldownError = readyInsertionCooldownError
                                        }

                                        AnimationType.TALKING -> {
                                            selectedFrameInput = talkingFrameInput
                                            selectedIntervalInput = talkingIntervalInput
                                            selectedFramesError = talkingFramesError
                                            selectedIntervalError = talkingIntervalError
                                            selectedInsertionFrameInput = talkingInsertionFrameInput
                                            selectedInsertionIntervalInput = talkingInsertionIntervalInput
                                            selectedInsertionEveryNInput = talkingInsertionEveryNInput
                                            selectedInsertionProbabilityInput = talkingInsertionProbabilityInput
                                            selectedInsertionCooldownInput = talkingInsertionCooldownInput
                                            selectedInsertionEnabled = talkingInsertionEnabled
                                            selectedInsertionExclusive = talkingInsertionExclusive
                                            selectedInsertionFramesError = talkingInsertionFramesError
                                            selectedInsertionIntervalError = talkingInsertionIntervalError
                                            selectedInsertionEveryNError = talkingInsertionEveryNError
                                            selectedInsertionProbabilityError = talkingInsertionProbabilityError
                                            selectedInsertionCooldownError = talkingInsertionCooldownError
                                        }
                                    }
                                    val readyBaseSummary = remember(appliedReadyFrames, appliedReadyIntervalMs) {
                                        AnimationSummary(label = AnimationType.READY.label, frames = appliedReadyFrames, intervalMs = appliedReadyIntervalMs)
                                    }
                                    val talkingBaseSummary = remember(appliedTalkingFrames, appliedTalkingIntervalMs) {
                                        AnimationSummary(label = AnimationType.TALKING.label, frames = appliedTalkingFrames, intervalMs = appliedTalkingIntervalMs)
                                    }
                                    val readyInsertionPreview = remember(
                                        readyInsertionFrameInput,
                                        readyInsertionIntervalInput,
                                        readyInsertionEveryNInput,
                                        readyInsertionProbabilityInput,
                                        readyInsertionCooldownInput,
                                        readyInsertionEnabled,
                                        readyInsertionExclusive,
                                        spriteSheetConfig.frameCount
                                    ) {
                                        buildInsertionPreviewSummary(
                                            label = "挿入",
                                            enabled = readyInsertionEnabled,
                                            frameInput = readyInsertionFrameInput,
                                            intervalInput = readyInsertionIntervalInput,
                                            everyNInput = readyInsertionEveryNInput,
                                            probabilityInput = readyInsertionProbabilityInput,
                                            cooldownInput = readyInsertionCooldownInput,
                                            exclusive = readyInsertionExclusive,
                                            frameCount = spriteSheetConfig.frameCount
                                        )
                                    }
                                    val talkingInsertionPreview = remember(
                                        talkingInsertionFrameInput,
                                        talkingInsertionIntervalInput,
                                        talkingInsertionEveryNInput,
                                        talkingInsertionProbabilityInput,
                                        talkingInsertionCooldownInput,
                                        talkingInsertionEnabled,
                                        talkingInsertionExclusive,
                                        spriteSheetConfig.frameCount
                                    ) {
                                        buildInsertionPreviewSummary(
                                            label = "挿入",
                                            enabled = talkingInsertionEnabled,
                                            frameInput = talkingInsertionFrameInput,
                                            intervalInput = talkingInsertionIntervalInput,
                                            everyNInput = talkingInsertionEveryNInput,
                                            probabilityInput = talkingInsertionProbabilityInput,
                                            cooldownInput = talkingInsertionCooldownInput,
                                            exclusive = talkingInsertionExclusive,
                                            frameCount = spriteSheetConfig.frameCount
                                        )
                                    }
                                    val (selectedInsertionSummary, selectedInsertionPreviewValues) = when (selectedAnimation) {
                                        AnimationType.READY -> readyInsertionPreview
                                        AnimationType.TALKING -> talkingInsertionPreview
                                    }
                                    val selectedBaseSummary = when (selectedAnimation) {
                                        AnimationType.READY -> readyBaseSummary
                                        AnimationType.TALKING -> talkingBaseSummary
                                    }
                                    val selectionState = AnimationSelectionState(
                                        selectedAnimation = selectedAnimation,
                                        animationOptions = animationOptions,
                                        onSelectedAnimationChange = { selectedAnimation = it }
                                    )
                                    val baseState = BaseAnimationUiState(
                                        frameInput = selectedFrameInput,
                                        onFrameInputChange = { updated ->
                                            when (selectedAnimation) {
                                                AnimationType.READY -> {
                                                    readyFrameInput = updated
                                                    readyFramesError = null
                                                }

                                                AnimationType.TALKING -> {
                                                    talkingFrameInput = updated
                                                    talkingFramesError = null
                                                }
                                            }
                                        },
                                        intervalInput = selectedIntervalInput,
                                        onIntervalInputChange = { updated ->
                                            when (selectedAnimation) {
                                                AnimationType.READY -> {
                                                    readyIntervalInput = updated
                                                    readyIntervalError = null
                                                }

                                                AnimationType.TALKING -> {
                                                    talkingIntervalInput = updated
                                                    talkingIntervalError = null
                                                }
                                            }
                                        },
                                        framesError = selectedFramesError,
                                        intervalError = selectedIntervalError,
                                        summary = selectedBaseSummary
                                    )
                                    val insertionState = InsertionAnimationUiState(
                                        frameInput = selectedInsertionFrameInput,
                                        onFrameInputChange = { updated ->
                                            when (selectedAnimation) {
                                                AnimationType.READY -> {
                                                    readyInsertionFrameInput = updated
                                                    readyInsertionFramesError = null
                                                }

                                                AnimationType.TALKING -> {
                                                    talkingInsertionFrameInput = updated
                                                    talkingInsertionFramesError = null
                                                }
                                            }
                                        },
                                        intervalInput = selectedInsertionIntervalInput,
                                        onIntervalInputChange = { updated ->
                                            when (selectedAnimation) {
                                                AnimationType.READY -> {
                                                    readyInsertionIntervalInput = updated
                                                    readyInsertionIntervalError = null
                                                }

                                                AnimationType.TALKING -> {
                                                    talkingInsertionIntervalInput = updated
                                                    talkingInsertionIntervalError = null
                                                }
                                            }
                                        },
                                        everyNInput = selectedInsertionEveryNInput,
                                        onEveryNInputChange = { updated ->
                                            when (selectedAnimation) {
                                                AnimationType.READY -> {
                                                    readyInsertionEveryNInput = updated
                                                    readyInsertionEveryNError = null
                                                }

                                                AnimationType.TALKING -> {
                                                    talkingInsertionEveryNInput = updated
                                                    talkingInsertionEveryNError = null
                                                }
                                            }
                                        },
                                        probabilityInput = selectedInsertionProbabilityInput,
                                        onProbabilityInputChange = { updated ->
                                            when (selectedAnimation) {
                                                AnimationType.READY -> {
                                                    readyInsertionProbabilityInput = updated
                                                    readyInsertionProbabilityError = null
                                                }

                                                AnimationType.TALKING -> {
                                                    talkingInsertionProbabilityInput = updated
                                                    talkingInsertionProbabilityError = null
                                                }
                                            }
                                        },
                                        cooldownInput = selectedInsertionCooldownInput,
                                        onCooldownInputChange = { updated ->
                                            when (selectedAnimation) {
                                                AnimationType.READY -> {
                                                    readyInsertionCooldownInput = updated
                                                    readyInsertionCooldownError = null
                                                }

                                                AnimationType.TALKING -> {
                                                    talkingInsertionCooldownInput = updated
                                                    talkingInsertionCooldownError = null
                                                }
                                            }
                                        },
                                        enabled = selectedInsertionEnabled,
                                        onEnabledChange = { checked ->
                                            when (selectedAnimation) {
                                                AnimationType.READY -> readyInsertionEnabled = checked
                                                AnimationType.TALKING -> talkingInsertionEnabled = checked
                                            }
                                        },
                                        exclusive = selectedInsertionExclusive,
                                        onExclusiveChange = { checked ->
                                            when (selectedAnimation) {
                                                AnimationType.READY -> readyInsertionExclusive = checked
                                                AnimationType.TALKING -> talkingInsertionExclusive = checked
                                            }
                                        },
                                        framesError = selectedInsertionFramesError,
                                        intervalError = selectedInsertionIntervalError,
                                        everyNError = selectedInsertionEveryNError,
                                        probabilityError = selectedInsertionProbabilityError,
                                        cooldownError = selectedInsertionCooldownError,
                                        summary = selectedInsertionSummary,
                                        previewValues = selectedInsertionPreviewValues
                                    )
                                    ReadyAnimationTab(
                                        imageBitmap = imageBitmap,
                                        spriteSheetConfig = spriteSheetConfig,
                                        selectionState = selectionState,
                                        baseState = baseState,
                                        insertionState = insertionState,
                                        isImeVisible = imeVisible,
                                        contentPadding = contentPadding,
                                        footerHeight = footerHeight
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SpriteSettingsFooter(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                onUpdate = {
                    if (tabIndex == 0) {
                        coroutineScope.launch { snackbarHostState.showSnackbar("プレビューに適用しました") }
                    } else {
                        onAnimationApply()
                    }
                },
                onSave = {
                    if (tabIndex == 0) {
                        saveSpriteSheetConfig()
                    } else {
                        onAnimationSave()
                    }
                }
            )
        }
    }
}

private data class AnimationSelectionState(
    val selectedAnimation: AnimationType,
    val animationOptions: List<AnimationType>,
    val onSelectedAnimationChange: (AnimationType) -> Unit,
)

private data class BaseAnimationUiState(
    val frameInput: String,
    val onFrameInputChange: (String) -> Unit,
    val intervalInput: String,
    val onIntervalInputChange: (String) -> Unit,
    val framesError: String?,
    val intervalError: String?,
    val summary: AnimationSummary,
)

private data class InsertionAnimationUiState(
    val frameInput: String,
    val onFrameInputChange: (String) -> Unit,
    val intervalInput: String,
    val onIntervalInputChange: (String) -> Unit,
    val everyNInput: String,
    val onEveryNInputChange: (String) -> Unit,
    val probabilityInput: String,
    val onProbabilityInputChange: (String) -> Unit,
    val cooldownInput: String,
    val onCooldownInputChange: (String) -> Unit,
    val enabled: Boolean,
    val onEnabledChange: (Boolean) -> Unit,
    val exclusive: Boolean,
    val onExclusiveChange: (Boolean) -> Unit,
    val framesError: String?,
    val intervalError: String?,
    val everyNError: String?,
    val probabilityError: String?,
    val cooldownError: String?,
    val summary: AnimationSummary,
    val previewValues: InsertionPreviewValues,
)

@Composable
private fun ReadyAnimationTab(
    imageBitmap: ImageBitmap,
    spriteSheetConfig: SpriteSheetConfig,
    selectionState: AnimationSelectionState,
    baseState: BaseAnimationUiState,
    insertionState: InsertionAnimationUiState,
    isImeVisible: Boolean,
    contentPadding: PaddingValues,
    footerHeight: Dp,
) {
    val selectedAnimation = selectionState.selectedAnimation
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val onFieldFocused: (Int) -> Unit = { targetIndex ->
        coroutineScope.launch { lazyListState.animateScrollToItem(index = targetIndex) }
    }
    val needsBottomInset by remember(lazyListState) {
        derivedStateOf {
            val info = lazyListState.layoutInfo
            val totalItemsCount = info.totalItemsCount
            if (totalItemsCount == 0) return@derivedStateOf false
            val visibleItems = info.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf false

            val firstItem = visibleItems.first()
            val lastItem = visibleItems.last()
            val canScrollUp = firstItem.index > 0 || firstItem.offset < info.viewportStartOffset
            val canScrollDown = lastItem.index < totalItemsCount - 1 ||
                (lastItem.offset + lastItem.size) > info.viewportEndOffset

            canScrollUp || canScrollDown
        }
    }
    val layoutDirection = LocalLayoutDirection.current
    val bottomContentPadding = if (isImeVisible) {
        contentPadding.calculateBottomPadding() + 2.dp
    } else {
        contentPadding.calculateBottomPadding() +
            if (needsBottomInset) footerHeight + 2.dp else 0.dp
    }
    val listContentPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        top = contentPadding.calculateTopPadding() + 20.dp,
        end = contentPadding.calculateEndPadding(layoutDirection),
        bottom = bottomContentPadding
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background
        ) {
            ReadyAnimationPreviewPane(
                imageBitmap = imageBitmap,
                spriteSheetConfig = spriteSheetConfig,
                baseSummary = baseState.summary,
                insertionSummary = insertionState.summary,
                insertionPreviewValues = insertionState.previewValues,
                insertionEnabled = insertionState.enabled,
                isImeVisible = isImeVisible,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = listContentPadding
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
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
                                selectedAnimation.label
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = baseState.frameInput,
                    onValueChange = baseState.onFrameInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .onFocusEvent { event ->
                            if (event.isFocused) onFieldFocused(1)
                        },
                    label = { Text("フレーム列 (例: 1,2,3)") },
                    singleLine = true,
                    isError = baseState.framesError != null,
                    supportingText = baseState.framesError?.let { errorText ->
                        { Text(errorText, color = Color.Red) }
                    }
                )
            }
            item {
                OutlinedTextField(
                    value = baseState.intervalInput,
                    onValueChange = baseState.onIntervalInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .onFocusEvent { event ->
                            if (event.isFocused) onFieldFocused(2)
                        },
                    label = { Text("周期 (ms)") },
                    singleLine = true,
                    isError = baseState.intervalError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = baseState.intervalError?.let { errorText ->
                        { Text(errorText, color = Color.Red) }
                    }
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
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
            item {
                AnimatedVisibility(visible = insertionState.enabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = insertionState.frameInput,
                            onValueChange = insertionState.onFrameInputChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .onFocusEvent { event ->
                                    if (event.isFocused) onFieldFocused(4)
                                },
                            label = { Text("挿入フレーム列（例: 4,5,6）") },
                            singleLine = true,
                            isError = insertionState.framesError != null,
                            supportingText = insertionState.framesError?.let { errorText ->
                                { Text(errorText, color = Color.Red) }
                            }
                        )
                        OutlinedTextField(
                            value = insertionState.intervalInput,
                            onValueChange = insertionState.onIntervalInputChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .onFocusEvent { event ->
                                    if (event.isFocused) onFieldFocused(5)
                                },
                            label = { Text("挿入周期（ms）") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = insertionState.intervalError != null,
                            supportingText = insertionState.intervalError?.let { errorText ->
                                { Text(errorText, color = Color.Red) }
                            }
                        )
                        OutlinedTextField(
                            value = insertionState.everyNInput,
                            onValueChange = insertionState.onEveryNInputChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .onFocusEvent { event ->
                                    if (event.isFocused) onFieldFocused(6)
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
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .onFocusEvent { event ->
                                    if (event.isFocused) onFieldFocused(7)
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
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .onFocusEvent { event ->
                                    if (event.isFocused) onFieldFocused(8)
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Exclusive（Ready中は挿入しない）")
                                Text(
                                    text = "ONにするとReady再生中は挿入を抑制します",
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimationDropdown(
    items: List<AnimationType>,
    selectedItem: AnimationType,
    onSelectedItemChange: (AnimationType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        TextField(
            value = selectedItem.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("アニメ種別") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        onSelectedItemChange(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ReadyAnimationPreview(
    imageBitmap: ImageBitmap,
    spriteSheetConfig: SpriteSheetConfig,
    summary: AnimationSummary,
    insertionSummary: AnimationSummary,
    insertionEnabled: Boolean,
    insertionPreviewValues: InsertionPreviewValues,
    spriteSizeDp: Dp,
    showDetails: Boolean,
    charYOffsetDp: Int,
    infoYOffsetDp: Int,
    detailsMaxHeightDp: Int,
    detailsMaxLines: Int,
    modifier: Modifier = Modifier,
) {
    val paramYOffsetDp = 3
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
    val insertionFrames = remember(insertionSummary, insertionEnabled) {
        if (insertionEnabled) insertionSummary.frames.ifEmpty { emptyList() } else emptyList()
    }
    val playbackFrames = remember(baseFrames, insertionFrames) {
        buildList {
            addAll(baseFrames)
            if (insertionFrames.isNotEmpty()) addAll(insertionFrames)
        }.ifEmpty { listOf(0) }
    }
    var currentFramePosition by remember(playbackFrames) { mutableIntStateOf(0) }
    val totalFrames = playbackFrames.size.coerceAtLeast(1)
    val isInsertionFrame = insertionFrames.isNotEmpty() && currentFramePosition >= baseFrames.size
    val currentIntervalMs = (if (isInsertionFrame) insertionSummary.intervalMs else summary.intervalMs)
        .coerceAtLeast(16)
    val currentFrameIndex = playbackFrames.getOrElse(currentFramePosition) { baseFrames.first() }
    val frameRegion = remember(normalizedConfig, currentFrameIndex) {
        val internalIndex = normalizedConfig.toInternalFrameIndex(currentFrameIndex) ?: return@remember null
        val box = normalizedConfig.boxes.getOrNull(internalIndex) ?: return@remember null
        SpriteFrameRegion(
            srcOffset = IntOffset(box.x, box.y),
            srcSize = IntSize(box.width, box.height)
        )
    }

    LaunchedEffect(playbackFrames, summary.intervalMs, insertionSummary.intervalMs) {
        currentFramePosition = 0
        while (isActive && playbackFrames.isNotEmpty()) {
            val isInsertion = insertionFrames.isNotEmpty() && currentFramePosition >= baseFrames.size
            val delayMs = (if (isInsertion) insertionSummary.intervalMs else summary.intervalMs)
                .coerceAtLeast(16)
            delay(delayMs.toLong())
            currentFramePosition = (currentFramePosition + 1) % playbackFrames.size
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top, // 右側情報ブロックを上寄せにする
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(spriteSizeDp)
                .offset(y = charYOffsetDp.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dstSize = IntSize(
                    width = size.width.roundToInt().coerceAtLeast(1),
                    height = size.height.roundToInt().coerceAtLeast(1)
                )
                drawFrameRegion(
                    sheet = imageBitmap,
                    region = frameRegion,
                    dstSize = dstSize,
                    placeholder = { offset, placeholderSize ->
                        drawFramePlaceholder(offset = offset, size = placeholderSize)
                    }
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                // TEMP: dev adjuster for info block Y offset (remove later)
                .offset(y = (paramYOffsetDp + infoYOffsetDp).dp), // TEMP: fix param Y to +3dp
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top)
        ) {
            Text(
                text = "フレーム: ${currentFramePosition + 1}/${totalFrames}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "周期: ${currentIntervalMs}ms",
                style = MaterialTheme.typography.bodySmall
            )
            AnimatedVisibility(visible = showDetails) {
                Column(
                    modifier = Modifier.heightIn(max = detailsMaxHeightDp.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Top)
                ) {
                    Text(
                        text = formatAppliedLine(summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = detailsMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (insertionEnabled) {
                        Text(
                            text = formatInsertionDetail(insertionSummary, insertionPreviewValues),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = detailsMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyAnimationPreviewPane(
    imageBitmap: ImageBitmap,
    spriteSheetConfig: SpriteSheetConfig,
    baseSummary: AnimationSummary,
    insertionSummary: AnimationSummary,
    insertionPreviewValues: InsertionPreviewValues,
    insertionEnabled: Boolean,
    isImeVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var innerBottomDp by rememberSaveable { mutableIntStateOf(0) }
    var outerBottomDp by rememberSaveable { mutableIntStateOf(0) }
    var innerVPadDp by rememberSaveable { mutableIntStateOf(if (isImeVisible) 0 else 2) }
    var charYOffsetDp by rememberSaveable { mutableIntStateOf(0) }
    var infoYOffsetDp by rememberSaveable { mutableIntStateOf(0) }
    var cardMinHeightDp by rememberSaveable { mutableIntStateOf(if (isImeVisible) 180 else 210) }
    var detailsMaxHeightDp by rememberSaveable { mutableIntStateOf(72) }
    var detailsMaxLines by rememberSaveable { mutableIntStateOf(3) }
    var headerSpacerDp by rememberSaveable { mutableIntStateOf(if (isImeVisible) 2 else 4) }
    var bodySpacerDp by rememberSaveable { mutableIntStateOf(if (isImeVisible) 4 else 8) }
    var contentHeightPx by remember { mutableIntStateOf(0) } // TEMP: dev content height capture
    val contentHeightDp = with(LocalDensity.current) { contentHeightPx.toDp() }

    Column(modifier = modifier) {
        var devExpanded by rememberSaveable { mutableStateOf(false) }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val maxHeightDp = if (isImeVisible) 220 else 300
                val effectiveMinDp = cardMinHeightDp.coerceAtMost(maxHeightDp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { devExpanded = !devExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "DEV ${if (devExpanded) "▴" else "▾"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "MinH:${effectiveMinDp}  InfoY:${infoYOffsetDp}  Details:${if (showDetails) "ON" else "OFF"}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AnimatedVisibility(visible = devExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Offsets",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "CharY:${charYOffsetDp}dp",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = {
                                        charYOffsetDp = (charYOffsetDp - 1).coerceIn(-200, 200)
                                    }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = {
                                        charYOffsetDp = (charYOffsetDp + 1).coerceIn(-200, 200)
                                    }
                                ) {
                                    Text("▼")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "InfoY:${infoYOffsetDp}dp / 情報ブロックY",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = {
                                        infoYOffsetDp = (infoYOffsetDp - 1).coerceIn(-200, 200)
                                    }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = {
                                        infoYOffsetDp = (infoYOffsetDp + 1).coerceIn(-200, 200)
                                    }
                                ) {
                                    Text("▼")
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Padding",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "OuterBottom:${abs(outerBottomDp)}dp / カード下余白",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = {
                                        outerBottomDp = (outerBottomDp + 1).coerceIn(0, 80)
                                    }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = {
                                        outerBottomDp = (outerBottomDp - 1).coerceIn(0, 80)
                                    }
                                ) {
                                    Text("▼")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "InnerBottom:${abs(innerBottomDp)}dp / 情報ブロック下余白",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = {
                                        innerBottomDp = (innerBottomDp + 1).coerceIn(0, 80)
                                    }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = {
                                        innerBottomDp = (innerBottomDp - 1).coerceIn(0, 80)
                                    }
                                ) {
                                    Text("▼")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "InnerVPad:${abs(innerVPadDp)}dp",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = {
                                        innerVPadDp = (innerVPadDp + 1).coerceIn(0, 24)
                                    }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = {
                                        innerVPadDp = (innerVPadDp - 1).coerceIn(0, 24)
                                    }
                                ) {
                                    Text("▼")
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "MinHeight:${effectiveMinDp}dp / カード最小高",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = {
                                        cardMinHeightDp = (cardMinHeightDp + 1).coerceIn(0, 320)
                                    }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = {
                                        cardMinHeightDp = (cardMinHeightDp - 1).coerceIn(0, 320)
                                    }
                                ) {
                                    Text("▼")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "DetailsMaxH:${detailsMaxHeightDp}dp / 詳細最大高",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = {
                                        detailsMaxHeightDp = (detailsMaxHeightDp + 1).coerceIn(0, 120)
                                    }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = {
                                        detailsMaxHeightDp = (detailsMaxHeightDp - 1).coerceIn(0, 120)
                                    }
                                ) {
                                    Text("▼")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "DetailsLines:${detailsMaxLines} / 詳細行数",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = {
                                        detailsMaxLines = (detailsMaxLines + 1).coerceIn(1, 6)
                                    }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = {
                                        detailsMaxLines = (detailsMaxLines - 1).coerceIn(1, 6)
                                    }
                                ) {
                                    Text("▼")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "HeaderSp:${headerSpacerDp}dp / 見出し余白",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = {
                                        headerSpacerDp = (headerSpacerDp + 1).coerceIn(0, 24)
                                    }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = {
                                        headerSpacerDp = (headerSpacerDp - 1).coerceIn(0, 24)
                                    }
                                ) {
                                    Text("▼")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "BodySp:${bodySpacerDp}dp / 本文余白",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = {
                                        bodySpacerDp = (bodySpacerDp + 1).coerceIn(0, 24)
                                    }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = {
                                        bodySpacerDp = (bodySpacerDp - 1).coerceIn(0, 24)
                                    }
                                ) {
                                    Text("▼")
                                }
                            }
                            Text(
                                text = "ContentH:${contentHeightDp.value.roundToInt()}dp",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val outerPaddingColor = if (outerBottomDp >= 0) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
        }
        val outerPaddingStroke = if (outerBottomDp >= 0) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = outerBottomDp.dp)
                .drawBehind {
                    val indicatorHeight = abs(outerBottomDp).dp.toPx().coerceAtMost(size.height)
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                val maxHeightDp = if (isImeVisible) 220 else 300
                val effectiveMinDp = cardMinHeightDp.coerceAtMost(maxHeightDp)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { contentHeightPx = it.height } // TEMP: dev measure content height
                        .heightIn(
                            min = effectiveMinDp.dp,
                            max = maxHeightDp.dp
                        )
                        // TEMP: allow preview card height to shrink to content (keep max cap)
                        // プレビューカード全体の余白を軽く圧縮して情報ブロックを上寄せ
                        .padding(horizontal = 12.dp, vertical = innerVPadDp.dp)
                ) {
                    val rawSpriteSize = minOf(maxWidth, maxHeight) * 0.30f
                    val spriteSize = if (isImeVisible) {
                        rawSpriteSize.coerceIn(56.dp, 96.dp)
                    } else {
                        rawSpriteSize.coerceIn(72.dp, 120.dp)
                    }

                    val innerPaddingColor = if (innerBottomDp >= 0) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
                    }
                    val innerPaddingStroke = if (innerBottomDp >= 0) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                val indicatorHeight = abs(innerBottomDp).dp.toPx().coerceAtMost(size.height)
                                if (indicatorHeight > 0f) {
                                    val top = size.height - indicatorHeight
                                    drawRect(
                                        color = innerPaddingColor,
                                        topLeft = Offset(x = 0f, y = top),
                                        size = Size(width = size.width, height = indicatorHeight)
                                    )
                                    drawLine(
                                        color = innerPaddingStroke,
                                        start = Offset(x = 0f, y = top),
                                        end = Offset(x = size.width, y = top),
                                        strokeWidth = 2f
                                    )
                                }
                            }
                            .padding(bottom = innerBottomDp.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "プレビュー",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.alpha(if (isImeVisible) 0.6f else 1f)
                            ) {
                                DetailsToggle(
                                    expanded = showDetails,
                                    onClick = { showDetails = !showDetails },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(headerSpacerDp.dp))
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = "現在: ${baseSummary.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(bodySpacerDp.dp))
                        ReadyAnimationPreview(
                            imageBitmap = imageBitmap,
                            spriteSheetConfig = spriteSheetConfig,
                            summary = baseSummary,
                            insertionSummary = insertionSummary,
                            insertionEnabled = insertionEnabled,
                            insertionPreviewValues = insertionPreviewValues,
                            spriteSizeDp = spriteSize,
                            showDetails = showDetails,
                            charYOffsetDp = charYOffsetDp,
                            infoYOffsetDp = infoYOffsetDp,
                            detailsMaxHeightDp = detailsMaxHeightDp,
                            detailsMaxLines = detailsMaxLines,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            // FIX: missing brace for ReadyAnimationPreviewPane
            }
        }
    }
}

@Composable
private fun DetailsToggle(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "詳細 ${if (expanded) "▴" else "▾"}",
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .wrapContentHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false),
                role = Role.Button,
                onClick = onClick
            )
    )
}

@Composable
private fun SpriteSettingsControls(
    selectedNumber: Int,
    selectedPosition: BoxPosition?,
    boxSizePx: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onMoveXNegative: () -> Unit,
    onMoveXPositive: () -> Unit,
    onMoveYNegative: () -> Unit,
    onMoveYPositive: () -> Unit,
    onSizeDecrease: () -> Unit,
    onSizeIncrease: () -> Unit,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrev) {
                    Text(text = "前へ")
                }
                Button(onClick = onNext) {
                    Text(text = "次へ")
                }
                Text(text = "選択中: $selectedNumber/9")
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onMoveXNegative) { Text("X-") }
                    IconButton(onClick = onMoveXPositive) { Text("X+") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onMoveYNegative) { Text("Y-") }
                    IconButton(onClick = onMoveYPositive) { Text("Y+") }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "サイズ: ${boxSizePx}px")
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onSizeDecrease) { Text("-") }
                IconButton(onClick = onSizeIncrease) { Text("+") }
            }
        }

        Text(
            text = selectedPosition?.let { position ->
                "座標: ${position.x},${position.y},${boxSizePx},${boxSizePx}"
            } ?: "座標: -, -, -, -"
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onCopy
        ) {
            Text(text = "コピー")
        }
    }
}

@Composable
private fun SpriteSettingsFooter(
    modifier: Modifier = Modifier,
    onUpdate: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = modifier
    ) {
        Divider()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = 52.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            tonalElevation = 4.dp,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = onUpdate,
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("更新")
                }
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = onSave,
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("保存")
                }
            }
        }
    }
}

private fun formatAppliedLine(
    summary: AnimationSummary,
    insertionPreviewValues: InsertionPreviewValues? = null
): String {
    if (insertionPreviewValues != null) {
        val framesText = insertionPreviewValues.framesText.ifBlank { "-" }
        val intervalText = insertionPreviewValues.intervalText.ifBlank { "-" }
        return "${summary.label}: $framesText / ${intervalText}ms"
    }
    val frames = summary.frames.ifEmpty { listOf(0) }.joinToString(",") { value -> (value + 1).toString() }
    return "${summary.label}: $frames / ${summary.intervalMs}ms"
}

private fun formatInsertionDetail(
    summary: AnimationSummary,
    previewValues: InsertionPreviewValues?
): String {
    if (previewValues == null) return formatAppliedLine(summary)
    val framesText = previewValues?.framesText?.ifBlank { "-" } ?: "-"
    val intervalText = previewValues?.intervalText?.ifBlank { "-" } ?: "-"
    val everyNText = previewValues?.everyNText?.ifBlank { "-" } ?: "-"
    val probabilityText = previewValues?.probabilityText?.ifBlank { "-" } ?: "-"
    val cooldownText = previewValues?.cooldownText?.ifBlank { "-" } ?: "-"
    val exclusiveText = previewValues?.exclusiveText ?: "-"

    val parts = buildList {
        add("${summary.label}: $framesText / ${intervalText}ms")
        add("N:$everyNText")
        add("P:${if (probabilityText == "-") "-" else "$probabilityText%"}")
        add("CD:$cooldownText")
        add("Excl:$exclusiveText")
    }
    // keep short to avoid card growth; joins with double space to mimic spec formatting
    return parts.joinToString(separator = "  ")
}
