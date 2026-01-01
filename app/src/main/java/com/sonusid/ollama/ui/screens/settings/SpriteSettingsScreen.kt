package com.sonusid.ollama.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipboardManager
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
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

private data class DevPreviewSettings(
    val cardMaxHeightDp: Int,
    val innerBottomDp: Int,
    val outerBottomDp: Int,
    val innerVPadDp: Int,
    val charYOffsetDp: Int,
    val infoXOffsetDp: Int,
    val infoYOffsetDp: Int,
    val headerOffsetLimitDp: Int,
    val headerLeftXOffsetDp: Int,
    val headerLeftYOffsetDp: Int,
    val headerRightXOffsetDp: Int,
    val headerRightYOffsetDp: Int,
    val cardMinHeightDp: Int,
    val detailsMaxHeightDp: Int,
    val detailsMaxLines: Int,
    val headerSpacerDp: Int,
    val bodySpacerDp: Int,
)

private object DevDefaults {
    const val cardMaxHeightDp = 130
    const val innerBottomDp = 0
    const val outerBottomDp = 0
    const val innerVPadDp = 8
    const val charYOffsetDp = -32
    const val infoXOffsetDp = 0
    const val infoYOffsetDp = 0
    const val headerOffsetLimitDp = 150
    const val headerLeftXOffsetDp = 114
    const val headerLeftYOffsetDp = 1
    const val headerRightXOffsetDp = 0
    const val headerRightYOffsetDp = 0
    const val cardMinHeightDp = 156
    const val detailsMaxHeightDp = 40
    const val detailsMaxLines = 2
    const val headerSpacerDp = 0
    const val bodySpacerDp = 0

    fun toDevPreviewSettings(): DevPreviewSettings =
        DevPreviewSettings(
            cardMaxHeightDp = cardMaxHeightDp,
            innerBottomDp = innerBottomDp,
            outerBottomDp = outerBottomDp,
            innerVPadDp = innerVPadDp,
            charYOffsetDp = charYOffsetDp,
            infoXOffsetDp = infoXOffsetDp,
            infoYOffsetDp = infoYOffsetDp,
            headerOffsetLimitDp = headerOffsetLimitDp,
            headerLeftXOffsetDp = headerLeftXOffsetDp,
            headerLeftYOffsetDp = headerLeftYOffsetDp,
            headerRightXOffsetDp = headerRightXOffsetDp,
            headerRightYOffsetDp = headerRightYOffsetDp,
            cardMinHeightDp = cardMinHeightDp,
            detailsMaxHeightDp = detailsMaxHeightDp,
            detailsMaxLines = detailsMaxLines,
            headerSpacerDp = headerSpacerDp,
            bodySpacerDp = bodySpacerDp,
        )
}

private data class DevSettingsDefaults(
    val cardMaxHeightDp: Int?,
    val innerBottomDp: Int?,
    val outerBottomDp: Int?,
    val innerVPadDp: Int?,
    val charYOffsetDp: Int?,
    val infoXOffsetDp: Int?,
    val infoYOffsetDp: Int?,
    val headerOffsetLimitDp: Int?,
    val headerLeftXOffsetDp: Int?,
    val headerLeftYOffsetDp: Int?,
    val headerRightXOffsetDp: Int?,
    val headerRightYOffsetDp: Int?,
    val cardMinHeightDp: Int?,
    val detailsMaxHeightDp: Int?,
    val detailsMaxLines: Int?,
    val headerSpacerDp: Int?,
    val bodySpacerDp: Int?,
) {
    companion object {
        fun fromJson(json: String?): DevSettingsDefaults? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val dev = JSONObject(json).optJSONObject("dev") ?: return null
                DevSettingsDefaults(
                    cardMaxHeightDp = dev.optIntOrNull("cardMaxHeightDp"),
                    innerBottomDp = dev.optIntOrNull("innerBottomDp"),
                    outerBottomDp = dev.optIntOrNull("outerBottomDp"),
                    innerVPadDp = dev.optIntOrNull("innerVPadDp"),
                    charYOffsetDp = dev.optIntOrNull("charYOffsetDp"),
                    infoXOffsetDp = dev.optIntOrNull("infoXOffsetDp"),
                    infoYOffsetDp = dev.optIntOrNull("infoYOffsetDp"),
                    headerOffsetLimitDp = dev.optIntOrNull("headerOffsetLimitDp"),
                    headerLeftXOffsetDp = dev.optIntOrNull("headerLeftXOffsetDp"),
                    headerLeftYOffsetDp = dev.optIntOrNull("headerLeftYOffsetDp"),
                    headerRightXOffsetDp = dev.optIntOrNull("headerRightXOffsetDp"),
                    headerRightYOffsetDp = dev.optIntOrNull("headerRightYOffsetDp"),
                    cardMinHeightDp = dev.optIntOrNull("cardMinHeightDp") ?: dev.optIntOrNull("minHeightDp"),
                    detailsMaxHeightDp = dev.optIntOrNull("detailsMaxHeightDp") ?: dev.optIntOrNull("detailsMaxH"),
                    detailsMaxLines = dev.optIntOrNull("detailsMaxLines") ?: dev.optIntOrNull("detailsLines"),
                    headerSpacerDp = dev.optIntOrNull("headerSpacerDp"),
                    bodySpacerDp = dev.optIntOrNull("bodySpacerDp"),
                )
            }.getOrNull()
        }
    }
}

private fun DevSettingsDefaults.toDevPreviewSettings(): DevPreviewSettings =
    DevPreviewSettings(
        cardMaxHeightDp = cardMaxHeightDp ?: DevDefaults.cardMaxHeightDp,
        innerBottomDp = innerBottomDp ?: DevDefaults.innerBottomDp,
        outerBottomDp = outerBottomDp ?: DevDefaults.outerBottomDp,
        innerVPadDp = innerVPadDp ?: DevDefaults.innerVPadDp,
        charYOffsetDp = charYOffsetDp ?: DevDefaults.charYOffsetDp,
        infoXOffsetDp = (infoXOffsetDp ?: DevDefaults.infoXOffsetDp).coerceIn(INFO_X_OFFSET_MIN, INFO_X_OFFSET_MAX),
        infoYOffsetDp = infoYOffsetDp ?: DevDefaults.infoYOffsetDp,
        headerOffsetLimitDp = headerOffsetLimitDp ?: DevDefaults.headerOffsetLimitDp,
        headerLeftXOffsetDp = headerLeftXOffsetDp ?: DevDefaults.headerLeftXOffsetDp,
        headerLeftYOffsetDp = headerLeftYOffsetDp ?: DevDefaults.headerLeftYOffsetDp,
        headerRightXOffsetDp = headerRightXOffsetDp ?: DevDefaults.headerRightXOffsetDp,
        headerRightYOffsetDp = headerRightYOffsetDp ?: DevDefaults.headerRightYOffsetDp,
        cardMinHeightDp = cardMinHeightDp ?: DevDefaults.cardMinHeightDp,
        detailsMaxHeightDp = detailsMaxHeightDp ?: DevDefaults.detailsMaxHeightDp,
        detailsMaxLines = detailsMaxLines ?: DevDefaults.detailsMaxLines,
        headerSpacerDp = headerSpacerDp ?: DevDefaults.headerSpacerDp,
        bodySpacerDp = bodySpacerDp ?: DevDefaults.bodySpacerDp,
    )

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

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
private const val INFO_X_OFFSET_MIN = -500
private const val INFO_X_OFFSET_MAX = 500

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

private fun devPreviewSettingsSaver() = androidx.compose.runtime.saveable.listSaver<DevPreviewSettings, Int>(
    save = { settings ->
        listOf(
            settings.cardMaxHeightDp,
            settings.innerBottomDp,
            settings.outerBottomDp,
            settings.innerVPadDp,
            settings.charYOffsetDp,
            settings.infoYOffsetDp,
            settings.headerOffsetLimitDp,
            settings.headerLeftXOffsetDp,
            settings.headerLeftYOffsetDp,
            settings.headerRightXOffsetDp,
            settings.headerRightYOffsetDp,
            settings.cardMinHeightDp,
            settings.detailsMaxHeightDp,
            settings.detailsMaxLines,
            settings.headerSpacerDp,
            settings.bodySpacerDp,
            settings.infoXOffsetDp,
        )
    },
    restore = { values ->
        if (values.size < 16) {
            DevDefaults.toDevPreviewSettings()
        } else {
            DevPreviewSettings(
                cardMaxHeightDp = values[0],
                innerBottomDp = values[1],
                outerBottomDp = values[2],
                innerVPadDp = values[3],
                charYOffsetDp = values[4],
                infoYOffsetDp = values[5],
                infoXOffsetDp = values.getOrNull(16)?.coerceIn(INFO_X_OFFSET_MIN, INFO_X_OFFSET_MAX) ?: DevDefaults.infoXOffsetDp,
                headerOffsetLimitDp = values[6],
                headerLeftXOffsetDp = values[7],
                headerLeftYOffsetDp = values[8],
                headerRightXOffsetDp = values[9],
                headerRightYOffsetDp = values[10],
                cardMinHeightDp = values[11],
                detailsMaxHeightDp = values[12],
                detailsMaxLines = values[13],
                headerSpacerDp = values[14],
                bodySpacerDp = values[15],
            )
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
    val defaultSpriteSheetConfig = remember { SpriteSheetConfig.default3x3() }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val settingsPreferences = remember(context.applicationContext) {
        SettingsPreferences(context.applicationContext)
    }
    val spriteSheetConfigJson by settingsPreferences.spriteSheetConfigJson.collectAsState(initial = null)
    val spriteSheetConfig by settingsPreferences.spriteSheetConfig.collectAsState(initial = defaultSpriteSheetConfig)
    val devFromJson = remember(spriteSheetConfigJson) {
        DevSettingsDefaults.fromJson(spriteSheetConfigJson)
    }
    val devPreviewDefaults = remember(devFromJson) {
        devFromJson?.toDevPreviewSettings() ?: DevDefaults.toDevPreviewSettings()
    }
    var devPreviewSettings by rememberSaveable(stateSaver = devPreviewSettingsSaver()) {
        mutableStateOf(devPreviewDefaults)
    }
    val initialHeaderLeftXOffsetDp = devFromJson?.headerLeftXOffsetDp
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
    var appliedReadyInsertionFrames by rememberSaveable { mutableStateOf(listOf(3, 4, 5)) }
    var appliedReadyInsertionIntervalMs by rememberSaveable { mutableStateOf(200) }
    var appliedReadyInsertionEveryNLoops by rememberSaveable { mutableStateOf(1) }
    var appliedReadyInsertionProbabilityPercent by rememberSaveable { mutableStateOf(50) }
    var appliedReadyInsertionCooldownLoops by rememberSaveable { mutableStateOf(0) }
    var appliedReadyInsertionEnabled by rememberSaveable { mutableStateOf(false) }
    var appliedReadyInsertionExclusive by rememberSaveable { mutableStateOf(false) }
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
    var appliedTalkingInsertionFrames by rememberSaveable { mutableStateOf(listOf(3, 4, 5)) }
    var appliedTalkingInsertionIntervalMs by rememberSaveable { mutableStateOf(200) }
    var appliedTalkingInsertionEveryNLoops by rememberSaveable { mutableStateOf(1) }
    var appliedTalkingInsertionProbabilityPercent by rememberSaveable { mutableStateOf(50) }
    var appliedTalkingInsertionCooldownLoops by rememberSaveable { mutableStateOf(0) }
    var appliedTalkingInsertionEnabled by rememberSaveable { mutableStateOf(false) }
    var appliedTalkingInsertionExclusive by rememberSaveable { mutableStateOf(false) }
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
            ?: defaultSpriteSheetConfig

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
        readyFrameInput = normalizedFrames
            .map { value -> value + 1 }
            .joinToString(separator = ",")
        readyIntervalInput = readyAnimationSettings.intervalMs.toString()
    }

    LaunchedEffect(talkingAnimationSettings) {
        val normalizedFrames = talkingAnimationSettings.frameSequence.ifEmpty { listOf(0) }
        appliedTalkingFrames = normalizedFrames
        appliedTalkingIntervalMs = talkingAnimationSettings.intervalMs
        talkingFrameInput = normalizedFrames
            .map { value -> value + 1 }
            .joinToString(separator = ",")
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
        readyInsertionFrameInput = normalizedFrames
            .map { value -> value + 1 }
            .joinToString(separator = ",")
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
        talkingInsertionFrameInput = normalizedFrames
            .map { value -> value + 1 }
            .joinToString(separator = ",")
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
        return SpriteSheetConfig(
            rows = defaultSpriteSheetConfig.rows,
            cols = defaultSpriteSheetConfig.cols,
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
            },
            insertionEnabled = defaultSpriteSheetConfig.insertionEnabled,
        )
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
        if (normalized.isEmpty()) {
            return ValidationResult(null, "1〜${maxFrameIndex} のカンマ区切りで入力してください")
        }
        val parsed = normalized.mapNotNull { token -> token.toIntOrNull() }
        if (parsed.size != normalized.size) {
            return ValidationResult(null, "数値で入力してください")
        }
        if (parsed.any { value -> value !in 1..maxFrameIndex }) {
            return ValidationResult(null, "1〜${maxFrameIndex}の範囲で入力してください")
        }
        val converted = parsed.map { value -> value - 1 }
        if (!allowDuplicates && converted.size != converted.distinct().size) {
            return ValidationResult(null, duplicateErrorMessage)
        }
        return ValidationResult(converted, null)
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

    fun copyAppliedSettings(devSettings: DevPreviewSettings) {
        coroutineScope.launch {
            runCatching {
                val config = buildSpriteSheetConfig()
                val error = config.validate()
                if (error != null) {
                    throw IllegalArgumentException(error)
                }
                val readyBase = ReadyAnimationSettings(
                    frameSequence = appliedReadyFrames,
                    intervalMs = appliedReadyIntervalMs
                )
                val talkingBase = ReadyAnimationSettings(
                    frameSequence = appliedTalkingFrames,
                    intervalMs = appliedTalkingIntervalMs
                )
                val readyInsertion = InsertionAnimationSettings(
                    enabled = appliedReadyInsertionEnabled,
                    frameSequence = appliedReadyInsertionFrames,
                    intervalMs = appliedReadyInsertionIntervalMs,
                    everyNLoops = appliedReadyInsertionEveryNLoops,
                    probabilityPercent = appliedReadyInsertionProbabilityPercent,
                    cooldownLoops = appliedReadyInsertionCooldownLoops,
                    exclusive = appliedReadyInsertionExclusive,
                )
                val talkingInsertion = InsertionAnimationSettings(
                    enabled = appliedTalkingInsertionEnabled,
                    frameSequence = appliedTalkingInsertionFrames,
                    intervalMs = appliedTalkingInsertionIntervalMs,
                    everyNLoops = appliedTalkingInsertionEveryNLoops,
                    probabilityPercent = appliedTalkingInsertionProbabilityPercent,
                    cooldownLoops = appliedTalkingInsertionCooldownLoops,
                    exclusive = appliedTalkingInsertionExclusive,
                )

                val jsonString = buildSettingsJson(
                    animationType = selectedAnimation,
                    spriteSheetConfig = config,
                    readyBase = readyBase,
                    talkingBase = talkingBase,
                    readyInsertion = readyInsertion,
                    talkingInsertion = talkingInsertion,
                    devSettings = devSettings,
                )
                clipboardManager.setText(AnnotatedString(jsonString))
            }.onSuccess {
                snackbarHostState.showSnackbar("設定JSONをコピーしました")
            }.onFailure { throwable ->
                snackbarHostState.showSnackbar("コピーに失敗しました: ${throwable.message}")
            }
        }
    }

    fun copyDevSettings(devSettings: DevPreviewSettings) {
        coroutineScope.launch {
            runCatching {
                val jsonString = buildDevJson(devSettings)
                clipboardManager.setText(AnnotatedString(jsonString))
            }.onSuccess {
                snackbarHostState.showSnackbar("JSONをコピーしました")
            }.onFailure { throwable ->
                snackbarHostState.showSnackbar("コピーに失敗しました: ${throwable.message}")
            }
        }
    }

    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

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

        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.back),
                                contentDescription = "戻る"
                            )
                        }
                        Text(
                            text = "Sprite Settings",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.size(32.dp))
                    }
                    TabRow(
                        selectedTabIndex = tabIndex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[tabIndex])
                                    .padding(horizontal = 6.dp),
                                height = 2.dp
                            )
                        },
                        divider = { Divider(thickness = 0.5.dp) }
                    ) {
                        Tab(
                            selected = tabIndex == 0,
                            onClick = { tabIndex = 0 },
                            text = {
                                Text(
                                    text = "調整",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Tab(
                            selected = tabIndex == 1,
                            onClick = { tabIndex = 1 },
                            text = {
                                Text(
                                    text = "アニメ",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Tab(
                            selected = tabIndex == 2,
                            onClick = { tabIndex = 2 },
                            text = {
                                Text(
                                    text = "DEV",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val actionButtonHeight = 28.dp // 上部操作ボタンも下部と同じ厚みに統一
                    val actionButtonModifier = Modifier
                        .weight(1f)
                        .height(actionButtonHeight)
                    val actionButtonPadding = PaddingValues(
                        horizontal = 12.dp,
                        vertical = 0.dp
                    ) // 内部余白を最小化して厚みを揃える
                    val actionButtonShape = RoundedCornerShape(999.dp)
                    val controlButtonHeight = 28.dp // 下部操作ボタンをコンパクト化
                    val controlButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            modifier = actionButtonModifier,
                            onClick = {
                                when (tabIndex) {
                                    0 -> coroutineScope.launch { snackbarHostState.showSnackbar("プレビューに適用しました") }
                                    1 -> onAnimationApply()
                                    else -> coroutineScope.launch { snackbarHostState.showSnackbar("DEVプレビューを更新しました") }
                                }
                            },
                            contentPadding = actionButtonPadding,
                            shape = actionButtonShape
                        ) {
                            Text(
                                text = "更新",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        FilledTonalButton(
                            modifier = actionButtonModifier,
                            onClick = {
                                when (tabIndex) {
                                    0 -> saveSpriteSheetConfig()
                                    1 -> onAnimationSave()
                                    else -> coroutineScope.launch { snackbarHostState.showSnackbar("DEV設定の保存は未対応です") }
                                }
                            },
                            contentPadding = actionButtonPadding,
                            shape = actionButtonShape
                        ) {
                            Text(
                                text = "保存",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        FilledTonalButton(
                            modifier = actionButtonModifier,
                            onClick = {
                                when (tabIndex) {
                                    0 -> copySpriteSheetConfig()
                                    1 -> copyAppliedSettings(devPreviewSettings)
                                    else -> copyDevSettings(devPreviewSettings)
                                }
                            },
                            contentPadding = actionButtonPadding,
                            shape = actionButtonShape
                        ) {
                            Text(
                                text = "コピー",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                    ) {
                            when (tabIndex) {
                                0 -> {
                                    val previewHeaderText = "${imageBitmap.width}×${imageBitmap.height} / ${"%.2f".format(displayScale)}x"
                                    val coordinateText =
                                        selectedPosition?.let { position ->
                                            "座標: ${position.x},${position.y},${boxSizePx},${boxSizePx}"
                                        } ?: "座標: -, -, -, -"
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState()),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        SpritePreviewBlock(
                                            imageBitmap = imageBitmap,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp),
                                            line1Text = previewHeaderText,
                                            line2Text = "選択中: ${selectedNumber}/9 | サイズ: ${boxSizePx}px | $coordinateText",
                                            onContainerSizeChanged = { newContainerSize: IntSize ->
                                                containerSize = newContainerSize
                                                if (imageBitmap.width != 0) {
                                                    displayScale = newContainerSize.width / imageBitmap.width.toFloat()
                                                }
                                            },
                                            overlayContent = {
                                                if (selectedPosition != null && containerSize.width > 0 && containerSize.height > 0) {
                                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                                        val scaleX = this.size.width / imageBitmap.width
                                                        val scaleY = this.size.height / imageBitmap.height
                                                        val scale = min(scaleX, scaleY)
                                                        val destinationWidth = imageBitmap.width * scale
                                                        val destinationHeight = imageBitmap.height * scale
                                                        val offsetX = (this.size.width - destinationWidth) / 2f
                                                        val offsetY = (this.size.height - destinationHeight) / 2f
                                                        drawRect(
                                                            color = Color.Red,
                                                            topLeft = Offset(
                                                                x = offsetX + selectedPosition.x * scale,
                                                                y = offsetY + selectedPosition.y * scale
                                                            ),
                                                            size = Size(
                                                                width = boxSizePx * scale,
                                                                height = boxSizePx * scale
                                                            ),
                                                            style = Stroke(width = 2.dp.toPx())
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        SpriteSettingsControls(
                                            buttonHeight = controlButtonHeight,
                                            buttonContentPadding = controlButtonPadding,
                                            buttonShape = actionButtonShape,
                                            onPrev = { selectedNumber = if (selectedNumber <= 1) 9 else selectedNumber - 1 },
                                            onNext = { selectedNumber = if (selectedNumber >= 9) 1 else selectedNumber + 1 },
                                            onMoveXNegative = { updateSelectedPosition(deltaX = -1, deltaY = 0) },
                                            onMoveXPositive = { updateSelectedPosition(deltaX = 1, deltaY = 0) },
                                            onMoveYNegative = { updateSelectedPosition(deltaX = 0, deltaY = -1) },
                                            onMoveYPositive = { updateSelectedPosition(deltaX = 0, deltaY = 1) },
                                            onSizeDecrease = { updateBoxSize(-4) },
                                            onSizeIncrease = { updateBoxSize(4) }
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
                                        devSettings = devPreviewSettings,
                                        onDevSettingsChange = { updated -> devPreviewSettings = updated },
                                        initialHeaderLeftXOffsetDp = initialHeaderLeftXOffsetDp
                                    )
                                }

                                2 -> {
                                    val previewHeaderText = "${imageBitmap.width}×${imageBitmap.height} / ${"%.2f".format(displayScale)}x"
                                    val coordinateText =
                                        selectedPosition?.let { position ->
                                            "座標: ${position.x},${position.y},${boxSizePx},${boxSizePx}"
                                        } ?: "座標: -, -, -, -"
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        SpritePreviewBlock(
                                            imageBitmap = imageBitmap,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp),
                                            line1Text = previewHeaderText,
                                            line2Text = "選択中: ${selectedNumber}/9 | サイズ: ${boxSizePx}px | $coordinateText",
                                            onContainerSizeChanged = { newContainerSize: IntSize ->
                                                containerSize = newContainerSize
                                                if (imageBitmap.width != 0) {
                                                    displayScale = newContainerSize.width / imageBitmap.width.toFloat()
                                                }
                                            }
                                        )
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp, horizontal = 12.dp)
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "DEVパラメータのコピーはDEVセクション内のボタンから行えます",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Divider()
                                            Text(
                                                text = "現在のDEV設定概要",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "CardMax:${devPreviewSettings.cardMaxHeightDp}  MinH:${devPreviewSettings.cardMinHeightDp}  Details:${devPreviewSettings.detailsMaxHeightDp} / ${devPreviewSettings.detailsMaxLines}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Text(
                                                text = "Offsets L(${devPreviewSettings.headerLeftXOffsetDp},${devPreviewSettings.headerLeftYOffsetDp}) R(${devPreviewSettings.headerRightXOffsetDp},${devPreviewSettings.headerRightYOffsetDp}) InfoX:${devPreviewSettings.infoXOffsetDp} InfoY:${devPreviewSettings.infoYOffsetDp} CharY:${devPreviewSettings.charYOffsetDp}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Text(
                                                text = "Padding Outer:${devPreviewSettings.outerBottomDp}  Inner:${devPreviewSettings.innerBottomDp}  VPad:${devPreviewSettings.innerVPadDp}  Spacer H:${devPreviewSettings.headerSpacerDp} / B:${devPreviewSettings.bodySpacerDp}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
    devSettings: DevPreviewSettings,
    onDevSettingsChange: (DevPreviewSettings) -> Unit,
    initialHeaderLeftXOffsetDp: Int?,
) {
    val clipboardManager = LocalClipboardManager.current
    val selectedAnimation = selectionState.selectedAnimation
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val onCopyDevJson: () -> Unit = { copyDevJson(clipboardManager, devSettings) }
    val onFieldFocused: (Int) -> Unit = { targetIndex ->
        coroutineScope.launch { lazyListState.animateScrollToItem(index = targetIndex) }
    }
    val layoutDirection = LocalLayoutDirection.current
    val bottomContentPadding = contentPadding.calculateBottomPadding() + if (isImeVisible) 2.dp else 0.dp
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
                modifier = Modifier.fillMaxWidth(),
                initialHeaderLeftXOffsetDp = initialHeaderLeftXOffsetDp,
                devSettings = devSettings,
                onDevSettingsChange = onDevSettingsChange,
                onCopy = onCopyDevJson
            )
        }
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

private data class ReadyAnimationState(
    val frameRegion: SpriteFrameRegion?,
    val currentFramePosition: Int,
    val totalFrames: Int,
    val currentIntervalMs: Int,
)

@Composable
private fun rememberReadyAnimationState(
    spriteSheetConfig: SpriteSheetConfig,
    summary: AnimationSummary,
    insertionSummary: AnimationSummary,
    insertionEnabled: Boolean,
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

    return ReadyAnimationState(
        frameRegion = frameRegion,
        currentFramePosition = currentFramePosition,
        totalFrames = totalFrames,
        currentIntervalMs = currentIntervalMs
    )
}

@Composable
private fun ReadyAnimationCharacter(
    imageBitmap: ImageBitmap,
    frameRegion: SpriteFrameRegion?,
    spriteSizeDp: Dp,
    charYOffsetDp: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(spriteSizeDp)
            .offset(y = charYOffsetDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dstW = size.width.roundToInt().coerceAtLeast(1)
            val dstH = size.height.roundToInt().coerceAtLeast(1)
            val side = min(dstW, dstH).coerceAtLeast(1)
            val squareSize = IntSize(side, side)
            val offset = IntOffset((dstW - side) / 2, (dstH - side) / 2)

            drawFrameRegion(
                sheet = imageBitmap,
                region = frameRegion,
                dstSize = squareSize,
                dstOffset = offset,
                placeholder = { placeholderOffset, placeholderSize ->
                    drawFramePlaceholder(offset = placeholderOffset, size = placeholderSize)
                }
            )
        }
    }
}

@Composable
private fun ReadyAnimationInfo(
    state: ReadyAnimationState,
    summary: AnimationSummary,
    insertionSummary: AnimationSummary,
    insertionPreviewValues: InsertionPreviewValues,
    insertionEnabled: Boolean,
    infoYOffsetDp: Int,
    modifier: Modifier = Modifier,
) {
    val paramYOffsetDp = 2
    val lineSpacing = 4.dp
    val baseFramesText = summary.frames.ifEmpty { listOf(0) }
        .joinToString(",") { value -> (value + 1).toString() }
    val insertionFramesText = insertionSummary.frames.ifEmpty { listOf(0) }
        .joinToString(",") { value -> (value + 1).toString() }
    val insertionLine = if (insertionEnabled && insertionSummary.enabled) {
        "挿入: ${insertionSummary.intervalMs}ms/$insertionFramesText"
    } else {
        "挿入: OFF"
    }
    val everyNText = insertionPreviewValues.everyNText.ifBlank { "-" }
    val probabilityText = insertionPreviewValues.probabilityText.ifBlank { "-" }
    val cooldownText = insertionPreviewValues.cooldownText.ifBlank { "-" }
    val exclusiveText = insertionPreviewValues.exclusiveText.ifBlank { "-" }
    Column(
        modifier = modifier
            .offset(y = (paramYOffsetDp + infoYOffsetDp).dp),
        verticalArrangement = Arrangement.spacedBy(lineSpacing, Alignment.Top)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "State:${summary.label}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "プレビュー",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "フレーム: ${state.currentFramePosition + 1}/${state.totalFrames}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "周期: ${state.currentIntervalMs}ms",
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
            text = "N:$everyNText    P:$probabilityText    CD:$cooldownText    Excl:$exclusiveText",
            style = MaterialTheme.typography.bodySmall
        )
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
    initialHeaderLeftXOffsetDp: Int?,
    devSettings: DevPreviewSettings,
    onDevSettingsChange: (DevPreviewSettings) -> Unit,
    onCopy: () -> Unit,
) {
    var cardMaxHeightDp by rememberSaveable(devSettings.cardMaxHeightDp) { mutableIntStateOf(devSettings.cardMaxHeightDp) }
    var innerBottomDp by rememberSaveable(devSettings.innerBottomDp) { mutableIntStateOf(devSettings.innerBottomDp) }
    var outerBottomDp by rememberSaveable(devSettings.outerBottomDp) { mutableIntStateOf(devSettings.outerBottomDp) }
    var innerVPadDp by rememberSaveable(devSettings.innerVPadDp) { mutableIntStateOf(devSettings.innerVPadDp) }
    var charYOffsetDp by rememberSaveable(devSettings.charYOffsetDp) { mutableIntStateOf(devSettings.charYOffsetDp) }
    var infoXOffsetDp by rememberSaveable(devSettings.infoXOffsetDp) { mutableIntStateOf(devSettings.infoXOffsetDp) }
    var infoYOffsetDp by rememberSaveable(devSettings.infoYOffsetDp) { mutableIntStateOf(devSettings.infoYOffsetDp) }
    var headerOffsetLimitDp by rememberSaveable(devSettings.headerOffsetLimitDp) { mutableIntStateOf(devSettings.headerOffsetLimitDp) }
    var headerLeftXOffsetDp by rememberSaveable(devSettings.headerLeftXOffsetDp) { mutableIntStateOf(devSettings.headerLeftXOffsetDp) }
    var headerLeftYOffsetDp by rememberSaveable(devSettings.headerLeftYOffsetDp) { mutableIntStateOf(devSettings.headerLeftYOffsetDp) }
    var headerRightXOffsetDp by rememberSaveable(devSettings.headerRightXOffsetDp) { mutableIntStateOf(devSettings.headerRightXOffsetDp) }
    var headerRightYOffsetDp by rememberSaveable(devSettings.headerRightYOffsetDp) { mutableIntStateOf(devSettings.headerRightYOffsetDp) }
    var cardMinHeightDp by rememberSaveable(devSettings.cardMinHeightDp) { mutableIntStateOf(devSettings.cardMinHeightDp) }
    var detailsMaxHeightDp by rememberSaveable(devSettings.detailsMaxHeightDp) { mutableIntStateOf(devSettings.detailsMaxHeightDp) }
    var detailsMaxLines by rememberSaveable(devSettings.detailsMaxLines) { mutableIntStateOf(devSettings.detailsMaxLines) }
    var headerSpacerDp by rememberSaveable(devSettings.headerSpacerDp) { mutableIntStateOf(devSettings.headerSpacerDp) }
    var bodySpacerDp by rememberSaveable(devSettings.bodySpacerDp) { mutableIntStateOf(devSettings.bodySpacerDp) }
    LaunchedEffect(devSettings) {
        cardMaxHeightDp = devSettings.cardMaxHeightDp
        innerBottomDp = devSettings.innerBottomDp
        outerBottomDp = devSettings.outerBottomDp
        innerVPadDp = devSettings.innerVPadDp
        charYOffsetDp = devSettings.charYOffsetDp
        infoXOffsetDp = devSettings.infoXOffsetDp.coerceIn(INFO_X_OFFSET_MIN, INFO_X_OFFSET_MAX)
        infoYOffsetDp = devSettings.infoYOffsetDp
        headerOffsetLimitDp = devSettings.headerOffsetLimitDp
        headerLeftXOffsetDp = devSettings.headerLeftXOffsetDp
        headerLeftYOffsetDp = devSettings.headerLeftYOffsetDp
        headerRightXOffsetDp = devSettings.headerRightXOffsetDp
        headerRightYOffsetDp = devSettings.headerRightYOffsetDp
        cardMinHeightDp = devSettings.cardMinHeightDp
        detailsMaxHeightDp = devSettings.detailsMaxHeightDp
        detailsMaxLines = devSettings.detailsMaxLines
        headerSpacerDp = devSettings.headerSpacerDp
        bodySpacerDp = devSettings.bodySpacerDp
    }
    fun propagateDevSettings() {
        val clampedInfoXOffsetDp = infoXOffsetDp.coerceIn(INFO_X_OFFSET_MIN, INFO_X_OFFSET_MAX)
        infoXOffsetDp = clampedInfoXOffsetDp
        onDevSettingsChange(
            DevPreviewSettings(
                cardMaxHeightDp = cardMaxHeightDp,
                innerBottomDp = innerBottomDp,
                outerBottomDp = outerBottomDp,
                innerVPadDp = innerVPadDp,
                charYOffsetDp = charYOffsetDp,
                infoXOffsetDp = clampedInfoXOffsetDp,
                infoYOffsetDp = infoYOffsetDp,
                headerOffsetLimitDp = headerOffsetLimitDp,
                headerLeftXOffsetDp = headerLeftXOffsetDp,
                headerLeftYOffsetDp = headerLeftYOffsetDp,
                headerRightXOffsetDp = headerRightXOffsetDp,
                headerRightYOffsetDp = headerRightYOffsetDp,
                cardMinHeightDp = cardMinHeightDp,
                detailsMaxHeightDp = detailsMaxHeightDp,
                detailsMaxLines = detailsMaxLines,
                headerSpacerDp = headerSpacerDp,
                bodySpacerDp = bodySpacerDp,
            )
        )
    }
    fun updateDevSettings(block: () -> Unit) {
        block()
        propagateDevSettings()
    }
    LaunchedEffect(initialHeaderLeftXOffsetDp) {
        initialHeaderLeftXOffsetDp?.let { initial ->
            headerLeftXOffsetDp = initial.coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp)
        }
    }
    val baseMaxHeightDp = if (isImeVisible) 220 else 300
    val customCardMaxHeightDp = cardMaxHeightDp.takeUnless { it == 0 }
    val effectiveCardMaxH: Int? = customCardMaxHeightDp ?: baseMaxHeightDp
    val boundedMinHeightDp = effectiveCardMaxH?.let { max -> cardMinHeightDp.coerceAtMost(max) } ?: cardMinHeightDp
    val effectiveMinHeightDp = effectiveCardMaxH?.let { max -> boundedMinHeightDp.coerceAtMost(max) } ?: boundedMinHeightDp
    val effectiveOuterBottomDp = outerBottomDp
    val effectiveInnerBottomDp = innerBottomDp
    val effectiveInnerVPadDp = innerVPadDp
    val effectiveBodySpacerDp = bodySpacerDp
    val effectiveDetailsMaxH = detailsMaxHeightDp.coerceAtLeast(1)

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
                val effectiveMinDp = effectiveMinHeightDp
                val effectiveMaxLabel = effectiveCardMaxH?.toString() ?: "∞"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { devExpanded = !devExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val devArrow = if (devExpanded) "▴" else "▾"
                        Text(
                            text = "DEV $devArrow",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "MinH:${effectiveMinDp} / MaxH:${effectiveMaxLabel}  InfoX:${infoXOffsetDp}  InfoY:${infoYOffsetDp}  HdrL:(${headerLeftXOffsetDp},${headerLeftYOffsetDp})  HdrR:(${headerRightXOffsetDp},${headerRightYOffsetDp})",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    FilledTonalButton(
                        onClick = onCopy,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("JSONコピー")
                    }
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
                                    onClick = { updateDevSettings { charYOffsetDp = (charYOffsetDp - 1).coerceIn(-200, 200) } }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = { updateDevSettings { charYOffsetDp = (charYOffsetDp + 1).coerceIn(-200, 200) } }
                                ) {
                                    Text("▼")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "InfoX:${infoXOffsetDp}dp / 情報ブロックX",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = {
                                        updateDevSettings {
                                            infoXOffsetDp = (infoXOffsetDp - 1).coerceIn(INFO_X_OFFSET_MIN, INFO_X_OFFSET_MAX)
                                        }
                                    }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = {
                                        updateDevSettings {
                                            infoXOffsetDp = (infoXOffsetDp + 1).coerceIn(INFO_X_OFFSET_MIN, INFO_X_OFFSET_MAX)
                                        }
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
                                    onClick = { updateDevSettings { infoYOffsetDp = (infoYOffsetDp - 1).coerceIn(-200, 200) } }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = { updateDevSettings { infoYOffsetDp = (infoYOffsetDp + 1).coerceIn(-200, 200) } }
                                ) {
                                    Text("▼")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "HeaderLimit:${headerOffsetLimitDp}dp / 見出し移動限界",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = {
                                        updateDevSettings {
                                            headerOffsetLimitDp = (headerOffsetLimitDp + 10).coerceIn(0, 400)
                                            headerLeftXOffsetDp = headerLeftXOffsetDp.coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp)
                                            headerLeftYOffsetDp = headerLeftYOffsetDp.coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp)
                                            headerRightXOffsetDp = headerRightXOffsetDp.coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp)
                                            headerRightYOffsetDp = headerRightYOffsetDp.coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp)
                                        }
                                    }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = {
                                        updateDevSettings {
                                            headerOffsetLimitDp = (headerOffsetLimitDp - 10).coerceIn(0, 400)
                                            headerLeftXOffsetDp = headerLeftXOffsetDp.coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp)
                                            headerLeftYOffsetDp = headerLeftYOffsetDp.coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp)
                                            headerRightXOffsetDp = headerRightXOffsetDp.coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp)
                                            headerRightYOffsetDp = headerRightYOffsetDp.coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp)
                                        }
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
                                    text = "HeaderLeftX:${headerLeftXOffsetDp}dp / 見出し左X",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = { updateDevSettings { headerLeftXOffsetDp = (headerLeftXOffsetDp - 1).coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp) } }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = { updateDevSettings { headerLeftXOffsetDp = (headerLeftXOffsetDp + 1).coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp) } }
                                ) {
                                    Text("▼")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "HeaderLeftY:${headerLeftYOffsetDp}dp / 見出し左Y",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = { updateDevSettings { headerLeftYOffsetDp = (headerLeftYOffsetDp - 1).coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp) } }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = { updateDevSettings { headerLeftYOffsetDp = (headerLeftYOffsetDp + 1).coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp) } }
                                ) {
                                    Text("▼")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "HeaderRightX:${headerRightXOffsetDp}dp / 見出し右X",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = { updateDevSettings { headerRightXOffsetDp = (headerRightXOffsetDp - 1).coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp) } }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = { updateDevSettings { headerRightXOffsetDp = (headerRightXOffsetDp + 1).coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp) } }
                                ) {
                                    Text("▼")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "HeaderRightY:${headerRightYOffsetDp}dp / 見出し右Y",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = { updateDevSettings { headerRightYOffsetDp = (headerRightYOffsetDp - 1).coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp) } }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = { updateDevSettings { headerRightYOffsetDp = (headerRightYOffsetDp + 1).coerceIn(-headerOffsetLimitDp, headerOffsetLimitDp) } }
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
                                    onClick = { updateDevSettings { outerBottomDp = (outerBottomDp + 1).coerceIn(0, 80) } }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = { updateDevSettings { outerBottomDp = (outerBottomDp - 1).coerceIn(0, 80) } }
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
                                    onClick = { updateDevSettings { innerBottomDp = (innerBottomDp + 1).coerceIn(0, 80) } }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = { updateDevSettings { innerBottomDp = (innerBottomDp - 1).coerceIn(0, 80) } }
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
                                    onClick = { updateDevSettings { innerVPadDp = (innerVPadDp + 1).coerceIn(0, 24) } }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = { updateDevSettings { innerVPadDp = (innerVPadDp - 1).coerceIn(0, 24) } }
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
                                val cardMaxLabel = effectiveCardMaxH?.let { "${it}dp" } ?: "制限なし"
                                Text(
                                    text = "CardMax:${cardMaxLabel} / DEV:${cardMaxHeightDp}dp / Base:${baseMaxHeightDp}dp",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = { updateDevSettings { cardMaxHeightDp = (cardMaxHeightDp + 10).coerceIn(0, 1200) } }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = { updateDevSettings { cardMaxHeightDp = (cardMaxHeightDp - 10).coerceIn(0, 1200) } }
                                ) {
                                    Text("▼")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "MinHeight:${effectiveMinDp}dp / カード最小高",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = { updateDevSettings { cardMinHeightDp = (cardMinHeightDp + 1).coerceIn(0, 320) } }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = { updateDevSettings { cardMinHeightDp = (cardMinHeightDp - 1).coerceIn(0, 320) } }
                                ) {
                                    Text("▼")
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "DetailsMaxH:${effectiveDetailsMaxH}dp / DEV:${detailsMaxHeightDp}dp",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                IconButton(
                                    onClick = { updateDevSettings { detailsMaxHeightDp = (detailsMaxHeightDp + 10).coerceIn(0, 1200) } }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = { updateDevSettings { detailsMaxHeightDp = (detailsMaxHeightDp - 10).coerceIn(0, 1200) } }
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
                                    onClick = { updateDevSettings { detailsMaxLines = (detailsMaxLines + 1).coerceIn(1, 6) } }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = { updateDevSettings { detailsMaxLines = (detailsMaxLines - 1).coerceIn(1, 6) } }
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
                                    onClick = { updateDevSettings { headerSpacerDp = (headerSpacerDp + 1).coerceIn(0, 24) } }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = { updateDevSettings { headerSpacerDp = (headerSpacerDp - 1).coerceIn(0, 24) } }
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
                                    onClick = { updateDevSettings { bodySpacerDp = (bodySpacerDp + 1).coerceIn(0, 24) } }
                                ) {
                                    Text("▲")
                                }
                                IconButton(
                                    onClick = { updateDevSettings { bodySpacerDp = (bodySpacerDp - 1).coerceIn(0, 24) } }
                                ) {
                                    Text("▼")
                                }
                            }
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
                .padding(bottom = effectiveOuterBottomDp.dp)
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
            val baseCardModifier = Modifier.fillMaxWidth()
            val cardHeightModifier = if (effectiveCardMaxH != null) {
                baseCardModifier.heightIn(
                    min = effectiveMinHeightDp.dp,
                    max = effectiveCardMaxH.dp
                )
            } else {
                baseCardModifier.heightIn(min = effectiveMinHeightDp.dp)
            }
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
            ) {
                val rawSpriteSize = (maxWidth * 0.30f).coerceAtLeast(1.dp)
                val spriteSize = if (isImeVisible) {
                    rawSpriteSize.coerceIn(56.dp, 96.dp)
                } else {
                    rawSpriteSize.coerceIn(72.dp, 120.dp)
                }
                val previewState = rememberReadyAnimationState(
                    spriteSheetConfig = spriteSheetConfig,
                    summary = baseSummary,
                    insertionSummary = insertionSummary,
                    insertionEnabled = insertionEnabled
                )
                val contentHorizontalPadding = 12.dp

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

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Card(
                        modifier = cardHeightModifier,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = contentHorizontalPadding, vertical = effectiveInnerVPadDp.dp)
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
                                .padding(bottom = effectiveInnerBottomDp.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ReadyAnimationInfo(
                                state = previewState,
                                summary = baseSummary,
                                insertionSummary = insertionSummary,
                                insertionPreviewValues = insertionPreviewValues,
                                insertionEnabled = insertionEnabled,
                                infoYOffsetDp = infoYOffsetDp + headerSpacerDp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(
                                        x = headerLeftXOffsetDp.dp,
                                        y = headerLeftYOffsetDp.dp
                                    )
                                    .padding(start = spriteSize + 8.dp + infoXOffsetDp.dp)
                            )
                        }
                    }
                    ReadyAnimationCharacter(
                        imageBitmap = imageBitmap,
                        frameRegion = previewState.frameRegion,
                        spriteSizeDp = spriteSize,
                        charYOffsetDp = charYOffsetDp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = contentHorizontalPadding,
                                top = effectiveInnerVPadDp.dp + headerSpacerDp.dp
                            )
                    )
                }
            // FIX: missing brace for ReadyAnimationPreviewPane
            }
        }
    }
}

@Composable
private fun SpritePreviewBlock(
    imageBitmap: ImageBitmap,
    line1Text: String,
    line2Text: String,
    modifier: Modifier = Modifier,
    onContainerSizeChanged: ((IntSize) -> Unit)? = null,
    overlayContent: @Composable BoxScope.() -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .heightIn(min = 220.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Sprite Preview",
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { newSize -> onContainerSizeChanged?.invoke(newSize) },
                contentScale = ContentScale.Fit
            )
            overlayContent()
        }
        val infoTextStyle = MaterialTheme.typography.labelMedium.copy(
            lineHeight = MaterialTheme.typography.labelMedium.fontSize
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = line1Text,
                style = infoTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = line2Text,
                style = infoTextStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SpriteSettingsControls(
    buttonHeight: Dp,
    buttonContentPadding: PaddingValues,
    buttonShape: Shape,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onMoveXNegative: () -> Unit,
    onMoveXPositive: () -> Unit,
    onMoveYNegative: () -> Unit,
    onMoveYPositive: () -> Unit,
    onSizeDecrease: () -> Unit,
    onSizeIncrease: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val buttonModifier = Modifier
            .weight(1f)
            .height(buttonHeight)

        val navigatorButtonColors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF6A00FF),
            contentColor = Color.White
        )
        val defaultControlButtonColors = ButtonDefaults.filledTonalButtonColors()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onPrev,
                    modifier = buttonModifier.semantics { contentDescription = "Previous" },
                    colors = navigatorButtonColors,
                    contentPadding = buttonContentPadding,
                    shape = buttonShape
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                FilledTonalButton(
                    onClick = onNext,
                    modifier = buttonModifier.semantics { contentDescription = "Next" },
                    colors = navigatorButtonColors,
                    contentPadding = buttonContentPadding,
                    shape = buttonShape
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                FilledTonalButton(
                    onClick = onMoveXNegative,
                    modifier = buttonModifier,
                    colors = defaultControlButtonColors,
                    contentPadding = buttonContentPadding,
                    shape = buttonShape
                ) {
                    Text("X-")
                }
                FilledTonalButton(
                    onClick = onMoveYNegative,
                    modifier = buttonModifier,
                    colors = defaultControlButtonColors,
                    contentPadding = buttonContentPadding,
                    shape = buttonShape
                ) {
                    Text("Y-")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onSizeDecrease,
                    modifier = buttonModifier,
                    colors = defaultControlButtonColors,
                    contentPadding = buttonContentPadding,
                    shape = buttonShape
                ) {
                    Text("-")
                }
                FilledTonalButton(
                    onClick = onSizeIncrease,
                    modifier = buttonModifier,
                    colors = defaultControlButtonColors,
                    contentPadding = buttonContentPadding,
                    shape = buttonShape
                ) {
                    Text("+")
                }
                FilledTonalButton(
                    onClick = onMoveXPositive,
                    modifier = buttonModifier,
                    colors = defaultControlButtonColors,
                    contentPadding = buttonContentPadding,
                    shape = buttonShape
                ) {
                    Text("X+")
                }
                FilledTonalButton(
                    onClick = onMoveYPositive,
                    modifier = buttonModifier,
                    colors = defaultControlButtonColors,
                    contentPadding = buttonContentPadding,
                    shape = buttonShape
                ) {
                    Text("Y+")
                }
            }
        }
    }
}

// UI調整: 下部操作ボタンをピル形状に戻し、矢印を真紫で視認性を向上。
private fun buildSettingsJson(
    animationType: AnimationType,
    spriteSheetConfig: SpriteSheetConfig,
    readyBase: ReadyAnimationSettings,
    talkingBase: ReadyAnimationSettings,
    readyInsertion: InsertionAnimationSettings,
    talkingInsertion: InsertionAnimationSettings,
    devSettings: DevPreviewSettings,
): String {
    val root = JSONObject()
    root.put("animationType", animationType.label)
    root.put(
        "ready",
        JSONObject()
            .put("base", readyBase.toJsonObject())
            .put("insertion", readyInsertion.toJsonObject())
    )
    root.put(
        "talking",
        JSONObject()
            .put("base", talkingBase.toJsonObject())
            .put("insertion", talkingInsertion.toJsonObject())
    )
    root.put("spriteSheetConfig", spriteSheetConfig.toJsonObject())
    root.put("dev", devSettings.toJsonObject())
    return root.toString(2)
}

private fun copyDevJson(
    clipboardManager: ClipboardManager,
    devSettings: DevPreviewSettings,
) {
    val jsonString = buildDevJson(devSettings)
    clipboardManager.setText(AnnotatedString(jsonString))
}

private fun buildDevJson(
    devSettings: DevPreviewSettings,
): String {
    val root = JSONObject()
    root.put("dev", devSettings.toJsonObject())
    return root.toString(2)
}

private fun ReadyAnimationSettings.toJsonObject(): JSONObject =
    JSONObject()
        .put("frames", frameSequence.toJsonArray())
        .put("intervalMs", intervalMs)

private fun InsertionAnimationSettings.toJsonObject(): JSONObject =
    JSONObject()
        .put("enabled", enabled)
        .put("frames", frameSequence.toJsonArray())
        .put("intervalMs", intervalMs)
        .put("everyNLoops", everyNLoops)
        .put("probabilityPercent", probabilityPercent)
        .put("cooldownLoops", cooldownLoops)
        .put("exclusive", exclusive)

private fun SpriteSheetConfig.toJsonObject(): JSONObject =
    JSONObject()
        .put("rows", rows)
        .put("cols", cols)
        .put("frameWidth", frameWidth)
        .put("frameHeight", frameHeight)
        .put(
            "boxes",
            JSONArray().apply {
                boxes.forEach { box ->
                    put(
                        JSONObject()
                            .put("frameIndex", box.frameIndex)
                            .put("x", box.x)
                            .put("y", box.y)
                            .put("width", box.width)
                            .put("height", box.height)
                    )
                }
            }
        )

private fun DevPreviewSettings.toJsonObject(): JSONObject =
    JSONObject()
        .put("cardMaxHeightDp", cardMaxHeightDp)
        .put("charYOffsetDp", charYOffsetDp)
        .put("infoXOffsetDp", infoXOffsetDp)
        .put("infoYOffsetDp", infoYOffsetDp)
        .put("headerOffsetLimitDp", headerOffsetLimitDp)
        .put("headerLeftXOffsetDp", headerLeftXOffsetDp)
        .put("headerLeftYOffsetDp", headerLeftYOffsetDp)
        .put("headerRightXOffsetDp", headerRightXOffsetDp)
        .put("headerRightYOffsetDp", headerRightYOffsetDp)
        .put("innerVPadDp", innerVPadDp)
        .put("innerBottomDp", innerBottomDp)
        .put("outerBottomDp", outerBottomDp)
        .put("cardMinHeightDp", cardMinHeightDp)
        .put("detailsMaxHeightDp", detailsMaxHeightDp)
        .put("detailsMaxLines", detailsMaxLines)
        .put("headerSpacerDp", headerSpacerDp)
        .put("bodySpacerDp", bodySpacerDp)

private fun List<Int>.toJsonArray(): JSONArray =
    JSONArray().apply { forEach { value -> put(value) } }
