package com.sonusid.ollama.ui.screens.settings

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
import com.sonusid.ollama.ui.components.toDstRect
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
        exclusive = exclusive
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
    var readyInsertionExclusive by rememberSaveable { mutableStateOf(false) }
    var appliedReadyInsertionFrames by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.frameSequence) }
    var appliedReadyInsertionIntervalMs by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.intervalMs) }
    var appliedReadyInsertionEveryNLoops by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.everyNLoops) }
    var appliedReadyInsertionProbabilityPercent by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.probabilityPercent) }
    var appliedReadyInsertionCooldownLoops by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.cooldownLoops) }
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
    var talkingInsertionExclusive by rememberSaveable { mutableStateOf(false) }
    var appliedTalkingInsertionFrames by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.frameSequence) }
    var appliedTalkingInsertionIntervalMs by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.intervalMs) }
    var appliedTalkingInsertionEveryNLoops by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.everyNLoops) }
    var appliedTalkingInsertionProbabilityPercent by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.probabilityPercent) }
    var appliedTalkingInsertionCooldownLoops by rememberSaveable { mutableStateOf(InsertionAnimationSettings.DEFAULT.cooldownLoops) }
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
        appliedReadyInsertionExclusive = readyInsertionAnimationSettings.exclusive
        readyInsertionFrameInput = normalizedFrames.joinToString(separator = ",") { value -> (value + 1).toString() }
        readyInsertionIntervalInput = readyInsertionAnimationSettings.intervalMs.toString()
        readyInsertionEveryNInput = readyInsertionAnimationSettings.everyNLoops.toString()
        readyInsertionProbabilityInput = readyInsertionAnimationSettings.probabilityPercent.toString()
        readyInsertionCooldownInput = readyInsertionAnimationSettings.cooldownLoops.toString()
        readyInsertionExclusive = readyInsertionAnimationSettings.exclusive
    }

    LaunchedEffect(talkingInsertionAnimationSettings) {
        val normalizedFrames = talkingInsertionAnimationSettings.frameSequence.ifEmpty { listOf(0) }
        appliedTalkingInsertionFrames = normalizedFrames
        appliedTalkingInsertionIntervalMs = talkingInsertionAnimationSettings.intervalMs
        appliedTalkingInsertionEveryNLoops = talkingInsertionAnimationSettings.everyNLoops
        appliedTalkingInsertionProbabilityPercent = talkingInsertionAnimationSettings.probabilityPercent
        appliedTalkingInsertionCooldownLoops = talkingInsertionAnimationSettings.cooldownLoops
        appliedTalkingInsertionExclusive = talkingInsertionAnimationSettings.exclusive
        talkingInsertionFrameInput = normalizedFrames.joinToString(separator = ",") { value -> (value + 1).toString() }
        talkingInsertionIntervalInput = talkingInsertionAnimationSettings.intervalMs.toString()
        talkingInsertionEveryNInput = talkingInsertionAnimationSettings.everyNLoops.toString()
        talkingInsertionProbabilityInput = talkingInsertionAnimationSettings.probabilityPercent.toString()
        talkingInsertionCooldownInput = talkingInsertionAnimationSettings.cooldownLoops.toString()
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
        if (parsed.size != parsed.distinct().size) {
            return ValidationResult(null, "重複しないように入力してください")
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
        val framesResult = parseFrameSequenceInput(frameInput, spriteSheetConfig.frameCount)
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
        val framesResult = parseFrameSequenceInput(frameInput, spriteSheetConfig.frameCount)
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

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }, bottomBar = {
        if (tabIndex == 0) {
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
                onSave = { saveSpriteSheetConfig() },
                onCopy = { copySpriteSheetConfig() }
            )
        }
    }) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
                                    readyInsertionExclusive,
                                    spriteSheetConfig.frameCount
                                ) {
                                    buildInsertionPreviewSummary(
                                        label = "挿入",
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
                                    talkingInsertionExclusive,
                                    spriteSheetConfig.frameCount
                                ) {
                                    buildInsertionPreviewSummary(
                                        label = "挿入",
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
                                ReadyAnimationTab(
                                    imageBitmap = imageBitmap,
                                    spriteSheetConfig = spriteSheetConfig,
                                    selectedAnimation = selectedAnimation,
                                    onSelectedAnimationChange = { selectedAnimation = it },
                                    animationOptions = animationOptions,
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
                                    insertionFrameInput = selectedInsertionFrameInput,
                                    onInsertionFrameInputChange = { updated ->
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
                                    insertionIntervalInput = selectedInsertionIntervalInput,
                                    onInsertionIntervalInputChange = { updated ->
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
                                    insertionEveryNInput = selectedInsertionEveryNInput,
                                    onInsertionEveryNInputChange = { updated ->
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
                                    insertionProbabilityInput = selectedInsertionProbabilityInput,
                                    onInsertionProbabilityInputChange = { updated ->
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
                                    insertionCooldownInput = selectedInsertionCooldownInput,
                                    onInsertionCooldownInputChange = { updated ->
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
                                    insertionExclusive = selectedInsertionExclusive,
                                    onInsertionExclusiveChange = { checked ->
                                        when (selectedAnimation) {
                                            AnimationType.READY -> readyInsertionExclusive = checked
                                            AnimationType.TALKING -> talkingInsertionExclusive = checked
                                        }
                                    },
                                    insertionFramesError = selectedInsertionFramesError,
                                    insertionIntervalError = selectedInsertionIntervalError,
                                    insertionEveryNError = selectedInsertionEveryNError,
                                    insertionProbabilityError = selectedInsertionProbabilityError,
                                    insertionCooldownError = selectedInsertionCooldownError,
                                    baseSummary = selectedBaseSummary,
                                    insertionSummary = selectedInsertionSummary,
                                    insertionPreviewValues = selectedInsertionPreviewValues,
                                    onApply = {
                                        val validatedBase = validateBaseInputs(selectedAnimation) ?: return@ReadyAnimationTab
                                        val validatedInsertion =
                                            validateInsertionInputs(selectedAnimation) ?: return@ReadyAnimationTab
                                        when (selectedAnimation) {
                                            AnimationType.READY -> {
                                                appliedReadyFrames = validatedBase.frameSequence
                                                appliedReadyIntervalMs = validatedBase.intervalMs
                                                appliedReadyInsertionFrames = validatedInsertion.frameSequence
                                                appliedReadyInsertionIntervalMs = validatedInsertion.intervalMs
                                                appliedReadyInsertionEveryNLoops = validatedInsertion.everyNLoops
                                                appliedReadyInsertionProbabilityPercent = validatedInsertion.probabilityPercent
                                                appliedReadyInsertionCooldownLoops = validatedInsertion.cooldownLoops
                                                appliedReadyInsertionExclusive = validatedInsertion.exclusive
                                            }

                                            AnimationType.TALKING -> {
                                                appliedTalkingFrames = validatedBase.frameSequence
                                                appliedTalkingIntervalMs = validatedBase.intervalMs
                                                appliedTalkingInsertionFrames = validatedInsertion.frameSequence
                                                appliedTalkingInsertionIntervalMs = validatedInsertion.intervalMs
                                                appliedTalkingInsertionEveryNLoops = validatedInsertion.everyNLoops
                                                appliedTalkingInsertionProbabilityPercent = validatedInsertion.probabilityPercent
                                                appliedTalkingInsertionCooldownLoops = validatedInsertion.cooldownLoops
                                                appliedTalkingInsertionExclusive = validatedInsertion.exclusive
                                            }
                                        }
                                        Log.d(
                                            "SpriteAnim",
                                            "Applied ${selectedAnimation.label} frames=${validatedBase.frameSequence.map { it + 1 }} intervalMs=${validatedBase.intervalMs} insertion=${validatedInsertion.frameSequence.map { it + 1 }} everyN=${validatedInsertion.everyNLoops} prob=${validatedInsertion.probabilityPercent}% cooldown=${validatedInsertion.cooldownLoops} exclusive=${validatedInsertion.exclusive}"
                                        )
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("プレビューに適用しました")
                                        }
                                    },
                                    onSave = {
                                        val validatedBase = validateBaseInputs(selectedAnimation) ?: return@ReadyAnimationTab
                                        val validatedInsertion =
                                            validateInsertionInputs(selectedAnimation) ?: return@ReadyAnimationTab
                                        when (selectedAnimation) {
                                            AnimationType.READY -> {
                                                appliedReadyFrames = validatedBase.frameSequence
                                                appliedReadyIntervalMs = validatedBase.intervalMs
                                                appliedReadyInsertionFrames = validatedInsertion.frameSequence
                                                appliedReadyInsertionIntervalMs = validatedInsertion.intervalMs
                                                appliedReadyInsertionEveryNLoops = validatedInsertion.everyNLoops
                                                appliedReadyInsertionProbabilityPercent = validatedInsertion.probabilityPercent
                                                appliedReadyInsertionCooldownLoops = validatedInsertion.cooldownLoops
                                                appliedReadyInsertionExclusive = validatedInsertion.exclusive
                                                coroutineScope.launch {
                                                    settingsPreferences.saveReadyAnimationSettings(validatedBase)
                                                    settingsPreferences.saveReadyInsertionAnimationSettings(validatedInsertion)
                                                    snackbarHostState.showSnackbar("Readyアニメを保存しました")
                                                }
                                            }

                                            AnimationType.TALKING -> {
                                                appliedTalkingFrames = validatedBase.frameSequence
                                                appliedTalkingIntervalMs = validatedBase.intervalMs
                                                appliedTalkingInsertionFrames = validatedInsertion.frameSequence
                                                appliedTalkingInsertionIntervalMs = validatedInsertion.intervalMs
                                                appliedTalkingInsertionEveryNLoops = validatedInsertion.everyNLoops
                                                appliedTalkingInsertionProbabilityPercent = validatedInsertion.probabilityPercent
                                                appliedTalkingInsertionCooldownLoops = validatedInsertion.cooldownLoops
                                                appliedTalkingInsertionExclusive = validatedInsertion.exclusive
                                                coroutineScope.launch {
                                                    settingsPreferences.saveTalkingAnimationSettings(validatedBase)
                                                    settingsPreferences.saveTalkingInsertionAnimationSettings(validatedInsertion)
                                                    snackbarHostState.showSnackbar("Talkingアニメを保存しました")
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun ReadyAnimationTab(
    imageBitmap: ImageBitmap,
    spriteSheetConfig: SpriteSheetConfig,
    selectedAnimation: AnimationType,
    onSelectedAnimationChange: (AnimationType) -> Unit,
    animationOptions: List<AnimationType>,
    frameInput: String,
    onFrameInputChange: (String) -> Unit,
    intervalInput: String,
    onIntervalInputChange: (String) -> Unit,
    framesError: String?,
    intervalError: String?,
    insertionFrameInput: String,
    onInsertionFrameInputChange: (String) -> Unit,
    insertionIntervalInput: String,
    onInsertionIntervalInputChange: (String) -> Unit,
    insertionEveryNInput: String,
    onInsertionEveryNInputChange: (String) -> Unit,
    insertionProbabilityInput: String,
    onInsertionProbabilityInputChange: (String) -> Unit,
    insertionCooldownInput: String,
    onInsertionCooldownInputChange: (String) -> Unit,
    insertionExclusive: Boolean,
    onInsertionExclusiveChange: (Boolean) -> Unit,
    insertionFramesError: String?,
    insertionIntervalError: String?,
    insertionEveryNError: String?,
    insertionProbabilityError: String?,
    insertionCooldownError: String?,
    baseSummary: AnimationSummary,
    insertionSummary: AnimationSummary,
    insertionPreviewValues: InsertionPreviewValues,
    onApply: () -> Unit,
    onSave: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val scrollToSettings: () -> Unit = {
        coroutineScope.launch { lazyListState.animateScrollToItem(1) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        state = lazyListState,
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ReadyAnimationPreviewPane(
                imageBitmap = imageBitmap,
                spriteSheetConfig = spriteSheetConfig,
                baseSummary = baseSummary,
                insertionSummary = insertionSummary,
                insertionPreviewValues = insertionPreviewValues,
                onApply = onApply,
                onSave = onSave,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            ReadyAnimationSettingsPane(
                animationOptions = animationOptions,
                selectedAnimation = selectedAnimation,
                onSelectedAnimationChange = onSelectedAnimationChange,
                frameInput = frameInput,
                onFrameInputChange = onFrameInputChange,
                intervalInput = intervalInput,
                onIntervalInputChange = onIntervalInputChange,
                framesError = framesError,
                intervalError = intervalError,
                insertionFrameInput = insertionFrameInput,
                onInsertionFrameInputChange = onInsertionFrameInputChange,
                insertionIntervalInput = insertionIntervalInput,
                onInsertionIntervalInputChange = onInsertionIntervalInputChange,
                insertionEveryNInput = insertionEveryNInput,
                onInsertionEveryNInputChange = onInsertionEveryNInputChange,
                insertionProbabilityInput = insertionProbabilityInput,
                onInsertionProbabilityInputChange = onInsertionProbabilityInputChange,
                insertionCooldownInput = insertionCooldownInput,
                onInsertionCooldownInputChange = onInsertionCooldownInputChange,
                insertionExclusive = insertionExclusive,
                onInsertionExclusiveChange = onInsertionExclusiveChange,
                insertionFramesError = insertionFramesError,
                insertionIntervalError = insertionIntervalError,
                insertionEveryNError = insertionEveryNError,
                insertionProbabilityError = insertionProbabilityError,
                insertionCooldownError = insertionCooldownError,
                onFieldFocused = scrollToSettings,
                modifier = Modifier.fillMaxWidth()
            )
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
private fun ReadyForm(
    readyFrameInput: String,
    onReadyFrameInputChange: (String) -> Unit,
    readyIntervalInput: String,
    onReadyIntervalInputChange: (String) -> Unit,
    readyFramesError: String?,
    readyIntervalError: String?,
    onFieldFocused: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = readyFrameInput,
            onValueChange = onReadyFrameInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { event ->
                    if (event.isFocused) onFieldFocused()
                },
            label = { Text("フレーム列 (例: 1,2,3)") },
            singleLine = true,
            isError = readyFramesError != null,
            supportingText = readyFramesError?.let { errorText ->
                { Text(errorText, color = Color.Red) }
            }
        )
        OutlinedTextField(
            value = readyIntervalInput,
            onValueChange = onReadyIntervalInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { event ->
                    if (event.isFocused) onFieldFocused()
                },
            label = { Text("周期 (ms)") },
            singleLine = true,
            isError = readyIntervalError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = readyIntervalError?.let { errorText ->
                { Text(errorText, color = Color.Red) }
            }
        )
    }
}

@Composable
private fun InsertionForm(
    insertionFrameInput: String,
    onInsertionFrameInputChange: (String) -> Unit,
    insertionIntervalInput: String,
    onInsertionIntervalInputChange: (String) -> Unit,
    insertionEveryNInput: String,
    onInsertionEveryNInputChange: (String) -> Unit,
    insertionProbabilityInput: String,
    onInsertionProbabilityInputChange: (String) -> Unit,
    insertionCooldownInput: String,
    onInsertionCooldownInputChange: (String) -> Unit,
    insertionExclusive: Boolean,
    onInsertionExclusiveChange: (Boolean) -> Unit,
    insertionFramesError: String?,
    insertionIntervalError: String?,
    insertionEveryNError: String?,
    insertionProbabilityError: String?,
    insertionCooldownError: String?,
    onFieldFocused: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = insertionFrameInput,
            onValueChange = onInsertionFrameInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { event ->
                    if (event.isFocused) onFieldFocused()
                },
            label = { Text("挿入フレーム列（例: 4,5,6）") },
            singleLine = true,
            isError = insertionFramesError != null,
            supportingText = insertionFramesError?.let { errorText ->
                { Text(errorText, color = Color.Red) }
            }
        )
        OutlinedTextField(
            value = insertionIntervalInput,
            onValueChange = onInsertionIntervalInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { event ->
                    if (event.isFocused) onFieldFocused()
                },
            label = { Text("挿入周期（ms）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = insertionIntervalError != null,
            supportingText = insertionIntervalError?.let { errorText ->
                { Text(errorText, color = Color.Red) }
            }
        )
        OutlinedTextField(
            value = insertionEveryNInput,
            onValueChange = onInsertionEveryNInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { event ->
                    if (event.isFocused) onFieldFocused()
                },
            label = { Text("毎 N ループ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = insertionEveryNError != null,
            supportingText = insertionEveryNError?.let { errorText ->
                { Text(errorText, color = Color.Red) }
            }
        )
        OutlinedTextField(
            value = insertionProbabilityInput,
            onValueChange = onInsertionProbabilityInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { event ->
                    if (event.isFocused) onFieldFocused()
                },
            label = { Text("確率（%）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = insertionProbabilityError != null,
            supportingText = insertionProbabilityError?.let { errorText ->
                { Text(errorText, color = Color.Red) }
            }
        )
        OutlinedTextField(
            value = insertionCooldownInput,
            onValueChange = onInsertionCooldownInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { event ->
                    if (event.isFocused) onFieldFocused()
                },
            label = { Text("クールダウン（ループ）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = insertionCooldownError != null,
            supportingText = insertionCooldownError?.let { errorText ->
                { Text(errorText, color = Color.Red) }
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                checked = insertionExclusive,
                onCheckedChange = onInsertionExclusiveChange
            )
        }
    }
}

@Composable
private fun ReadyAnimationPreview(
    imageBitmap: ImageBitmap,
    spriteSheetConfig: SpriteSheetConfig,
    summary: AnimationSummary,
    insertionSummary: AnimationSummary,
    insertionPreviewValues: InsertionPreviewValues,
    spriteSizeDp: Dp,
    showDetails: Boolean,
    modifier: Modifier = Modifier,
) {
    val resolvedFrames = summary.frames.ifEmpty { listOf(0) }
    var currentFrameIndex by remember(resolvedFrames) { mutableStateOf(0) }

    LaunchedEffect(resolvedFrames, summary.intervalMs) {
        if (resolvedFrames.isEmpty()) return@LaunchedEffect
        currentFrameIndex = 0
        while (isActive && resolvedFrames.isNotEmpty()) {
            delay(summary.intervalMs.toLong().coerceAtLeast(1L))
            currentFrameIndex = (currentFrameIndex + 1) % resolvedFrames.size
        }
    }

    val boxByFrameIndex = remember(spriteSheetConfig) {
        spriteSheetConfig.boxes.associateBy { box ->
            spriteSheetConfig.toInternalFrameIndex(box.frameIndex) ?: box.frameIndex
        }
    }
    val currentFrame = resolvedFrames.getOrNull(currentFrameIndex)?.coerceIn(0, spriteSheetConfig.frameCount - 1)
    val currentBox = currentFrame?.let { frameIndex -> boxByFrameIndex[frameIndex] }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(spriteSizeDp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (currentBox == null) return@Canvas
                    val region = SpriteFrameRegion(
                        srcOffset = IntOffset(currentBox.x, currentBox.y),
                        srcSize = IntSize(currentBox.width, currentBox.height),
                    )
                    val (dstSizeInt, dstOffsetInt) = ContentScale.Fit.toDstRect(
                        srcSize = region.srcSize,
                        canvasSize = this.size
                    )
                    drawFrameRegion(
                        sheet = imageBitmap,
                        region = region,
                        dstSize = dstSizeInt,
                        dstOffset = dstOffsetInt,
                        placeholder = { offset, dstSize -> drawFramePlaceholder(offset, dstSize) },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "フレーム: ${(currentFrame ?: 0) + 1} / ${spriteSheetConfig.frameCount}  周期: ${summary.intervalMs}ms",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatAppliedLine(
                        summary = summary,
                        insertionPreviewValues = null
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedVisibility(visible = showDetails) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formatAppliedLine(summary),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatInsertionDetail(
                                summary = insertionSummary,
                                previewValues = insertionPreviewValues
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyAnimationSettingsPane(
    animationOptions: List<AnimationType>,
    selectedAnimation: AnimationType,
    onSelectedAnimationChange: (AnimationType) -> Unit,
    frameInput: String,
    onFrameInputChange: (String) -> Unit,
    intervalInput: String,
    onIntervalInputChange: (String) -> Unit,
    framesError: String?,
    intervalError: String?,
    insertionFrameInput: String,
    onInsertionFrameInputChange: (String) -> Unit,
    insertionIntervalInput: String,
    onInsertionIntervalInputChange: (String) -> Unit,
    insertionEveryNInput: String,
    onInsertionEveryNInputChange: (String) -> Unit,
    insertionProbabilityInput: String,
    onInsertionProbabilityInputChange: (String) -> Unit,
    insertionCooldownInput: String,
    onInsertionCooldownInputChange: (String) -> Unit,
    insertionExclusive: Boolean,
    onInsertionExclusiveChange: (Boolean) -> Unit,
    insertionFramesError: String?,
    insertionIntervalError: String?,
    insertionEveryNError: String?,
    insertionProbabilityError: String?,
    insertionCooldownError: String?,
    onFieldFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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
            items = animationOptions,
            selectedItem = selectedAnimation,
            onSelectedItemChange = onSelectedAnimationChange,
            modifier = Modifier.fillMaxWidth()
        )
        ReadyForm(
            readyFrameInput = frameInput,
            onReadyFrameInputChange = onFrameInputChange,
            readyIntervalInput = intervalInput,
            onReadyIntervalInputChange = onIntervalInputChange,
            readyFramesError = framesError,
            readyIntervalError = intervalError,
            onFieldFocused = onFieldFocused
        )
        Text(
            text = "挿入設定",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        InsertionForm(
            insertionFrameInput = insertionFrameInput,
            onInsertionFrameInputChange = onInsertionFrameInputChange,
            insertionIntervalInput = insertionIntervalInput,
            onInsertionIntervalInputChange = onInsertionIntervalInputChange,
            insertionEveryNInput = insertionEveryNInput,
            onInsertionEveryNInputChange = onInsertionEveryNInputChange,
            insertionProbabilityInput = insertionProbabilityInput,
            onInsertionProbabilityInputChange = onInsertionProbabilityInputChange,
            insertionCooldownInput = insertionCooldownInput,
            onInsertionCooldownInputChange = onInsertionCooldownInputChange,
            insertionExclusive = insertionExclusive,
            onInsertionExclusiveChange = onInsertionExclusiveChange,
            insertionFramesError = insertionFramesError,
            insertionIntervalError = insertionIntervalError,
            insertionEveryNError = insertionEveryNError,
            insertionProbabilityError = insertionProbabilityError,
            insertionCooldownError = insertionCooldownError,
            onFieldFocused = onFieldFocused
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun ReadyAnimationPreviewPane(
    imageBitmap: ImageBitmap,
    spriteSheetConfig: SpriteSheetConfig,
    baseSummary: AnimationSummary,
    insertionSummary: AnimationSummary,
    insertionPreviewValues: InsertionPreviewValues,
    onApply: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier.animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            val rawSpriteSize = minOf(maxWidth, maxHeight) * 0.30f
            val spriteSize = rawSpriteSize.coerceIn(72.dp, 120.dp)
            val stackButtons = maxWidth < 260.dp

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    TextButton(onClick = { showDetails = !showDetails }) {
                        val suffix = if (showDetails) "▴" else "▾"
                        Text(text = "詳細 $suffix")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "現在: ${baseSummary.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "挿入: ${formatAppliedLine(insertionSummary, insertionPreviewValues)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ReadyAnimationPreview(
                    imageBitmap = imageBitmap,
                    spriteSheetConfig = spriteSheetConfig,
                    summary = baseSummary,
                    insertionSummary = insertionSummary,
                    insertionPreviewValues = insertionPreviewValues,
                    spriteSizeDp = spriteSize,
                    showDetails = showDetails,
                    modifier = Modifier.fillMaxWidth()
                )
                if (stackButtons) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            onClick = onApply,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) { Text("更新") }
                        FilledTonalButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            onClick = onSave,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) { Text("保存") }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            onClick = onApply,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) { Text("更新") }
                        FilledTonalButton(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            onClick = onSave,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) { Text("保存") }
                    }
                }
            }
        }
    }
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
    onSave: () -> Unit,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = onSave
            ) {
                Text(text = "保存")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = onCopy
            ) {
                Text(text = "コピー")
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
