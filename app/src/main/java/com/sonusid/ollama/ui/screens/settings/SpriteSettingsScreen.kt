package com.sonusid.ollama.ui.screens.settings

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
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
import com.sonusid.ollama.ui.components.ReadyPreviewLayoutState
import com.sonusid.ollama.ui.components.ReadyPreviewSlot
import com.sonusid.ollama.ui.components.SpriteFrameRegion
import com.sonusid.ollama.ui.components.DevMenuSectionHost
import com.sonusid.ollama.ui.components.drawFramePlaceholder
import com.sonusid.ollama.ui.components.drawFrameRegion
import com.sonusid.ollama.ui.components.rememberReadyPreviewLayoutState
import kotlinx.coroutines.CoroutineScope
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

private fun Modifier.debugBounds(tag: String): Modifier =
    this.onGloballyPositioned { c ->
        val r = c.boundsInWindow()
        Log.d(
            "SpritePos",
            "$tag left=${r.left} top=${r.top} right=${r.right} bottom=${r.bottom} " +
                "w=${r.width} h=${r.height}"
        )
    }

private enum class AnimationType(val label: String) {
    READY("Ready"),
    TALKING("Talking（ロング）");

    companion object {
        val options = values().toList()
    }
}

private enum class SpriteTab {
    ANIM,
    ADJUST,
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

internal data class DevPreviewSettings(
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

internal data class ReadyPreviewUiState(
    val charYOffsetDp: Int,
    val effectiveMinHeightDp: Int,
    val effectiveCardMaxH: Int?,
    val infoXOffsetDp: Int,
    val infoYOffsetDp: Int,
    val headerLeftXOffsetDp: Int,
    val headerLeftYOffsetDp: Int,
    val headerRightXOffsetDp: Int,
    val headerRightYOffsetDp: Int,
    val baseMaxHeightDp: Int,
    val effectiveDetailsMaxH: Int,
    val outerBottomDp: Int,
    val innerBottomDp: Int,
    val innerVPadDp: Int,
    val detailsMaxHeightDp: Int,
    val cardMaxHeightDp: Int,
    val cardMinHeightDp: Int,
    val detailsMaxLines: Int,
    val headerOffsetLimitDp: Int,
    val headerSpacerDp: Int,
    val bodySpacerDp: Int,
)

// 作業メモ(Step0):
// - devUnlocked/devMenuEnabled/devExpanded および DEV UI 本体は ReadyAnimationPreviewPane 内のDEVブロック（行2400前後）。
// - 「開発メニュー」トグルは devUnlocked ガード内のRow（行2056前後）。
// - TopAppBar/actions相当は存在せず、上部は戻るIconButtonのみ（行1200前後）。

private object DevDefaults {
    const val cardMaxHeightDp = 130
    const val innerBottomDp = 0
    const val outerBottomDp = 0
    const val innerVPadDp = 8
    const val charYOffsetDp = 5
    const val infoXOffsetDp = -107
    const val infoYOffsetDp = 3
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
internal const val INFO_X_OFFSET_MIN = -500
internal const val INFO_X_OFFSET_MAX = 500

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

@OptIn(ExperimentalMaterial3Api::class)
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
    var selectedTab by rememberSaveable { mutableStateOf(SpriteTab.ANIM) }
    val devUnlocked = true
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
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

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

    fun isBaseInputSynced(target: AnimationType): Boolean {
        val (inputFrames, inputInterval) = when (target) {
            AnimationType.READY -> readyFrameInput to readyIntervalInput
            AnimationType.TALKING -> talkingFrameInput to talkingIntervalInput
        }
        val (appliedFrames, appliedInterval) = when (target) {
            AnimationType.READY -> appliedReadyFrames to appliedReadyIntervalMs
            AnimationType.TALKING -> appliedTalkingFrames to appliedTalkingIntervalMs
        }
        val framesResult = parseFrameSequenceInput(
            input = inputFrames,
            frameCount = spriteSheetConfig.frameCount,
            allowDuplicates = true
        )
        val intervalResult = parseIntervalMsInput(inputInterval)

        val framesMatch = framesResult.value?.let { parsed -> parsed == appliedFrames } ?: false
        val intervalMatch = intervalResult.value?.let { parsed -> parsed == appliedInterval } ?: false
        return framesMatch && intervalMatch
    }

    fun isInsertionInputSynced(target: AnimationType): Boolean {
        val inputEnabled: Boolean
        val inputExclusive: Boolean
        val framesResult: ValidationResult<List<Int>>
        val intervalResult: ValidationResult<Int>
        val everyNResult: ValidationResult<Int>
        val probabilityResult: ValidationResult<Int>
        val cooldownResult: ValidationResult<Int>
        when (target) {
            AnimationType.READY -> {
                inputEnabled = readyInsertionEnabled
                inputExclusive = readyInsertionExclusive
                framesResult = parseFrameSequenceInput(
                    input = readyInsertionFrameInput,
                    frameCount = spriteSheetConfig.frameCount,
                    duplicateErrorMessage = "挿入フレームは重複しないように入力してください"
                )
                intervalResult = parseIntervalMsInput(readyInsertionIntervalInput)
                everyNResult = parseEveryNLoopsInput(readyInsertionEveryNInput)
                probabilityResult = parseProbabilityPercentInput(readyInsertionProbabilityInput)
                cooldownResult = parseCooldownLoopsInput(readyInsertionCooldownInput)
            }

            AnimationType.TALKING -> {
                inputEnabled = talkingInsertionEnabled
                inputExclusive = talkingInsertionExclusive
                framesResult = parseFrameSequenceInput(
                    input = talkingInsertionFrameInput,
                    frameCount = spriteSheetConfig.frameCount,
                    duplicateErrorMessage = "挿入フレームは重複しないように入力してください"
                )
                intervalResult = parseIntervalMsInput(talkingInsertionIntervalInput)
                everyNResult = parseEveryNLoopsInput(talkingInsertionEveryNInput)
                probabilityResult = parseProbabilityPercentInput(talkingInsertionProbabilityInput)
                cooldownResult = parseCooldownLoopsInput(talkingInsertionCooldownInput)
            }
        }
        val inputState = listOf(
            framesResult.value,
            intervalResult.value,
            everyNResult.value,
            probabilityResult.value,
            cooldownResult.value
        )
        if (inputState.any { it == null }) {
            return false
        }

        val parsedInsertion = InsertionAnimationSettings(
            enabled = inputEnabled,
            frameSequence = framesResult.value!!,
            intervalMs = intervalResult.value!!,
            everyNLoops = everyNResult.value!!,
            probabilityPercent = probabilityResult.value!!,
            cooldownLoops = cooldownResult.value!!,
            exclusive = inputExclusive
        )

        val appliedState = when (target) {
            AnimationType.READY -> InsertionAnimationSettings(
                enabled = appliedReadyInsertionEnabled,
                frameSequence = appliedReadyInsertionFrames,
                intervalMs = appliedReadyInsertionIntervalMs,
                everyNLoops = appliedReadyInsertionEveryNLoops,
                probabilityPercent = appliedReadyInsertionProbabilityPercent,
                cooldownLoops = appliedReadyInsertionCooldownLoops,
                exclusive = appliedReadyInsertionExclusive
            )

            AnimationType.TALKING -> InsertionAnimationSettings(
                enabled = appliedTalkingInsertionEnabled,
                frameSequence = appliedTalkingInsertionFrames,
                intervalMs = appliedTalkingInsertionIntervalMs,
                everyNLoops = appliedTalkingInsertionEveryNLoops,
                probabilityPercent = appliedTalkingInsertionProbabilityPercent,
                cooldownLoops = appliedTalkingInsertionCooldownLoops,
                exclusive = appliedTalkingInsertionExclusive
            )
        }

        if (parsedInsertion.enabled != appliedState.enabled || parsedInsertion.exclusive != appliedState.exclusive) {
            return false
        }

        val frameMatches = parsedInsertion.frameSequence == appliedState.frameSequence
        val intervalMatches = parsedInsertion.intervalMs == appliedState.intervalMs
        val everyNMatches = parsedInsertion.everyNLoops == appliedState.everyNLoops
        val probabilityMatches = parsedInsertion.probabilityPercent == appliedState.probabilityPercent
        val cooldownMatches = parsedInsertion.cooldownLoops == appliedState.cooldownLoops

        return frameMatches && intervalMatches && everyNMatches && probabilityMatches && cooldownMatches
    }

    fun hasUnsavedChanges(): Boolean {
        val baseSynced = isBaseInputSynced(AnimationType.READY) && isBaseInputSynced(AnimationType.TALKING)
        val insertionSynced =
            isInsertionInputSynced(AnimationType.READY) && isInsertionInputSynced(AnimationType.TALKING)
        return !(baseSynced && insertionSynced)
    }

    fun onBackRequested() {
        if (showDiscardDialog) {
            showDiscardDialog = false
            return
        }
        if (hasUnsavedChanges()) {
            showDiscardDialog = true
        } else {
            navController.popBackStack()
        }
    }

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

    // [非dp] 下: IME の insets(インセット)に関係
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

    BackHandler(onBack = { onBackRequested() })

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text(text = "破棄して戻る")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(text = "キャンセル")
                }
            },
            title = { Text(text = "編集内容を破棄しますか？") },
            text = { Text(text = "保存していない変更があります。破棄して戻りますか？") }
        )
    }

    // [非dp] 縦: Scaffold の insets(インセット)に関係
    val configuration = LocalConfiguration.current
    val adaptiveHorizontalPadding = maxOf(8.dp, minOf(16.dp, configuration.screenWidthDp.dp * 0.02f))
    val actionButtonHeight = 28.dp // 上部操作ボタンも下部と同じ厚みに統一
    // [dp] 縦: 画面全体 の最小サイズ(最小サイズ)に関係
    val actionButtonModifier = Modifier
        // [非dp] 横: 画面全体 の weight(制約)に関係
        .weight(1f)
        // [dp] 縦: 画面全体 の最小サイズ(最小サイズ)に関係
        .height(actionButtonHeight)
    // [dp] 縦横: 画面全体 の余白(余白)に関係
    val actionButtonPadding = PaddingValues(
        horizontal = 12.dp,
        vertical = 0.dp
    ) // 内部余白を最小化して厚みを揃える
    val actionButtonShape = RoundedCornerShape(999.dp)
    // [dp] 縦: 画面全体 の最小サイズ(最小サイズ)に関係
    val controlButtonHeight = 28.dp // 下部操作ボタンをコンパクト化
    // [dp] 縦横: 画面全体 の余白(余白)に関係
    val controlButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    // [dp] 縦横: 本文アクション列 の余白(余白)に関係
    val contentActionRowPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
    // [dp] 縦横: TopAppBar 下段 の余白(余白)に関係
    val topBarActionRowPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

    Scaffold(
        topBar = {
            Column {
                SmallTopAppBar(
                    title = {
                        Text(
                            text = "Sprite Settings",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { onBackRequested() }) {
                            Icon(
                                painter = painterResource(R.drawable.back),
                                contentDescription = "戻る"
                            )
                        }
                    },
                    actions = {},
                    modifier = Modifier.padding(horizontal = adaptiveHorizontalPadding)
                )
                Surface(tonalElevation = 2.dp) {
                    // NOTE: フォントサイズ大・狭幅でもボタンが詰まらないか要確認
                    SpriteActionPillsRow(
                        selectedTab = selectedTab,
                        coroutineScope = coroutineScope,
                        snackbarHostState = snackbarHostState,
                        devPreviewSettings = devPreviewSettings,
                        onAnimationApply = onAnimationApply,
                        onAnimationSave = onAnimationSave,
                        saveSpriteSheetConfig = ::saveSpriteSheetConfig,
                        copyAppliedSettings = ::copyAppliedSettings,
                        copySpriteSheetConfig = ::copySpriteSheetConfig,
                        actionButtonModifier = actionButtonModifier,
                        actionButtonPadding = actionButtonPadding,
                        actionButtonShape = actionButtonShape,
                        rowPadding = topBarActionRowPadding
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection) + adaptiveHorizontalPadding,
            top = innerPadding.calculateTopPadding(),
            end = innerPadding.calculateEndPadding(layoutDirection) + adaptiveHorizontalPadding,
            bottom = innerPadding.calculateBottomPadding()
        )

        Column(
            // [非dp] 縦横: 画面全体 の fillMaxSize(制約)に関係
            modifier = Modifier
                .fillMaxSize()
        ) {
            Surface(
                // [非dp] 縦: 画面全体 の weight(制約)に関係
                modifier = Modifier
                    .weight(1f)
                    // [非dp] 横: 画面全体 の fillMaxWidth(制約)に関係
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        // [非dp] 縦横: 画面全体 の fillMaxSize(制約)に関係
                        .fillMaxSize()
                        // [非dp] 四方向: Scaffold の insets(インセット)に関係
                        .padding(contentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    val displayedTabs = listOf(SpriteTab.ANIM, SpriteTab.ADJUST)
                    val displayedTabIndex = displayedTabs.indexOf(selectedTab).takeIf { it >= 0 } ?: 0

                    TabRow(
                        selectedTabIndex = displayedTabIndex,
                        modifier = Modifier
                            // [非dp] 横: TopAppBar の fillMaxWidth(制約)に関係
                            .fillMaxWidth()
                            // [dp] 縦: TopAppBar の最小サイズ(最小サイズ)に関係
                            .height(32.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[displayedTabIndex])
                                    // [dp] 横: TopAppBar の余白(余白)に関係
                                    .padding(horizontal = 6.dp),
                                height = 2.dp
                            )
                        },
                        divider = { HorizontalDivider(thickness = 0.5.dp) }
                    ) {
                        displayedTabs.forEach { tab ->
                            when (tab) {
                                SpriteTab.ANIM -> Tab(
                                    selected = selectedTab == SpriteTab.ANIM,
                                    onClick = {
                                        selectedTab = SpriteTab.ANIM
                                    },
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

                                SpriteTab.ADJUST -> Tab(
                                    selected = selectedTab == SpriteTab.ADJUST,
                                    onClick = {
                                        selectedTab = SpriteTab.ADJUST
                                    },
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
                            }
                        }
                    }
                    // TODO: TopBar統一後に本文側のアクション列は削除予定
                    SpriteActionPillsRow(
                        selectedTab = selectedTab,
                        coroutineScope = coroutineScope,
                        snackbarHostState = snackbarHostState,
                        devPreviewSettings = devPreviewSettings,
                        onAnimationApply = onAnimationApply,
                        onAnimationSave = onAnimationSave,
                        saveSpriteSheetConfig = ::saveSpriteSheetConfig,
                        copyAppliedSettings = ::copyAppliedSettings,
                        copySpriteSheetConfig = ::copySpriteSheetConfig,
                        actionButtonModifier = actionButtonModifier,
                        actionButtonPadding = actionButtonPadding,
                        actionButtonShape = actionButtonShape,
                        rowPadding = contentActionRowPadding
                    )
                    Box(
                        modifier = Modifier
                            // [非dp] 横: 画面全体 の fillMaxWidth(制約)に関係
                            .fillMaxWidth()
                            // [非dp] 縦: 画面全体 の weight(制約)に関係
                            .weight(1f, fill = true)
                    ) {
                        val animationTabContent: @Composable () -> Unit = {
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
                                devUnlocked = devUnlocked,
                                devSettings = devPreviewSettings,
                                onDevSettingsChange = { updated -> devPreviewSettings = updated },
                                initialHeaderLeftXOffsetDp = initialHeaderLeftXOffsetDp
                            )
                        }

                        when (selectedTab) {
                            SpriteTab.ANIM -> animationTabContent()

                            SpriteTab.ADJUST -> {
                                val previewHeaderText = "${imageBitmap.width}×${imageBitmap.height} / ${"%.2f".format(displayScale)}x"
                                val coordinateText =
                                    selectedPosition?.let { position ->
                                        "座標: ${position.x},${position.y},${boxSizePx},${boxSizePx}"
                                    } ?: "座標: -, -, -, -"
                                Column(
                                    modifier = Modifier
                                        // [非dp] 縦横: プレビュー の fillMaxSize(制約)に関係
                                        .fillMaxSize()
                                        // [非dp] 縦: プレビュー の verticalScroll(制約)に関係
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    SpritePreviewBlock(
                                        imageBitmap = imageBitmap,
                                        modifier = Modifier
                                            // [非dp] 横: プレビュー の fillMaxWidth(制約)に関係
                                            .fillMaxWidth()
                                            // [dp] 上: プレビュー の余白(余白)に関係
                                            .padding(top = 6.dp),
                                        line1Text = previewHeaderText,
                                        line2Text = "選択中: ${selectedNumber}/9 | サイズ: ${boxSizePx}px | $coordinateText",
                                        isImeVisible = imeVisible,
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
                                    // [dp] 縦: プレビュー の間隔(間隔)に関係
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
    devUnlocked: Boolean,
    devSettings: DevPreviewSettings,
    onDevSettingsChange: (DevPreviewSettings) -> Unit,
    initialHeaderLeftXOffsetDp: Int?,
) {
    val configuration = LocalConfiguration.current
    val selectedAnimation = selectionState.selectedAnimation
    val lazyListState = rememberLazyListState()
    val layoutState = rememberReadyPreviewLayoutState(
        devSettings = devSettings,
        onDevSettingsChange = onDevSettingsChange
    )
    LaunchedEffect(initialHeaderLeftXOffsetDp) {
        initialHeaderLeftXOffsetDp?.let { initial ->
            layoutState.headerLeftXOffsetDp = initial.coerceIn(-layoutState.headerOffsetLimitDp, layoutState.headerOffsetLimitDp)
        }
    }
    // [dp] 縦: プレビュー の最小サイズ(最小サイズ)に関係
    val baseMaxHeightDp = if (isImeVisible) 220 else 300
    val customCardMaxHeightDp = layoutState.cardMaxHeightDp.takeUnless { it == 0 }
    val effectiveCardMaxH: Int? = customCardMaxHeightDp ?: baseMaxHeightDp
    val boundedMinHeightDp = effectiveCardMaxH?.let { max -> layoutState.cardMinHeightDp.coerceAtMost(max) } ?: layoutState.cardMinHeightDp
    val effectiveMinHeightDp = effectiveCardMaxH?.let { max -> boundedMinHeightDp.coerceAtMost(max) } ?: boundedMinHeightDp
    val effectiveDetailsMaxH = layoutState.detailsMaxHeightDp.coerceAtLeast(1)

    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val heightScale = (configuration.screenHeightDp / 800f).coerceIn(0.85f, 1.15f)
    fun scaledInt(value: Int): Int = (value * heightScale).roundToInt()
    val readyPreviewUiState = ReadyPreviewUiState(
        charYOffsetDp = scaledInt(layoutState.charYOffsetDp),
        effectiveMinHeightDp = effectiveMinHeightDp,
        effectiveCardMaxH = effectiveCardMaxH,
        infoXOffsetDp = layoutState.infoXOffsetDp,
        infoYOffsetDp = scaledInt(layoutState.infoYOffsetDp),
        headerLeftXOffsetDp = layoutState.headerLeftXOffsetDp,
        headerLeftYOffsetDp = layoutState.headerLeftYOffsetDp,
        headerRightXOffsetDp = layoutState.headerRightXOffsetDp,
        headerRightYOffsetDp = layoutState.headerRightYOffsetDp,
        baseMaxHeightDp = baseMaxHeightDp,
        effectiveDetailsMaxH = effectiveDetailsMaxH,
        outerBottomDp = scaledInt(layoutState.outerBottomDp),
        innerBottomDp = scaledInt(layoutState.innerBottomDp),
        innerVPadDp = scaledInt(layoutState.innerVPadDp),
        detailsMaxHeightDp = scaledInt(layoutState.detailsMaxHeightDp),
        cardMaxHeightDp = layoutState.cardMaxHeightDp,
        cardMinHeightDp = layoutState.cardMinHeightDp,
        detailsMaxLines = layoutState.detailsMaxLines,
        headerOffsetLimitDp = layoutState.headerOffsetLimitDp,
        headerSpacerDp = scaledInt(layoutState.headerSpacerDp),
        bodySpacerDp = scaledInt(layoutState.bodySpacerDp),
    )
    val layoutDirection = LocalLayoutDirection.current
    val imeBottomPaddingDp = with(density) {
        imeBottomPx.toDp()
    }
    val listTopExtraPadding = if (configuration.screenHeightDp < 660) 8.dp else 12.dp
    val imeExtraPadding = if (isImeVisible) 14.dp else 0.dp
    // [非dp] 下: IME の insets(インセット)に関係
    val bottomContentPadding = if (isImeVisible) {
        contentPadding.calculateBottomPadding() + imeBottomPaddingDp + imeExtraPadding
    } else {
        contentPadding.calculateBottomPadding()
    }
    // [dp] 四方向: リスト の余白(余白)に関係
    val listContentPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        top = contentPadding.calculateTopPadding() + listTopExtraPadding,
        end = contentPadding.calculateEndPadding(layoutDirection),
        bottom = bottomContentPadding
    )

    Column(
        modifier = Modifier
            // [非dp] 縦横: 画面全体 の fillMaxSize(制約)に関係
            .fillMaxSize()
    ) {
        Surface(
            // [非dp] 横: プレビュー の fillMaxWidth(制約)に関係
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
                previewUiState = readyPreviewUiState
            )
        }
        // [dp] 縦: プレビュー の間隔(間隔)に関係
        Spacer(modifier = Modifier.height(6.dp))
        LazyColumn(
            modifier = Modifier
                // [非dp] 縦: リスト の weight(制約)に関係
                .weight(1f)
                // [非dp] 横: リスト の fillMaxWidth(制約)に関係
                .fillMaxWidth(),
            state = lazyListState,
            // [dp] 縦: リスト の間隔(間隔)に関係
            verticalArrangement = Arrangement.spacedBy(10.dp),
            // [dp] 四方向: リスト の余白(余白)に関係
            contentPadding = listContentPadding
        ) {
            item {
                Column(
                    modifier = Modifier
                        // [非dp] 横: リスト の fillMaxWidth(制約)に関係
                        .fillMaxWidth(),
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
                        // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = baseState.frameInput,
                        onValueChange = baseState.onFrameInputChange,
                        modifier = Modifier
                            // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                            .fillMaxWidth(),
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
                            .fillMaxWidth(),
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
                        verticalAlignment = Alignment.CenterVertically,
                        // [非dp] 横: 入力欄 の SpaceBetween(間隔)に関係
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
            }
            item {
                AnimatedVisibility(visible = insertionState.enabled) {
                    @OptIn(ExperimentalFoundationApi::class)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = insertionState.frameInput,
                            onValueChange = insertionState.onFrameInputChange,
                            modifier = Modifier
                                // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                                .fillMaxWidth(),
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
                                // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                                .fillMaxWidth(),
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
                                // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                                .fillMaxWidth(),
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
                                .fillMaxWidth(),
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
                                .fillMaxWidth(),
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
                            verticalAlignment = Alignment.CenterVertically,
                            // [非dp] 横: 入力欄 の SpaceBetween(間隔)に関係
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                // [非dp] 横: 入力欄 の weight(制約)に関係
                                modifier = Modifier.weight(1f)
                            ) {
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
            item {
                DevMenuSectionHost(
                    devUnlocked = devUnlocked,
                    layoutState = layoutState,
                    previewUiState = readyPreviewUiState
                )
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
                // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
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
    var currentFramePosition by remember(playbackFrames) { mutableStateOf(0) }
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
            // [dp] 縦横: プレビュー の最小サイズ(最小サイズ)に関係
            .size(spriteSizeDp)
            // [dp] 上下: プレビュー の余白(余白)に関係
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
    // [dp] 縦: プレビュー の間隔(間隔)に関係
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
    previewUiState: ReadyPreviewUiState,
    devMenuContent: (@Composable () -> Unit)? = null,
) {
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
                // [dp] 縦横: プレビュー の最小サイズ(最小サイズ)に関係
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
                            charYOffsetDp = previewUiState.charYOffsetDp,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                // [dp] 左上: プレビュー の余白(余白)に関係
                                .padding(
                                    start = contentHorizontalPadding,
                                    top = previewUiState.innerVPadDp.dp + previewUiState.headerSpacerDp.dp
                                )
                        )
                    },
                    info = {
                        ReadyAnimationInfo(
                            state = previewState,
                            summary = baseSummary,
                            insertionSummary = insertionSummary,
                            insertionPreviewValues = insertionPreviewValues,
                            insertionEnabled = insertionEnabled,
                            infoYOffsetDp = previewUiState.infoYOffsetDp,
                            modifier = Modifier
                                // [非dp] 横: プレビュー の fillMaxWidth(制約)に関係
                                .fillMaxWidth()
                                .offset(
                                    x = previewUiState.headerLeftXOffsetDp.dp,
                                    y = previewUiState.headerLeftYOffsetDp.dp
                                )
                                // [dp] 左: プレビュー の余白(余白)に関係
                                .padding(
                                    start = spriteSize + (spriteSize * 0.08f).coerceIn(4.dp, 6.dp)
                                )
                                // [dp] 左右: プレビュー の余白(余白)に関係
                                .offset(x = previewUiState.infoXOffsetDp.dp)
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

@Composable
private fun SpritePreviewBlock(
    imageBitmap: ImageBitmap,
    line1Text: String,
    line2Text: String,
    isImeVisible: Boolean,
    modifier: Modifier = Modifier,
    onContainerSizeChanged: ((IntSize) -> Unit)? = null,
    overlayContent: @Composable BoxScope.() -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        // [dp] 縦: プレビュー の間隔(間隔)に関係
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val minHeight = if (isImeVisible) 160.dp else 200.dp
        val configuration = LocalConfiguration.current
        val aspectRatio = min(
            1f,
            configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
        ).coerceAtLeast(0.7f)
        Box(
            modifier = Modifier
                // [非dp] 横: プレビュー の fillMaxWidth(制約)に関係
                .fillMaxWidth()
                // [非dp] 縦横: プレビュー の aspectRatio(制約)に関係
                .aspectRatio(aspectRatio)
                // [dp] 縦: プレビュー の最小サイズ(最小サイズ)に関係
                .heightIn(min = minHeight),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Sprite Preview",
                modifier = Modifier
                    // [非dp] 縦横: プレビュー の fillMaxSize(制約)に関係
                    .fillMaxSize()
                    .onSizeChanged { newSize -> onContainerSizeChanged?.invoke(newSize) },
                contentScale = ContentScale.Fit
            )
            overlayContent()
        }
        val infoTextStyle = MaterialTheme.typography.labelMedium.copy(
            lineHeight = MaterialTheme.typography.labelMedium.fontSize
        )
        val textHorizontalPadding = maxOf(0.dp, minOf(4.dp, configuration.screenWidthDp.dp * 0.01f))
        Column(
            modifier = Modifier
                // [非dp] 横: プレビュー の fillMaxWidth(制約)に関係
                .fillMaxWidth()
                // [dp] 左右: プレビュー の余白(余白)に関係
                .padding(horizontal = textHorizontalPadding),
            // [dp] 縦: プレビュー の間隔(間隔)に関係
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
            // [非dp] 横: 画面全体 の fillMaxWidth(制約)に関係
            .fillMaxWidth()
            // [dp] 四方向: 画面全体 の余白(余白)に関係
            .padding(horizontal = 16.dp, vertical = 12.dp),
        // [dp] 縦: 画面全体 の間隔(間隔)に関係
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val buttonModifier = Modifier
            // [非dp] 横: 画面全体 の weight(制約)に関係
            .weight(1f)
            // [dp] 縦: 画面全体 の最小サイズ(最小サイズ)に関係
            .height(buttonHeight)

        val navigatorButtonColors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF6A00FF),
            contentColor = Color.White
        )
        val defaultControlButtonColors = ButtonDefaults.filledTonalButtonColors()

        Column(
            // [dp] 縦: 画面全体 の間隔(間隔)に関係
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                // [非dp] 横: 画面全体 の fillMaxWidth(制約)に関係
                modifier = Modifier.fillMaxWidth(),
                // [dp] 横: 画面全体 の間隔(間隔)に関係
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
                // [非dp] 横: 画面全体 の fillMaxWidth(制約)に関係
                modifier = Modifier.fillMaxWidth(),
                // [dp] 横: 画面全体 の間隔(間隔)に関係
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

@Composable
private fun SpriteActionPillsRow(
    selectedTab: SpriteTab,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    devPreviewSettings: DevPreviewSettings,
    onAnimationApply: () -> Unit,
    onAnimationSave: () -> Unit,
    saveSpriteSheetConfig: () -> Unit,
    copyAppliedSettings: (DevPreviewSettings) -> Unit,
    copySpriteSheetConfig: () -> Unit,
    actionButtonModifier: Modifier,
    actionButtonPadding: PaddingValues,
    actionButtonShape: Shape,
    rowPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            // [非dp] 横: 画面全体 の fillMaxWidth(制約)に関係
            .fillMaxWidth()
            // [dp] 縦横: 画面全体 の余白(余白)に関係
            .padding(rowPadding),
        // [dp] 横: 画面全体 の間隔(間隔)に関係
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(
            modifier = actionButtonModifier,
            onClick = {
                when (selectedTab) {
                    SpriteTab.ANIM -> onAnimationApply()
                    SpriteTab.ADJUST -> coroutineScope.launch { snackbarHostState.showSnackbar("プレビューに適用しました") }
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
                when (selectedTab) {
                    SpriteTab.ANIM -> onAnimationSave()
                    SpriteTab.ADJUST -> saveSpriteSheetConfig()
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
                when (selectedTab) {
                    SpriteTab.ANIM -> copyAppliedSettings(devPreviewSettings)
                    SpriteTab.ADJUST -> copySpriteSheetConfig()
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

internal fun DevPreviewSettings.toJsonObject(): JSONObject =
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

/*
TODO: 余白候補一覧（SpriteSettingsScreen.kt）
- [非dp] 上: Scaffold / システムバー (インセット) … L1255
- [非dp] 下: IME / リスト の contentPadding(インセット) … L1903
- [dp] 縦: TopAppBar の余白 IconButton padding(余白) … L1287
- [dp] 縦: リスト の項目間隔 Arrangement.spacedBy(12.dp)(間隔) … L1946
- [非dp] 下: カード の outerBottomDp padding(余白) … L2413
- [dp] 左右: プレビュー の contentHorizontalPadding/padding(余白) … L2470
- [非dp] 縦: プレビュー の aspectRatio/heightIn(制約) … L2554
- [dp] 縦: 開発メニュー の Spacer(間隔) … L2530
*/
