package com.sonusid.ollama.ui.screens.settings

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavController
import com.sonusid.ollama.BuildConfig
import com.sonusid.ollama.R
import com.sonusid.ollama.data.SpriteSheetConfig
import com.sonusid.ollama.data.boxesWithInternalIndex
import com.sonusid.ollama.data.isUninitialized
import com.sonusid.ollama.data.toInternalFrameIndex
import com.sonusid.ollama.data.BoxPosition as SpriteSheetBoxPosition
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.ui.components.ReadyPreviewLayoutState
import com.sonusid.ollama.ui.components.ReadyPreviewSlot
import com.sonusid.ollama.ui.components.SpriteFrameRegion
import com.sonusid.ollama.ui.components.DevMenuSectionHost
import com.sonusid.ollama.ui.components.drawFramePlaceholder
import com.sonusid.ollama.ui.components.drawFrameRegion
import com.sonusid.ollama.ui.components.rememberNightSpriteColorFilterForDarkTheme
import com.sonusid.ollama.ui.components.rememberReadyPreviewLayoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random
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

private enum class AnimationType(val internalKey: String, val displayLabel: String) {
    READY("Ready", "Ready"),
    TALKING("Talking", "Speaking"),
    IDLE("Idle", "Idle"),
    THINKING("Thinking", "Thinking"),
    TALK_SHORT("TalkShort", "TalkShort"),
    TALK_LONG("TalkLong", "TalkLong"),
    TALK_CALM("TalkCalm", "TalkCalm"),
    ERROR_LIGHT("ErrorLight", "ErrorLight"),
    ERROR_HEAVY("ErrorHeavy", "ErrorHeavy"),
    OFFLINE_ENTER("OfflineEnter", "OfflineEnter"),
    OFFLINE_LOOP("OfflineLoop", "OfflineLoop"),
    OFFLINE_EXIT("OfflineExit", "OfflineExit");

    companion object {
        val options = listOf(
            READY,
            TALKING,
            IDLE,
            THINKING,
            TALK_SHORT,
            TALK_LONG,
            TALK_CALM,
            ERROR_LIGHT,
            ERROR_HEAVY,
            OFFLINE_ENTER,
            OFFLINE_LOOP,
            OFFLINE_EXIT,
        )

        fun fromInternalKeyOrNull(key: String?): AnimationType? {
            if (key.isNullOrBlank()) return null
            return options.firstOrNull { it.internalKey == key }
        }
    }

    val supportsPersistence: Boolean
        get() = this == READY || this == TALKING
}

private enum class SpriteTab {
    ANIM,
    ADJUST,
}

private data class AnimationDefaults(
    val base: ReadyAnimationSettings,
    val insertion: InsertionAnimationSettings,
)

private data class AllAnimations(
    val animations: Map<AnimationType, AnimationDefaults>,
)

private data class AnimationInputState(
    val frameInput: String,
    val intervalInput: String,
    val framesError: String?,
    val intervalError: String?,
    val insertionPattern1FramesInput: String,
    val insertionPattern1WeightInput: String,
    val insertionPattern1IntervalInput: String,
    val insertionPattern2FramesInput: String,
    val insertionPattern2WeightInput: String,
    val insertionPattern2IntervalInput: String,
    val insertionIntervalInput: String,
    val insertionEveryNInput: String,
    val insertionProbabilityInput: String,
    val insertionCooldownInput: String,
    val insertionEnabled: Boolean,
    val insertionExclusive: Boolean,
    val insertionPattern1FramesError: String?,
    val insertionPattern1WeightError: String?,
    val insertionPattern1IntervalError: String?,
    val insertionPattern2FramesError: String?,
    val insertionPattern2WeightError: String?,
    val insertionPattern2IntervalError: String?,
    val insertionIntervalError: String?,
    val insertionEveryNError: String?,
    val insertionProbabilityError: String?,
    val insertionCooldownError: String?,
    val appliedBase: ReadyAnimationSettings,
    val appliedInsertion: InsertionAnimationSettings,
)

// 暫定: statusAnimationMap に近い値をここで簡易マッピングする。
private val extraAnimationDefaults: Map<AnimationType, AnimationDefaults> = mapOf(
    AnimationType.IDLE to AnimationDefaults(
        base = ReadyAnimationSettings(frameSequence = listOf(0, 8, 0, 5, 0), intervalMs = 490),
        insertion = InsertionAnimationSettings(
            enabled = true,
            patterns = listOf(InsertionPattern(frameSequence = listOf(0, 0, 8, 0))),
            intervalMs = 490,
            everyNLoops = 8,
            probabilityPercent = 100,
            cooldownLoops = 0,
            exclusive = false,
        ),
    ),
    AnimationType.THINKING to AnimationDefaults(
        base = ReadyAnimationSettings.THINKING_DEFAULT,
        insertion = InsertionAnimationSettings.THINKING_DEFAULT,
    ),
    AnimationType.TALK_SHORT to AnimationDefaults(
        base = ReadyAnimationSettings(frameSequence = listOf(0, 6, 2, 6, 0), intervalMs = 130),
        insertion = InsertionAnimationSettings.TALKING_DEFAULT.copy(
            enabled = false,
            patterns = listOf(InsertionPattern(frameSequence = listOf(0, 6, 2, 6, 0))),
            intervalMs = 130,
        ),
    ),
    AnimationType.TALK_LONG to AnimationDefaults(
        base = ReadyAnimationSettings(frameSequence = listOf(0, 4, 6, 4, 4, 6, 4, 0), intervalMs = 190),
        insertion = InsertionAnimationSettings(
            enabled = true,
            patterns = listOf(InsertionPattern(frameSequence = listOf(1))),
            intervalMs = 190,
            everyNLoops = 2,
            probabilityPercent = 100,
            cooldownLoops = 0,
            exclusive = true,
        ),
    ),
    AnimationType.TALK_CALM to AnimationDefaults(
        base = ReadyAnimationSettings(frameSequence = listOf(7, 4, 7, 8, 7), intervalMs = 280),
        insertion = InsertionAnimationSettings.TALKING_DEFAULT.copy(
            enabled = false,
            patterns = listOf(InsertionPattern(frameSequence = listOf(7, 4, 7, 8, 7))),
            intervalMs = 280,
        ),
    ),
    AnimationType.ERROR_LIGHT to AnimationDefaults(
        base = ReadyAnimationSettings(frameSequence = listOf(5, 7, 5), intervalMs = 390),
        insertion = InsertionAnimationSettings.TALKING_DEFAULT.copy(
            enabled = false,
            patterns = listOf(InsertionPattern(frameSequence = listOf(5, 7, 5))),
            intervalMs = 390,
        ),
    ),
    AnimationType.ERROR_HEAVY to AnimationDefaults(
        base = ReadyAnimationSettings(frameSequence = listOf(5, 5, 5, 7, 5), intervalMs = 400),
        insertion = InsertionAnimationSettings(
            enabled = true,
            patterns = listOf(InsertionPattern(frameSequence = listOf(2))),
            intervalMs = 400,
            everyNLoops = 6,
            probabilityPercent = 100,
            cooldownLoops = 0,
            exclusive = true,
        ),
    ),
    AnimationType.OFFLINE_ENTER to AnimationDefaults(
        base = ReadyAnimationSettings(frameSequence = listOf(0, 8, 8), intervalMs = 1_250),
        insertion = InsertionAnimationSettings.TALKING_DEFAULT.copy(
            enabled = false,
            patterns = listOf(InsertionPattern(frameSequence = listOf(0, 8, 8))),
            intervalMs = 1_250,
        ),
    ),
    AnimationType.OFFLINE_LOOP to AnimationDefaults(
        base = ReadyAnimationSettings(frameSequence = listOf(8, 8), intervalMs = 1_250),
        insertion = InsertionAnimationSettings.TALKING_DEFAULT.copy(
            enabled = false,
            patterns = listOf(InsertionPattern(frameSequence = listOf(8, 8))),
            intervalMs = 1_250,
        ),
    ),
    AnimationType.OFFLINE_EXIT to AnimationDefaults(
        base = ReadyAnimationSettings(frameSequence = listOf(8, 0), intervalMs = 1_250),
        insertion = InsertionAnimationSettings.TALKING_DEFAULT.copy(
            enabled = false,
            patterns = listOf(InsertionPattern(frameSequence = listOf(8, 0))),
            intervalMs = 1_250,
        ),
    ),
)

private fun List<Int>.toFrameInputText(): String =
    joinToString(separator = ",") { value -> (value + 1).toString() }

private data class InsertionPatternInputs(
    val pattern1FramesInput: String,
    val pattern1WeightInput: String,
    val pattern1IntervalInput: String,
    val pattern2FramesInput: String,
    val pattern2WeightInput: String,
    val pattern2IntervalInput: String,
)

private fun List<InsertionPattern>.toInsertionPatternInputs(): InsertionPatternInputs {
    val pattern1 = getOrNull(0)
    val pattern2 = getOrNull(1)
    return InsertionPatternInputs(
        pattern1FramesInput = pattern1?.frameSequence?.toFrameInputText().orEmpty(),
        pattern1WeightInput = (pattern1?.weight ?: 1).toString(),
        pattern1IntervalInput = pattern1?.intervalMs?.toString().orEmpty(),
        pattern2FramesInput = pattern2?.frameSequence?.toFrameInputText().orEmpty(),
        pattern2WeightInput = (pattern2?.weight ?: 0).toString(),
        pattern2IntervalInput = pattern2?.intervalMs?.toString().orEmpty(),
    )
}

private fun AnimationDefaults.toInputState(): AnimationInputState =
    insertion.patterns.toInsertionPatternInputs().let { inputs ->
        AnimationInputState(
            frameInput = base.frameSequence.toFrameInputText(),
            intervalInput = base.intervalMs.toString(),
            framesError = null,
            intervalError = null,
            insertionPattern1FramesInput = inputs.pattern1FramesInput,
            insertionPattern1WeightInput = inputs.pattern1WeightInput,
            insertionPattern1IntervalInput = inputs.pattern1IntervalInput,
            insertionPattern2FramesInput = inputs.pattern2FramesInput,
            insertionPattern2WeightInput = inputs.pattern2WeightInput,
            insertionPattern2IntervalInput = inputs.pattern2IntervalInput,
            insertionIntervalInput = insertion.intervalMs.toString(),
            insertionEveryNInput = insertion.everyNLoops.toString(),
            insertionProbabilityInput = insertion.probabilityPercent.toString(),
            insertionCooldownInput = insertion.cooldownLoops.toString(),
            insertionEnabled = insertion.enabled,
            insertionExclusive = insertion.exclusive,
            insertionPattern1FramesError = null,
            insertionPattern1WeightError = null,
            insertionPattern1IntervalError = null,
            insertionPattern2FramesError = null,
            insertionPattern2WeightError = null,
            insertionPattern2IntervalError = null,
            insertionIntervalError = null,
            insertionEveryNError = null,
            insertionProbabilityError = null,
            insertionCooldownError = null,
            appliedBase = base,
            appliedInsertion = insertion,
        )
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
    val pattern1WeightText: String,
    val pattern2WeightText: String,
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
    pattern1FramesInput: String,
    pattern1WeightInput: String,
    pattern2FramesInput: String,
    pattern2WeightInput: String,
    intervalInput: String,
    everyNInput: String,
    probabilityInput: String,
    cooldownInput: String,
    exclusive: Boolean,
    defaultIntervalMs: Int,
    frameCount: Int
): Pair<AnimationSummary, InsertionPreviewValues> {
    val framesText = pattern1FramesInput.trim()
    val pattern2FramesText = pattern2FramesInput.trim()
    val primaryFramesText = framesText.ifEmpty { pattern2FramesText }
    val intervalText = intervalInput.trim()
    val everyNText = everyNInput.trim()
    val probabilityText = probabilityInput.trim()
    val cooldownText = cooldownInput.trim()
    val pattern1WeightText = if (framesText.isEmpty()) {
        "0"
    } else {
        pattern1WeightInput.trim().ifEmpty { "1" }
    }
    val pattern2WeightText = if (pattern2FramesText.isEmpty()) {
        "0"
    } else {
        pattern2WeightInput.trim().ifEmpty { "1" }
    }

    val previewValues = InsertionPreviewValues(
        framesText = primaryFramesText.ifEmpty { "-" },
        intervalText = intervalText.ifEmpty { "-" },
        everyNText = everyNText.ifEmpty { "-" },
        probabilityText = probabilityText.ifEmpty { "-" },
        cooldownText = cooldownText.ifEmpty { "-" },
        exclusiveText = if (exclusive) "ON" else "OFF",
        pattern1WeightText = pattern1WeightText,
        pattern2WeightText = pattern2WeightText,
    )

    val primaryPatternText = primaryFramesText
        .replace("｜", "|")
        .replace("，", ",")
        .replace("、", ",")
        .split("|")
        .map { token -> token.trim() }
        .firstOrNull { token -> token.isNotEmpty() }
        .orEmpty()
    val parsedFrames = primaryPatternText
        .split(",")
        .map { token -> token.trim() }
        .filter { token -> token.isNotEmpty() }
        .mapNotNull { token ->
            token.toIntOrNull()
                ?.takeIf { value -> value in 1..frameCount }
                ?.minus(1)
        }
    val frames = parsedFrames.ifEmpty { listOf(0) }
    val intervalMs = intervalText.toIntOrNull()?.takeIf { it > 0 } ?: defaultIntervalMs
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

private fun buildEffectiveInsertionIntervalText(
    patterns: List<InsertionPattern>,
    defaultIntervalMs: Int,
): String {
    // 実効周期は候補パターンの interval とデフォルト値の組み合わせで表示する
    val defaultInterval = defaultIntervalMs.coerceAtLeast(0)
    val candidates = patterns.filter { pattern ->
        pattern.weight > 0 && pattern.frameSequence.isNotEmpty()
    }
    if (candidates.isEmpty()) {
        return "${defaultInterval}ms"
    }
    val resolvedIntervals = candidates.map { pattern ->
        (pattern.intervalMs ?: defaultInterval).coerceAtLeast(0)
    }
    val minInterval = resolvedIntervals.minOrNull() ?: defaultInterval
    val maxInterval = resolvedIntervals.maxOrNull() ?: defaultInterval
    return if (minInterval == maxInterval) {
        "${minInterval}ms"
    } else {
        "${minInterval}〜${maxInterval}ms"
    }
}

private fun selectWeightedInsertionPattern(
    patterns: List<InsertionPattern>,
    random: Random,
): Pair<Int, InsertionPattern>? {
    val candidates = patterns.withIndex().filter { (_, pattern) ->
        pattern.weight > 0 && pattern.frameSequence.isNotEmpty()
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

private const val DEFAULT_BOX_SIZE_PX = 88
internal const val INFO_X_OFFSET_MIN = -500
internal const val INFO_X_OFFSET_MAX = 500
private const val ALL_ANIMATIONS_JSON_VERSION = 1
private const val ALL_ANIMATIONS_READY_KEY = "Ready"
private const val ALL_ANIMATIONS_TALKING_KEY = "Talking"
private const val ALL_ANIMATIONS_THINKING_KEY = "Thinking"
private const val JSON_VERSION_KEY = "version"
private const val JSON_ANIMATIONS_KEY = "animations"
private const val JSON_BASE_KEY = "base"
private const val JSON_INSERTION_KEY = "insertion"
private const val JSON_ENABLED_KEY = "enabled"
private const val JSON_PATTERNS_KEY = "patterns"
private const val JSON_FRAMES_KEY = "frames"
private const val JSON_WEIGHT_KEY = "weight"
private const val JSON_INTERVAL_MS_KEY = "intervalMs"
private const val JSON_PATTERN_INTERVAL_MS_KEY = "intervalMs"
private const val JSON_EVERY_N_LOOPS_KEY = "everyNLoops"
private const val JSON_PROBABILITY_PERCENT_KEY = "probabilityPercent"
private const val JSON_COOLDOWN_LOOPS_KEY = "cooldownLoops"
private const val JSON_EXCLUSIVE_KEY = "exclusive"
private const val READY_LEGACY_LABEL = "ReadyBlink"
private const val UNSET_SPRITE_TAB = "__UNSET__"

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
    val context = LocalContext.current
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = context) {
        // 画像デコードは重いため、遷移直後の白ブランクを避ける目的で非同期ロードする
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeResource(context.resources, R.drawable.lami_sprite_3x3_288)
        }?.asImageBitmap()
    }
    val imageWidth = imageBitmap?.width ?: 0
    val imageHeight = imageBitmap?.height ?: 0
    val safeImageWidth = imageWidth.coerceAtLeast(1)
    val safeImageHeight = imageHeight.coerceAtLeast(1)
    val defaultSpriteSheetConfig = remember { SpriteSheetConfig.default3x3() }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val successSnackbarDurationMs = 1200L
    val errorSnackbarDurationMs = 1800L
    val settingsPreferences = remember(context.applicationContext) {
        SettingsPreferences(context.applicationContext)
    }

    LaunchedEffect(Unit) {
        // 戻る履歴/再起動時の復元のため、表示開始時に最後の画面を保存する
        settingsPreferences.saveLastRoute(Routes.SPRITE_SETTINGS)
    }

    fun showTopSnackbar(message: String, isError: Boolean) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val dismissDelayMs = if (isError) errorSnackbarDurationMs else successSnackbarDurationMs
            val dismissJob = launch {
                delay(dismissDelayMs)
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            snackbarHostState
                .showSnackbar(
                    message = message,
                    actionLabel = if (isError) "ERROR" else null,
                    duration = SnackbarDuration.Indefinite
                )
            dismissJob.cancel()
        }
    }

    fun showTopSnackbarSuccess(message: String) {
        showTopSnackbar(message = message, isError = false)
    }

    fun showTopSnackbarError(message: String) {
        showTopSnackbar(message = message, isError = true)
    }
    val spriteSheetConfigJson by settingsPreferences.spriteSheetConfigJson.collectAsState(initial = null)
    val spriteSheetConfig by settingsPreferences.spriteSheetConfig.collectAsState(initial = defaultSpriteSheetConfig)
    val spriteAnimationsJson by settingsPreferences.spriteAnimationsJson.collectAsState(initial = null)
    val lastSelectedAnimationKey by settingsPreferences.lastSelectedAnimation.collectAsState(initial = null)
    // 段階移行：SPEAKING（Talk系）は新DataStoreの選択キーを優先して復元する
    val selectedSpeakingKey by settingsPreferences
        .selectedKeyFlow(SpriteState.SPEAKING)
        .collectAsState(initial = null)
    // 段階移行：READYは新DataStoreの選択キーを優先して復元する
    val selectedReadyKey by settingsPreferences
        .selectedKeyFlow(SpriteState.READY)
        .collectAsState(initial = null)
    // 段階移行：IDLEは新DataStoreの選択キーを優先して復元する
    val selectedIdleKey by settingsPreferences
        .selectedKeyFlow(SpriteState.IDLE)
        .collectAsState(initial = null)
    // 段階移行：THINKINGは新DataStoreの選択キーを優先して復元する
    val selectedThinkingKey by settingsPreferences
        .selectedKeyFlow(SpriteState.THINKING)
        .collectAsState(initial = null)
    val lastSelectedSpriteTabKey by settingsPreferences.lastSelectedSpriteTab
        .collectAsState(initial = UNSET_SPRITE_TAB)
    val lastSelectedBoxNumberKey by settingsPreferences.lastSelectedBoxNumber.collectAsState(initial = null)
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
    val readyAnimationSettings by settingsPreferences.readyAnimationSettings.collectAsState(
        initial = ReadyAnimationSettings.READY_DEFAULT
    )
    val talkingAnimationSettings by settingsPreferences.talkingAnimationSettings.collectAsState(
        initial = ReadyAnimationSettings.TALKING_DEFAULT
    )
    val readyInsertionAnimationSettings by settingsPreferences.readyInsertionAnimationSettings.collectAsState(
        initial = InsertionAnimationSettings.READY_DEFAULT
    )
    val talkingInsertionAnimationSettings by settingsPreferences.talkingInsertionAnimationSettings.collectAsState(
        initial = InsertionAnimationSettings.TALKING_DEFAULT
    )
    var selectedNumber by rememberSaveable { mutableStateOf(1) }
    var boxSizePx by rememberSaveable { mutableStateOf(DEFAULT_BOX_SIZE_PX) }
    var boxPositions by rememberSaveable(stateSaver = boxPositionsSaver()) { mutableStateOf(defaultBoxPositions()) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var displayScale by remember { mutableStateOf(1f) }
    var selectedTab by rememberSaveable { mutableStateOf(SpriteTab.ANIM) }
    var didRestoreTab by remember {
        // 保存/復元の順序を保証するため、復元試行の完了フラグを保持する
        mutableStateOf(false)
    }
    var didRestoreAdjustSelection by remember {
        // 画像調整の保存/復元順序を保証するため、復元試行の完了フラグを保持する
        mutableStateOf(false)
    }
    val devUnlocked = true
    var readyFrameInput by rememberSaveable { mutableStateOf("1,1,1,1") }
    var readyIntervalInput by rememberSaveable { mutableStateOf("90") }
    var appliedReadyFrames by rememberSaveable { mutableStateOf(listOf(0, 0, 0, 0)) }
    var appliedReadyIntervalMs by rememberSaveable { mutableStateOf(90) }
    var readyFramesError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyIntervalError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingFrameInput by rememberSaveable { mutableStateOf("1,2,3,2") }
    var talkingIntervalInput by rememberSaveable { mutableStateOf("700") }
    LaunchedEffect(imageBitmap, containerSize) {
        // delegated property のため smart cast 不可→ローカル束縛
        val bmp = imageBitmap
        if (bmp != null && containerSize.width > 0) {
            displayScale = containerSize.width / bmp.width.toFloat()
        }
    }
    var appliedTalkingFrames by rememberSaveable { mutableStateOf(listOf(0, 1, 2, 1)) }
    var appliedTalkingIntervalMs by rememberSaveable { mutableStateOf(700) }
    var talkingFramesError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingIntervalError by rememberSaveable { mutableStateOf<String?>(null) }
    // Ready insertion のUI初期値を新デフォルトに合わせる
    var readyInsertionPattern1FramesInput by rememberSaveable { mutableStateOf("5,0") }
    var readyInsertionPattern1WeightInput by rememberSaveable { mutableStateOf("3") }
    var readyInsertionPattern1IntervalInput by rememberSaveable { mutableStateOf("110") }
    var readyInsertionPattern2FramesInput by rememberSaveable { mutableStateOf("5,0,5,0,0") }
    var readyInsertionPattern2WeightInput by rememberSaveable { mutableStateOf("1") }
    var readyInsertionPattern2IntervalInput by rememberSaveable { mutableStateOf("80") }
    var readyInsertionIntervalInput by rememberSaveable { mutableStateOf("120") }
    var readyInsertionEveryNInput by rememberSaveable { mutableStateOf("5") }
    var readyInsertionProbabilityInput by rememberSaveable { mutableStateOf("58") }
    var readyInsertionCooldownInput by rememberSaveable { mutableStateOf("4") }
    var readyInsertionEnabled by rememberSaveable { mutableStateOf(true) }
    var readyInsertionExclusive by rememberSaveable { mutableStateOf(false) }
    var appliedReadyInsertionPatterns by remember {
        mutableStateOf(
            listOf(
                InsertionPattern(frameSequence = listOf(5, 0), weight = 3, intervalMs = 110),
                InsertionPattern(frameSequence = listOf(5, 0, 5, 0, 0), weight = 1, intervalMs = 80),
            )
        )
    }
    var appliedReadyInsertionIntervalMs by rememberSaveable { mutableStateOf(120) }
    var appliedReadyInsertionEveryNLoops by rememberSaveable { mutableStateOf(5) }
    var appliedReadyInsertionProbabilityPercent by rememberSaveable { mutableStateOf(58) }
    var appliedReadyInsertionCooldownLoops by rememberSaveable { mutableStateOf(4) }
    var appliedReadyInsertionEnabled by rememberSaveable { mutableStateOf(true) }
    var appliedReadyInsertionExclusive by rememberSaveable { mutableStateOf(false) }
    var readyInsertionPattern1FramesError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyInsertionPattern1WeightError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyInsertionPattern1IntervalError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyInsertionPattern2FramesError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyInsertionPattern2WeightError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyInsertionPattern2IntervalError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyInsertionIntervalError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyInsertionEveryNError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyInsertionProbabilityError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyInsertionCooldownError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingInsertionPattern1FramesInput by rememberSaveable { mutableStateOf("4,5,6") }
    var talkingInsertionPattern1WeightInput by rememberSaveable { mutableStateOf("1") }
    var talkingInsertionPattern1IntervalInput by rememberSaveable { mutableStateOf("") }
    var talkingInsertionPattern2FramesInput by rememberSaveable { mutableStateOf("") }
    var talkingInsertionPattern2WeightInput by rememberSaveable { mutableStateOf("0") }
    var talkingInsertionPattern2IntervalInput by rememberSaveable { mutableStateOf("") }
    var talkingInsertionIntervalInput by rememberSaveable { mutableStateOf("200") }
    var talkingInsertionEveryNInput by rememberSaveable { mutableStateOf("1") }
    var talkingInsertionProbabilityInput by rememberSaveable { mutableStateOf("50") }
    var talkingInsertionCooldownInput by rememberSaveable { mutableStateOf("0") }
    var talkingInsertionEnabled by rememberSaveable { mutableStateOf(false) }
    var talkingInsertionExclusive by rememberSaveable { mutableStateOf(false) }
    var appliedTalkingInsertionPatterns by remember {
        mutableStateOf(listOf(InsertionPattern(frameSequence = listOf(3, 4, 5))))
    }
    var appliedTalkingInsertionIntervalMs by rememberSaveable { mutableStateOf(200) }
    var appliedTalkingInsertionEveryNLoops by rememberSaveable { mutableStateOf(1) }
    var appliedTalkingInsertionProbabilityPercent by rememberSaveable { mutableStateOf(50) }
    var appliedTalkingInsertionCooldownLoops by rememberSaveable { mutableStateOf(0) }
    var appliedTalkingInsertionEnabled by rememberSaveable { mutableStateOf(false) }
    var appliedTalkingInsertionExclusive by rememberSaveable { mutableStateOf(false) }
    var talkingInsertionPattern1FramesError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingInsertionPattern1WeightError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingInsertionPattern1IntervalError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingInsertionPattern2FramesError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingInsertionPattern2WeightError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingInsertionPattern2IntervalError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingInsertionIntervalError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingInsertionEveryNError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingInsertionProbabilityError by rememberSaveable { mutableStateOf<String?>(null) }
    var talkingInsertionCooldownError by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAnimation by rememberSaveable { mutableStateOf(AnimationType.READY) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val extraAnimationStates = remember { mutableStateMapOf<AnimationType, AnimationInputState>() }

    LaunchedEffect(selectedSpeakingKey) {
        val restoredType = when (selectedSpeakingKey) {
            "TalkShort" -> AnimationType.TALK_SHORT
            "TalkLong" -> AnimationType.TALK_LONG
            "TalkCalm" -> AnimationType.TALK_CALM
            "TalkDefault" -> AnimationType.TALKING
            else -> null
        }
        if (restoredType != null && restoredType != selectedAnimation) {
            // SPEAKINGの復元は新DataStoreを優先して適用する
            selectedAnimation = restoredType
        }
    }

    LaunchedEffect(selectedReadyKey) {
        if (selectedReadyKey == "Ready" && selectedAnimation != AnimationType.READY) {
            // READY復元（新DataStore優先）
            selectedAnimation = AnimationType.READY
        }
    }

    LaunchedEffect(selectedIdleKey) {
        if (selectedIdleKey == "Idle" && selectedAnimation != AnimationType.IDLE) {
            // IDLE復元（新DataStore優先）
            selectedAnimation = AnimationType.IDLE
        }
    }

    LaunchedEffect(selectedThinkingKey) {
        if (selectedThinkingKey == "Thinking" && selectedAnimation != AnimationType.THINKING) {
            // THINKING復元（新DataStore優先）
            selectedAnimation = AnimationType.THINKING
        }
    }

    LaunchedEffect(lastSelectedAnimationKey) {
        val restoredKey = lastSelectedAnimationKey
        val restoredType = AnimationType.fromInternalKeyOrNull(restoredKey)
        val isSpeakingType = restoredType == AnimationType.TALK_SHORT ||
            restoredType == AnimationType.TALK_LONG ||
            restoredType == AnimationType.TALK_CALM ||
            restoredType == AnimationType.TALKING
        val isReadyType = restoredType == AnimationType.READY
        val isIdleType = restoredType == AnimationType.IDLE
        val isThinkingType = restoredType == AnimationType.THINKING
        if (isSpeakingType && selectedSpeakingKey != null) {
            // 段階移行：SPEAKINGは新DataStoreがある場合は旧キー復元をスキップする
            return@LaunchedEffect
        }
        if (isReadyType && selectedReadyKey != null) {
            // 段階移行：READYは新DataStoreがある場合は旧キー復元をスキップする
            return@LaunchedEffect
        }
        if (isIdleType && selectedIdleKey != null) {
            // 段階移行：IDLEは新DataStoreがある場合は旧キー復元をスキップする
            return@LaunchedEffect
        }
        if (isThinkingType && selectedThinkingKey != null) {
            // 段階移行：THINKINGは新DataStoreがある場合は旧キー復元をスキップする
            return@LaunchedEffect
        }
        if (restoredType != null && restoredType != selectedAnimation) {
            // internalKey から復元する（表示名差分の影響回避）
            selectedAnimation = restoredType
        }
    }

    LaunchedEffect(lastSelectedSpriteTabKey) {
        val restoredKey = lastSelectedSpriteTabKey
        if (restoredKey == UNSET_SPRITE_TAB) {
            return@LaunchedEffect
        }
        val restoredTab = restoredKey?.let {
            runCatching { SpriteTab.valueOf(it) }.getOrNull()
        }
        if (restoredTab != null && restoredTab != selectedTab) {
            // DataStore から復元したタブを適用する
            selectedTab = restoredTab
        }
        // 復元の試行が完了したので保存を解禁（再起動保持の上書き事故を防ぐ）
        didRestoreTab = true
    }

    LaunchedEffect(lastSelectedBoxNumberKey) {
        val restoredNumber = lastSelectedBoxNumberKey
        if (restoredNumber != null) {
            selectedNumber = restoredNumber.coerceIn(1, boxPositions.size.coerceAtLeast(1))
        }
        // 復元の試行が完了したので保存を解禁（再起動保持の上書き事故を防ぐ）
        didRestoreAdjustSelection = true
    }

    LaunchedEffect(selectedTab) {
        if (!didRestoreTab) {
            // 復元完了前の保存を避け、再起動保持の復元順序を守る
            return@LaunchedEffect
        }
        // タブ切替のたびに保存して、戻る/再起動後に復元できるようにする
        settingsPreferences.saveLastSelectedSpriteTab(selectedTab.name)
    }

    LaunchedEffect(selectedNumber) {
        if (!didRestoreAdjustSelection) {
            // 復元完了前の保存を避け、再起動保持の復元順序を守る
            return@LaunchedEffect
        }
        settingsPreferences.saveLastSelectedBoxNumber(selectedNumber)
    }

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
        val normalizedPatterns = readyInsertionAnimationSettings.patterns
        val patternInputs = normalizedPatterns.toInsertionPatternInputs()
        appliedReadyInsertionPatterns = normalizedPatterns
        appliedReadyInsertionIntervalMs = readyInsertionAnimationSettings.intervalMs
        appliedReadyInsertionEveryNLoops = readyInsertionAnimationSettings.everyNLoops
        appliedReadyInsertionProbabilityPercent = readyInsertionAnimationSettings.probabilityPercent
        appliedReadyInsertionCooldownLoops = readyInsertionAnimationSettings.cooldownLoops
        appliedReadyInsertionEnabled = readyInsertionAnimationSettings.enabled
        appliedReadyInsertionExclusive = readyInsertionAnimationSettings.exclusive
        readyInsertionPattern1FramesInput = patternInputs.pattern1FramesInput
        readyInsertionPattern1WeightInput = patternInputs.pattern1WeightInput
        readyInsertionPattern1IntervalInput = patternInputs.pattern1IntervalInput
        readyInsertionPattern2FramesInput = patternInputs.pattern2FramesInput
        readyInsertionPattern2WeightInput = patternInputs.pattern2WeightInput
        readyInsertionPattern2IntervalInput = patternInputs.pattern2IntervalInput
        readyInsertionIntervalInput = readyInsertionAnimationSettings.intervalMs.toString()
        readyInsertionEveryNInput = readyInsertionAnimationSettings.everyNLoops.toString()
        readyInsertionProbabilityInput = readyInsertionAnimationSettings.probabilityPercent.toString()
        readyInsertionCooldownInput = readyInsertionAnimationSettings.cooldownLoops.toString()
        readyInsertionEnabled = readyInsertionAnimationSettings.enabled
        readyInsertionExclusive = readyInsertionAnimationSettings.exclusive
    }

    LaunchedEffect(talkingInsertionAnimationSettings) {
        val normalizedPatterns = talkingInsertionAnimationSettings.patterns
        val patternInputs = normalizedPatterns.toInsertionPatternInputs()
        appliedTalkingInsertionPatterns = normalizedPatterns
        appliedTalkingInsertionIntervalMs = talkingInsertionAnimationSettings.intervalMs
        appliedTalkingInsertionEveryNLoops = talkingInsertionAnimationSettings.everyNLoops
        appliedTalkingInsertionProbabilityPercent = talkingInsertionAnimationSettings.probabilityPercent
        appliedTalkingInsertionCooldownLoops = talkingInsertionAnimationSettings.cooldownLoops
        appliedTalkingInsertionEnabled = talkingInsertionAnimationSettings.enabled
        appliedTalkingInsertionExclusive = talkingInsertionAnimationSettings.exclusive
        talkingInsertionPattern1FramesInput = patternInputs.pattern1FramesInput
        talkingInsertionPattern1WeightInput = patternInputs.pattern1WeightInput
        talkingInsertionPattern1IntervalInput = patternInputs.pattern1IntervalInput
        talkingInsertionPattern2FramesInput = patternInputs.pattern2FramesInput
        talkingInsertionPattern2WeightInput = patternInputs.pattern2WeightInput
        talkingInsertionPattern2IntervalInput = patternInputs.pattern2IntervalInput
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

    data class InsertionPatternsValidation(
        val patterns: List<InsertionPattern>?,
        val pattern1FramesError: String?,
        val pattern1WeightError: String?,
        val pattern1IntervalError: String?,
        val pattern2FramesError: String?,
        val pattern2WeightError: String?,
        val pattern2IntervalError: String?,
    )

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

    fun parseWeightInput(
        input: String,
        defaultValue: Int,
    ): ValidationResult<Int> {
        val rawText = input.trim()
        if (rawText.isEmpty()) return ValidationResult(defaultValue, null)
        val rawValue = rawText.toIntOrNull() ?: return ValidationResult(null, "数値を入力してください")
        if (rawValue < 0) return ValidationResult(null, "0以上で入力してください")
        return ValidationResult(rawValue, null)
    }

    fun parsePatternIntervalInput(input: String): ValidationResult<Int?> {
        val rawText = input.trim()
        if (rawText.isEmpty()) return ValidationResult(null, null)
        val rawValue = rawText.toIntOrNull() ?: return ValidationResult(null, "数値を入力してください")
        if (rawValue < 0) return ValidationResult(null, "0以上で入力してください")
        if (rawValue > InsertionAnimationSettings.MAX_INTERVAL_MS) {
            return ValidationResult(
                null,
                "0〜${InsertionAnimationSettings.MAX_INTERVAL_MS}の範囲で入力してください"
            )
        }
        return ValidationResult(rawValue, null)
    }

    fun parseInsertionPatternsInput(
        pattern1FramesInput: String,
        pattern1WeightInput: String,
        pattern1IntervalInput: String,
        pattern2FramesInput: String,
        pattern2WeightInput: String,
        pattern2IntervalInput: String,
        frameCount: Int,
    ): InsertionPatternsValidation {
        // 影響範囲: 挿入フレーム入力のパターン分解は保存/プレビュー双方で参照される。
        var pattern1FramesError: String? = null
        var pattern1WeightError: String? = null
        var pattern1IntervalError: String? = null
        var pattern2FramesError: String? = null
        var pattern2WeightError: String? = null
        var pattern2IntervalError: String? = null
        val patterns = mutableListOf<InsertionPattern>()

        val pattern1FramesText = pattern1FramesInput.trim()
        val pattern2FramesText = pattern2FramesInput.trim()

        if (pattern1FramesText.isNotEmpty()) {
            val framesResult = parseFrameSequenceInput(
                input = pattern1FramesText,
                frameCount = frameCount,
                allowDuplicates = true,
            )
            val weightResult = parseWeightInput(
                input = pattern1WeightInput,
                defaultValue = 1,
            )
            val intervalResult = parsePatternIntervalInput(pattern1IntervalInput)
            pattern1FramesError = framesResult.error
            pattern1WeightError = weightResult.error
            pattern1IntervalError = intervalResult.error
            if (framesResult.value != null && weightResult.value != null && intervalResult.error == null) {
                patterns.add(
                    InsertionPattern(
                        frameSequence = framesResult.value,
                        weight = weightResult.value,
                        intervalMs = intervalResult.value,
                    )
                )
            }
        } else {
            val weightResult = parseWeightInput(
                input = pattern1WeightInput,
                defaultValue = 1,
            )
            pattern1WeightError = weightResult.error
            val intervalResult = parsePatternIntervalInput(pattern1IntervalInput)
            pattern1IntervalError = intervalResult.error
        }

        if (pattern2FramesText.isNotEmpty()) {
            val framesResult = parseFrameSequenceInput(
                input = pattern2FramesText,
                frameCount = frameCount,
                allowDuplicates = true,
            )
            val weightResult = parseWeightInput(
                input = pattern2WeightInput,
                defaultValue = 1,
            )
            val intervalResult = parsePatternIntervalInput(pattern2IntervalInput)
            pattern2FramesError = framesResult.error
            pattern2WeightError = weightResult.error
            pattern2IntervalError = intervalResult.error
            if (framesResult.value != null && weightResult.value != null && intervalResult.error == null) {
                patterns.add(
                    InsertionPattern(
                        frameSequence = framesResult.value,
                        weight = weightResult.value,
                        intervalMs = intervalResult.value,
                    )
                )
            }
        } else {
            val weightResult = parseWeightInput(
                input = pattern2WeightInput,
                defaultValue = 0,
            )
            pattern2WeightError = weightResult.error
            val intervalResult = parsePatternIntervalInput(pattern2IntervalInput)
            pattern2IntervalError = intervalResult.error
        }

        if (patterns.isEmpty() && pattern1FramesText.isEmpty() && pattern2FramesText.isEmpty()) {
            pattern1FramesError = "挿入パターンは1つ以上入力してください"
        }

        val hasError = listOf(
            pattern1FramesError,
            pattern1WeightError,
            pattern1IntervalError,
            pattern2FramesError,
            pattern2WeightError,
            pattern2IntervalError,
        ).any { it != null }

        return InsertionPatternsValidation(
            patterns = if (hasError) null else patterns,
            pattern1FramesError = pattern1FramesError,
            pattern1WeightError = pattern1WeightError,
            pattern1IntervalError = pattern1IntervalError,
            pattern2FramesError = pattern2FramesError,
            pattern2WeightError = pattern2WeightError,
            pattern2IntervalError = pattern2IntervalError,
        )
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

    fun resolveExtraState(target: AnimationType): AnimationInputState {
        val existing = extraAnimationStates[target]
        if (existing != null) {
            return existing
        }
        val defaults = requireNotNull(extraAnimationDefaults[target]) { "対象外のアニメ種別です: $target" }
        val initialState = defaults.toInputState()
        extraAnimationStates[target] = initialState
        return initialState
    }

    fun updateExtraState(target: AnimationType, transform: (AnimationInputState) -> AnimationInputState) {
        val current = resolveExtraState(target)
        extraAnimationStates[target] = transform(current)
    }

    fun resolveInputState(target: AnimationType): AnimationInputState {
        return when (target) {
            AnimationType.READY -> AnimationInputState(
                frameInput = readyFrameInput,
                intervalInput = readyIntervalInput,
                framesError = readyFramesError,
                intervalError = readyIntervalError,
                insertionPattern1FramesInput = readyInsertionPattern1FramesInput,
                insertionPattern1WeightInput = readyInsertionPattern1WeightInput,
                insertionPattern1IntervalInput = readyInsertionPattern1IntervalInput,
                insertionPattern2FramesInput = readyInsertionPattern2FramesInput,
                insertionPattern2WeightInput = readyInsertionPattern2WeightInput,
                insertionPattern2IntervalInput = readyInsertionPattern2IntervalInput,
                insertionIntervalInput = readyInsertionIntervalInput,
                insertionEveryNInput = readyInsertionEveryNInput,
                insertionProbabilityInput = readyInsertionProbabilityInput,
                insertionCooldownInput = readyInsertionCooldownInput,
                insertionEnabled = readyInsertionEnabled,
                insertionExclusive = readyInsertionExclusive,
                insertionPattern1FramesError = readyInsertionPattern1FramesError,
                insertionPattern1WeightError = readyInsertionPattern1WeightError,
                insertionPattern1IntervalError = readyInsertionPattern1IntervalError,
                insertionPattern2FramesError = readyInsertionPattern2FramesError,
                insertionPattern2WeightError = readyInsertionPattern2WeightError,
                insertionPattern2IntervalError = readyInsertionPattern2IntervalError,
                insertionIntervalError = readyInsertionIntervalError,
                insertionEveryNError = readyInsertionEveryNError,
                insertionProbabilityError = readyInsertionProbabilityError,
                insertionCooldownError = readyInsertionCooldownError,
                appliedBase = ReadyAnimationSettings(
                    frameSequence = appliedReadyFrames,
                    intervalMs = appliedReadyIntervalMs,
                ),
                appliedInsertion = InsertionAnimationSettings(
                    enabled = appliedReadyInsertionEnabled,
                    patterns = appliedReadyInsertionPatterns,
                    intervalMs = appliedReadyInsertionIntervalMs,
                    everyNLoops = appliedReadyInsertionEveryNLoops,
                    probabilityPercent = appliedReadyInsertionProbabilityPercent,
                    cooldownLoops = appliedReadyInsertionCooldownLoops,
                    exclusive = appliedReadyInsertionExclusive,
                ),
            )

            AnimationType.TALKING -> AnimationInputState(
                frameInput = talkingFrameInput,
                intervalInput = talkingIntervalInput,
                framesError = talkingFramesError,
                intervalError = talkingIntervalError,
                insertionPattern1FramesInput = talkingInsertionPattern1FramesInput,
                insertionPattern1WeightInput = talkingInsertionPattern1WeightInput,
                insertionPattern1IntervalInput = talkingInsertionPattern1IntervalInput,
                insertionPattern2FramesInput = talkingInsertionPattern2FramesInput,
                insertionPattern2WeightInput = talkingInsertionPattern2WeightInput,
                insertionPattern2IntervalInput = talkingInsertionPattern2IntervalInput,
                insertionIntervalInput = talkingInsertionIntervalInput,
                insertionEveryNInput = talkingInsertionEveryNInput,
                insertionProbabilityInput = talkingInsertionProbabilityInput,
                insertionCooldownInput = talkingInsertionCooldownInput,
                insertionEnabled = talkingInsertionEnabled,
                insertionExclusive = talkingInsertionExclusive,
                insertionPattern1FramesError = talkingInsertionPattern1FramesError,
                insertionPattern1WeightError = talkingInsertionPattern1WeightError,
                insertionPattern1IntervalError = talkingInsertionPattern1IntervalError,
                insertionPattern2FramesError = talkingInsertionPattern2FramesError,
                insertionPattern2WeightError = talkingInsertionPattern2WeightError,
                insertionPattern2IntervalError = talkingInsertionPattern2IntervalError,
                insertionIntervalError = talkingInsertionIntervalError,
                insertionEveryNError = talkingInsertionEveryNError,
                insertionProbabilityError = talkingInsertionProbabilityError,
                insertionCooldownError = talkingInsertionCooldownError,
                appliedBase = ReadyAnimationSettings(
                    frameSequence = appliedTalkingFrames,
                    intervalMs = appliedTalkingIntervalMs,
                ),
                appliedInsertion = InsertionAnimationSettings(
                    enabled = appliedTalkingInsertionEnabled,
                    patterns = appliedTalkingInsertionPatterns,
                    intervalMs = appliedTalkingInsertionIntervalMs,
                    everyNLoops = appliedTalkingInsertionEveryNLoops,
                    probabilityPercent = appliedTalkingInsertionProbabilityPercent,
                    cooldownLoops = appliedTalkingInsertionCooldownLoops,
                    exclusive = appliedTalkingInsertionExclusive,
                ),
            )

            else -> resolveExtraState(target)
        }
    }

    fun isInsertionEnabled(target: AnimationType): Boolean {
        return when (target) {
            AnimationType.READY -> readyInsertionEnabled
            AnimationType.TALKING -> talkingInsertionEnabled
            else -> resolveExtraState(target).insertionEnabled
        }
    }

    fun isBaseInputSynced(target: AnimationType): Boolean {
        val (inputFrames, inputInterval) = when (target) {
            AnimationType.READY -> readyFrameInput to readyIntervalInput
            AnimationType.TALKING -> talkingFrameInput to talkingIntervalInput
            else -> {
                val state = resolveExtraState(target)
                state.frameInput to state.intervalInput
            }
        }
        val (appliedFrames, appliedInterval) = when (target) {
            AnimationType.READY -> appliedReadyFrames to appliedReadyIntervalMs
            AnimationType.TALKING -> appliedTalkingFrames to appliedTalkingIntervalMs
            else -> {
                val state = resolveExtraState(target)
                state.appliedBase.frameSequence to state.appliedBase.intervalMs
            }
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
        val patternsResult: InsertionPatternsValidation
        val intervalResult: ValidationResult<Int>
        val everyNResult: ValidationResult<Int>
        val probabilityResult: ValidationResult<Int>
        val cooldownResult: ValidationResult<Int>
        when (target) {
            AnimationType.READY -> {
                inputEnabled = readyInsertionEnabled
                inputExclusive = readyInsertionExclusive
                patternsResult = parseInsertionPatternsInput(
                    pattern1FramesInput = readyInsertionPattern1FramesInput,
                    pattern1WeightInput = readyInsertionPattern1WeightInput,
                    pattern1IntervalInput = readyInsertionPattern1IntervalInput,
                    pattern2FramesInput = readyInsertionPattern2FramesInput,
                    pattern2WeightInput = readyInsertionPattern2WeightInput,
                    pattern2IntervalInput = readyInsertionPattern2IntervalInput,
                    frameCount = spriteSheetConfig.frameCount
                )
                intervalResult = parseIntervalMsInput(readyInsertionIntervalInput)
                everyNResult = parseEveryNLoopsInput(readyInsertionEveryNInput)
                probabilityResult = parseProbabilityPercentInput(readyInsertionProbabilityInput)
                cooldownResult = parseCooldownLoopsInput(readyInsertionCooldownInput)
            }

            AnimationType.TALKING -> {
                inputEnabled = talkingInsertionEnabled
                inputExclusive = talkingInsertionExclusive
                patternsResult = parseInsertionPatternsInput(
                    pattern1FramesInput = talkingInsertionPattern1FramesInput,
                    pattern1WeightInput = talkingInsertionPattern1WeightInput,
                    pattern1IntervalInput = talkingInsertionPattern1IntervalInput,
                    pattern2FramesInput = talkingInsertionPattern2FramesInput,
                    pattern2WeightInput = talkingInsertionPattern2WeightInput,
                    pattern2IntervalInput = talkingInsertionPattern2IntervalInput,
                    frameCount = spriteSheetConfig.frameCount
                )
                intervalResult = parseIntervalMsInput(talkingInsertionIntervalInput)
                everyNResult = parseEveryNLoopsInput(talkingInsertionEveryNInput)
                probabilityResult = parseProbabilityPercentInput(talkingInsertionProbabilityInput)
                cooldownResult = parseCooldownLoopsInput(talkingInsertionCooldownInput)
            }

            else -> {
                val state = resolveExtraState(target)
                inputEnabled = state.insertionEnabled
                inputExclusive = state.insertionExclusive
                patternsResult = parseInsertionPatternsInput(
                    pattern1FramesInput = state.insertionPattern1FramesInput,
                    pattern1WeightInput = state.insertionPattern1WeightInput,
                    pattern1IntervalInput = state.insertionPattern1IntervalInput,
                    pattern2FramesInput = state.insertionPattern2FramesInput,
                    pattern2WeightInput = state.insertionPattern2WeightInput,
                    pattern2IntervalInput = state.insertionPattern2IntervalInput,
                    frameCount = spriteSheetConfig.frameCount
                )
                intervalResult = parseIntervalMsInput(state.insertionIntervalInput)
                everyNResult = parseEveryNLoopsInput(state.insertionEveryNInput)
                probabilityResult = parseProbabilityPercentInput(state.insertionProbabilityInput)
                cooldownResult = parseCooldownLoopsInput(state.insertionCooldownInput)
            }
        }
        val inputState = listOf(
            patternsResult.patterns,
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
            patterns = patternsResult.patterns!!,
            intervalMs = intervalResult.value!!,
            everyNLoops = everyNResult.value!!,
            probabilityPercent = probabilityResult.value!!,
            cooldownLoops = cooldownResult.value!!,
            exclusive = inputExclusive
        )

        val appliedState = when (target) {
            AnimationType.READY -> InsertionAnimationSettings(
                enabled = appliedReadyInsertionEnabled,
                patterns = appliedReadyInsertionPatterns,
                intervalMs = appliedReadyInsertionIntervalMs,
                everyNLoops = appliedReadyInsertionEveryNLoops,
                probabilityPercent = appliedReadyInsertionProbabilityPercent,
                cooldownLoops = appliedReadyInsertionCooldownLoops,
                exclusive = appliedReadyInsertionExclusive
            )

            AnimationType.TALKING -> InsertionAnimationSettings(
                enabled = appliedTalkingInsertionEnabled,
                patterns = appliedTalkingInsertionPatterns,
                intervalMs = appliedTalkingInsertionIntervalMs,
                everyNLoops = appliedTalkingInsertionEveryNLoops,
                probabilityPercent = appliedTalkingInsertionProbabilityPercent,
                cooldownLoops = appliedTalkingInsertionCooldownLoops,
                exclusive = appliedTalkingInsertionExclusive
            )

            else -> resolveExtraState(target).appliedInsertion
        }

        if (parsedInsertion.enabled != appliedState.enabled || parsedInsertion.exclusive != appliedState.exclusive) {
            return false
        }

        val patternsMatch = parsedInsertion.patterns == appliedState.patterns
        val intervalMatches = parsedInsertion.intervalMs == appliedState.intervalMs
        val everyNMatches = parsedInsertion.everyNLoops == appliedState.everyNLoops
        val probabilityMatches = parsedInsertion.probabilityPercent == appliedState.probabilityPercent
        val cooldownMatches = parsedInsertion.cooldownLoops == appliedState.cooldownLoops

        return patternsMatch && intervalMatches && everyNMatches && probabilityMatches && cooldownMatches
    }

    fun hasUnsavedChanges(): Boolean {
        val baseSynced = isBaseInputSynced(AnimationType.READY) && isBaseInputSynced(AnimationType.TALKING)
        val insertionSynced =
            isInsertionInputSynced(AnimationType.READY) && isInsertionInputSynced(AnimationType.TALKING)
        return !(baseSynced && insertionSynced)
    }

    fun navigateBackWithFallback() {
        val popped = navController.popBackStack()
        if (!popped) {
            // startDestination時に戻り先が無いので、Settingsへフォールバック遷移する
            navController.navigate(Routes.SETTINGS) {
                launchSingleTop = true
            }
        }
    }

    fun onBackRequested() {
        if (showDiscardDialog) {
            showDiscardDialog = false
            return
        }
        if (hasUnsavedChanges()) {
            showDiscardDialog = true
        } else {
            navigateBackWithFallback()
        }
    }

    fun clampAllPositions(newBoxSizePx: Int): List<BoxPosition> =
        boxPositions.map { position ->
            clampPosition(position, newBoxSizePx, safeImageWidth, safeImageHeight)
        }

    fun updateBoxSize(delta: Int) {
        val maxSize = minOf(safeImageWidth, safeImageHeight).coerceAtLeast(1)
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
            sheetWidth = safeImageWidth,
            sheetHeight = safeImageHeight
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
            else -> {
                val state = resolveExtraState(target)
                state.frameInput to state.intervalInput
            }
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

            else -> {
                updateExtraState(target) { state ->
                    state.copy(
                        framesError = framesResult.error,
                        intervalError = intervalResult.error,
                    )
                }
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
        val pattern1FramesInput: String
        val pattern1WeightInput: String
        val pattern1IntervalInput: String
        val pattern2FramesInput: String
        val pattern2WeightInput: String
        val pattern2IntervalInput: String
        val intervalInput: String
        val everyNInput: String
        val probabilityInput: String
        val cooldownInput: String
        val exclusive: Boolean
        when (target) {
            AnimationType.READY -> {
                pattern1FramesInput = readyInsertionPattern1FramesInput
                pattern1WeightInput = readyInsertionPattern1WeightInput
                pattern1IntervalInput = readyInsertionPattern1IntervalInput
                pattern2FramesInput = readyInsertionPattern2FramesInput
                pattern2WeightInput = readyInsertionPattern2WeightInput
                pattern2IntervalInput = readyInsertionPattern2IntervalInput
                intervalInput = readyInsertionIntervalInput
                everyNInput = readyInsertionEveryNInput
                probabilityInput = readyInsertionProbabilityInput
                cooldownInput = readyInsertionCooldownInput
                exclusive = readyInsertionExclusive
            }

            AnimationType.TALKING -> {
                pattern1FramesInput = talkingInsertionPattern1FramesInput
                pattern1WeightInput = talkingInsertionPattern1WeightInput
                pattern1IntervalInput = talkingInsertionPattern1IntervalInput
                pattern2FramesInput = talkingInsertionPattern2FramesInput
                pattern2WeightInput = talkingInsertionPattern2WeightInput
                pattern2IntervalInput = talkingInsertionPattern2IntervalInput
                intervalInput = talkingInsertionIntervalInput
                everyNInput = talkingInsertionEveryNInput
                probabilityInput = talkingInsertionProbabilityInput
                cooldownInput = talkingInsertionCooldownInput
                exclusive = talkingInsertionExclusive
            }

            else -> {
                val state = resolveExtraState(target)
                pattern1FramesInput = state.insertionPattern1FramesInput
                pattern1WeightInput = state.insertionPattern1WeightInput
                pattern1IntervalInput = state.insertionPattern1IntervalInput
                pattern2FramesInput = state.insertionPattern2FramesInput
                pattern2WeightInput = state.insertionPattern2WeightInput
                pattern2IntervalInput = state.insertionPattern2IntervalInput
                intervalInput = state.insertionIntervalInput
                everyNInput = state.insertionEveryNInput
                probabilityInput = state.insertionProbabilityInput
                cooldownInput = state.insertionCooldownInput
                exclusive = state.insertionExclusive
            }
        }
        val patternsResult = parseInsertionPatternsInput(
            pattern1FramesInput = pattern1FramesInput,
            pattern1WeightInput = pattern1WeightInput,
            pattern1IntervalInput = pattern1IntervalInput,
            pattern2FramesInput = pattern2FramesInput,
            pattern2WeightInput = pattern2WeightInput,
            pattern2IntervalInput = pattern2IntervalInput,
            frameCount = spriteSheetConfig.frameCount
        )
        val intervalResult = parseIntervalMsInput(intervalInput)
        val everyNResult = parseEveryNLoopsInput(everyNInput)
        val probabilityResult = parseProbabilityPercentInput(probabilityInput)
        val cooldownResult = parseCooldownLoopsInput(cooldownInput)

        when (target) {
            AnimationType.READY -> {
                readyInsertionPattern1FramesError = patternsResult.pattern1FramesError
                readyInsertionPattern1WeightError = patternsResult.pattern1WeightError
                readyInsertionPattern1IntervalError = patternsResult.pattern1IntervalError
                readyInsertionPattern2FramesError = patternsResult.pattern2FramesError
                readyInsertionPattern2WeightError = patternsResult.pattern2WeightError
                readyInsertionPattern2IntervalError = patternsResult.pattern2IntervalError
                readyInsertionIntervalError = intervalResult.error
                readyInsertionEveryNError = everyNResult.error
                readyInsertionProbabilityError = probabilityResult.error
                readyInsertionCooldownError = cooldownResult.error
            }

            AnimationType.TALKING -> {
                talkingInsertionPattern1FramesError = patternsResult.pattern1FramesError
                talkingInsertionPattern1WeightError = patternsResult.pattern1WeightError
                talkingInsertionPattern1IntervalError = patternsResult.pattern1IntervalError
                talkingInsertionPattern2FramesError = patternsResult.pattern2FramesError
                talkingInsertionPattern2WeightError = patternsResult.pattern2WeightError
                talkingInsertionPattern2IntervalError = patternsResult.pattern2IntervalError
                talkingInsertionIntervalError = intervalResult.error
                talkingInsertionEveryNError = everyNResult.error
                talkingInsertionProbabilityError = probabilityResult.error
                talkingInsertionCooldownError = cooldownResult.error
            }

            else -> {
                updateExtraState(target) { state ->
                    state.copy(
                        insertionPattern1FramesError = patternsResult.pattern1FramesError,
                        insertionPattern1WeightError = patternsResult.pattern1WeightError,
                        insertionPattern1IntervalError = patternsResult.pattern1IntervalError,
                        insertionPattern2FramesError = patternsResult.pattern2FramesError,
                        insertionPattern2WeightError = patternsResult.pattern2WeightError,
                        insertionPattern2IntervalError = patternsResult.pattern2IntervalError,
                        insertionIntervalError = intervalResult.error,
                        insertionEveryNError = everyNResult.error,
                        insertionProbabilityError = probabilityResult.error,
                        insertionCooldownError = cooldownResult.error,
                    )
                }
            }
        }

        val patterns = patternsResult.patterns
        val interval = intervalResult.value
        val everyN = everyNResult.value
        val probability = probabilityResult.value
        val cooldown = cooldownResult.value

        if (patterns != null && interval != null && everyN != null && probability != null && cooldown != null) {
            return InsertionAnimationSettings(
                enabled = true,
                patterns = patterns,
                intervalMs = interval,
                everyNLoops = everyN,
                probabilityPercent = probability,
                cooldownLoops = cooldown,
                exclusive = exclusive,
            )
        }
        return null
    }

    fun resolveDefaultAnimations(): Map<AnimationType, AnimationDefaults> {
        val readyDefaults = AnimationDefaults(
            base = ReadyAnimationSettings(
                frameSequence = appliedReadyFrames,
                intervalMs = appliedReadyIntervalMs,
            ),
            insertion = InsertionAnimationSettings(
                enabled = appliedReadyInsertionEnabled,
                patterns = appliedReadyInsertionPatterns,
                intervalMs = appliedReadyInsertionIntervalMs,
                everyNLoops = appliedReadyInsertionEveryNLoops,
                probabilityPercent = appliedReadyInsertionProbabilityPercent,
                cooldownLoops = appliedReadyInsertionCooldownLoops,
                exclusive = appliedReadyInsertionExclusive,
            ),
        )
        val talkingDefaults = AnimationDefaults(
            base = ReadyAnimationSettings(
                frameSequence = appliedTalkingFrames,
                intervalMs = appliedTalkingIntervalMs,
            ),
            insertion = InsertionAnimationSettings(
                enabled = appliedTalkingInsertionEnabled,
                patterns = appliedTalkingInsertionPatterns,
                intervalMs = appliedTalkingInsertionIntervalMs,
                everyNLoops = appliedTalkingInsertionEveryNLoops,
                probabilityPercent = appliedTalkingInsertionProbabilityPercent,
                cooldownLoops = appliedTalkingInsertionCooldownLoops,
                exclusive = appliedTalkingInsertionExclusive,
            ),
        )
        return buildMap {
            put(AnimationType.READY, readyDefaults)
            put(AnimationType.TALKING, talkingDefaults)
            putAll(extraAnimationDefaults)
        }
    }

    fun resolveLegacyDefaultsFromSettings(): Map<AnimationType, AnimationDefaults> =
        buildMap {
            put(
                AnimationType.READY,
                AnimationDefaults(
                    base = readyAnimationSettings,
                    insertion = readyInsertionAnimationSettings,
                )
            )
            put(
                AnimationType.TALKING,
                AnimationDefaults(
                    base = talkingAnimationSettings,
                    insertion = talkingInsertionAnimationSettings,
                )
            )
            putAll(extraAnimationDefaults)
        }

    fun parseFramesFromJson(
        array: JSONArray?,
        fallback: List<Int>,
        frameCount: Int,
    ): ValidationResult<List<Int>> {
        if (array == null) return ValidationResult(fallback, null)
        val frames = buildList {
            for (index in 0 until array.length()) {
                val value = array.opt(index)
                val frameIndex = when (value) {
                    is Int -> value
                    is Number -> value.toInt()
                    else -> return ValidationResult(null, "framesの形式が不正です")
                }
                if (frameIndex !in 0 until frameCount) {
                    return ValidationResult(null, "framesは0〜${frameCount - 1}の範囲で入力してください")
                }
                add(frameIndex)
            }
        }
        return ValidationResult(frames.ifEmpty { fallback }, null)
    }

    fun parseInsertionPatternsFromJson(
        json: JSONObject?,
        fallback: List<InsertionPattern>,
    ): ValidationResult<List<InsertionPattern>> {
        if (json == null) return ValidationResult(fallback, null)
        val patternsArray = json.optJSONArray(JSON_PATTERNS_KEY)
        if (patternsArray != null) {
            val patterns = mutableListOf<InsertionPattern>()
            for (index in 0 until patternsArray.length()) {
                val patternObject = patternsArray.optJSONObject(index)
                    ?: return ValidationResult(null, "patterns[$index]はオブジェクトで入力してください")
                val framesResult = parseFramesFromJson(
                    array = patternObject.optJSONArray(JSON_FRAMES_KEY),
                    fallback = emptyList(),
                    frameCount = spriteSheetConfig.frameCount,
                )
                if (framesResult.error != null) {
                    return ValidationResult(null, "patterns[$index].frames: ${framesResult.error}")
                }
                val frames = framesResult.value.orEmpty()
                val weight = patternObject.optInt(JSON_WEIGHT_KEY, 1).coerceAtLeast(0)
                val intervalMs = if (patternObject.has(JSON_PATTERN_INTERVAL_MS_KEY)) {
                    val rawInterval = patternObject.optInt(JSON_PATTERN_INTERVAL_MS_KEY, -1)
                    if (rawInterval < 0 || rawInterval > InsertionAnimationSettings.MAX_INTERVAL_MS) {
                        return ValidationResult(
                            null,
                            "patterns[$index].intervalMsは0〜${InsertionAnimationSettings.MAX_INTERVAL_MS}の範囲で入力してください"
                        )
                    }
                    rawInterval
                } else {
                    null
                }
                if (frames.isNotEmpty()) {
                    patterns.add(
                        InsertionPattern(
                            frameSequence = frames,
                            weight = weight,
                            intervalMs = intervalMs,
                        )
                    )
                }
            }
            return ValidationResult(patterns, null)
        }
        val legacyFramesResult = parseFramesFromJson(
            array = json.optJSONArray(JSON_FRAMES_KEY),
            fallback = fallback.firstOrNull()?.frameSequence.orEmpty(),
            frameCount = spriteSheetConfig.frameCount,
        )
        if (legacyFramesResult.error != null) return ValidationResult(null, legacyFramesResult.error)
        val legacyFrames = legacyFramesResult.value.orEmpty()
        val legacyPatterns = if (legacyFrames.isEmpty()) {
            emptyList()
        } else {
            listOf(InsertionPattern(frameSequence = legacyFrames, weight = 1))
        }
        return ValidationResult(legacyPatterns, null)
    }

    fun parseBaseFromJson(
        json: JSONObject?,
        fallback: ReadyAnimationSettings,
    ): ValidationResult<ReadyAnimationSettings> {
        if (json == null) return ValidationResult(fallback, null)
        val framesResult = parseFramesFromJson(
            array = json.optJSONArray(JSON_FRAMES_KEY),
            fallback = fallback.frameSequence,
            frameCount = spriteSheetConfig.frameCount,
        )
        val intervalMs = json.optInt(JSON_INTERVAL_MS_KEY, fallback.intervalMs)
        if (intervalMs !in ReadyAnimationSettings.MIN_INTERVAL_MS..ReadyAnimationSettings.MAX_INTERVAL_MS) {
            return ValidationResult(
                null,
                "intervalMsは${ReadyAnimationSettings.MIN_INTERVAL_MS}〜${ReadyAnimationSettings.MAX_INTERVAL_MS}の範囲で入力してください"
            )
        }
        if (framesResult.error != null) return ValidationResult(null, framesResult.error)
        return ValidationResult(
            ReadyAnimationSettings(
                frameSequence = framesResult.value ?: fallback.frameSequence,
                intervalMs = intervalMs,
            ),
            null
        )
    }

    fun parseInsertionFromJson(
        json: JSONObject?,
        fallback: InsertionAnimationSettings,
    ): ValidationResult<InsertionAnimationSettings> {
        if (json == null) return ValidationResult(fallback, null)
        val patternsResult = parseInsertionPatternsFromJson(
            json = json,
            fallback = fallback.patterns,
        )
        val intervalMs = json.optInt(JSON_INTERVAL_MS_KEY, fallback.intervalMs)
        if (intervalMs !in InsertionAnimationSettings.MIN_INTERVAL_MS..InsertionAnimationSettings.MAX_INTERVAL_MS) {
            return ValidationResult(
                null,
                "intervalMsは${InsertionAnimationSettings.MIN_INTERVAL_MS}〜${InsertionAnimationSettings.MAX_INTERVAL_MS}の範囲で入力してください"
            )
        }
        val everyNLoops = json.optInt(JSON_EVERY_N_LOOPS_KEY, fallback.everyNLoops)
        if (everyNLoops < InsertionAnimationSettings.MIN_EVERY_N_LOOPS) {
            return ValidationResult(null, "everyNLoopsは${InsertionAnimationSettings.MIN_EVERY_N_LOOPS}以上で入力してください")
        }
        val probabilityPercent = json.optInt(JSON_PROBABILITY_PERCENT_KEY, fallback.probabilityPercent)
        if (probabilityPercent !in InsertionAnimationSettings.MIN_PROBABILITY_PERCENT..InsertionAnimationSettings.MAX_PROBABILITY_PERCENT) {
            return ValidationResult(null, "probabilityPercentは0〜100の範囲で入力してください")
        }
        val cooldownLoops = json.optInt(JSON_COOLDOWN_LOOPS_KEY, fallback.cooldownLoops)
        if (cooldownLoops < InsertionAnimationSettings.MIN_COOLDOWN_LOOPS) {
            return ValidationResult(null, "cooldownLoopsは0以上で入力してください")
        }
        val enabled = json.optBoolean(JSON_ENABLED_KEY, fallback.enabled)
        val exclusive = json.optBoolean(JSON_EXCLUSIVE_KEY, fallback.exclusive)
        if (patternsResult.error != null) return ValidationResult(null, patternsResult.error)
        return ValidationResult(
            InsertionAnimationSettings(
                enabled = enabled,
                patterns = patternsResult.value ?: fallback.patterns,
                intervalMs = intervalMs,
                everyNLoops = everyNLoops,
                probabilityPercent = probabilityPercent,
                cooldownLoops = cooldownLoops,
                exclusive = exclusive,
            ),
            null
        )
    }

    fun resolveEditingOrApplied(target: AnimationType): AnimationDefaults {
        val applied = resolveDefaultAnimations().getValue(target)
        val baseFramesInput: String
        val baseIntervalInput: String
        val insertionPattern1FramesInput: String
        val insertionPattern1WeightInput: String
        val insertionPattern2FramesInput: String
        val insertionPattern2WeightInput: String
        val insertionPattern1IntervalInput: String
        val insertionPattern2IntervalInput: String
        val insertionIntervalInput: String
        val insertionEveryNInput: String
        val insertionProbabilityInput: String
        val insertionCooldownInput: String
        val insertionExclusive: Boolean
        val insertionEnabled = isInsertionEnabled(target)
        when (target) {
            AnimationType.READY -> {
                baseFramesInput = readyFrameInput
                baseIntervalInput = readyIntervalInput
                insertionPattern1FramesInput = readyInsertionPattern1FramesInput
                insertionPattern1WeightInput = readyInsertionPattern1WeightInput
                insertionPattern2FramesInput = readyInsertionPattern2FramesInput
                insertionPattern2WeightInput = readyInsertionPattern2WeightInput
                insertionPattern1IntervalInput = readyInsertionPattern1IntervalInput
                insertionPattern2IntervalInput = readyInsertionPattern2IntervalInput
                insertionIntervalInput = readyInsertionIntervalInput
                insertionEveryNInput = readyInsertionEveryNInput
                insertionProbabilityInput = readyInsertionProbabilityInput
                insertionCooldownInput = readyInsertionCooldownInput
                insertionExclusive = readyInsertionExclusive
            }

            AnimationType.TALKING -> {
                baseFramesInput = talkingFrameInput
                baseIntervalInput = talkingIntervalInput
                insertionPattern1FramesInput = talkingInsertionPattern1FramesInput
                insertionPattern1WeightInput = talkingInsertionPattern1WeightInput
                insertionPattern2FramesInput = talkingInsertionPattern2FramesInput
                insertionPattern2WeightInput = talkingInsertionPattern2WeightInput
                insertionPattern1IntervalInput = talkingInsertionPattern1IntervalInput
                insertionPattern2IntervalInput = talkingInsertionPattern2IntervalInput
                insertionIntervalInput = talkingInsertionIntervalInput
                insertionEveryNInput = talkingInsertionEveryNInput
                insertionProbabilityInput = talkingInsertionProbabilityInput
                insertionCooldownInput = talkingInsertionCooldownInput
                insertionExclusive = talkingInsertionExclusive
            }

            else -> {
                val state = resolveExtraState(target)
                baseFramesInput = state.frameInput
                baseIntervalInput = state.intervalInput
                insertionPattern1FramesInput = state.insertionPattern1FramesInput
                insertionPattern1WeightInput = state.insertionPattern1WeightInput
                insertionPattern2FramesInput = state.insertionPattern2FramesInput
                insertionPattern2WeightInput = state.insertionPattern2WeightInput
                insertionPattern1IntervalInput = state.insertionPattern1IntervalInput
                insertionPattern2IntervalInput = state.insertionPattern2IntervalInput
                insertionIntervalInput = state.insertionIntervalInput
                insertionEveryNInput = state.insertionEveryNInput
                insertionProbabilityInput = state.insertionProbabilityInput
                insertionCooldownInput = state.insertionCooldownInput
                insertionExclusive = state.insertionExclusive
            }
        }
        val baseFramesResult = parseFrameSequenceInput(
            input = baseFramesInput,
            frameCount = spriteSheetConfig.frameCount,
            allowDuplicates = true
        )
        val baseIntervalResult = parseIntervalMsInput(baseIntervalInput)
        val baseSettings = if (baseFramesResult.value != null && baseIntervalResult.value != null) {
            ReadyAnimationSettings(
                frameSequence = baseFramesResult.value!!,
                intervalMs = baseIntervalResult.value!!,
            )
        } else {
            applied.base
        }
        val insertionSettings = if (!insertionEnabled) {
            applied.insertion.copy(enabled = false)
        } else {
            val patternsResult = parseInsertionPatternsInput(
                pattern1FramesInput = insertionPattern1FramesInput,
                pattern1WeightInput = insertionPattern1WeightInput,
                pattern1IntervalInput = insertionPattern1IntervalInput,
                pattern2FramesInput = insertionPattern2FramesInput,
                pattern2WeightInput = insertionPattern2WeightInput,
                pattern2IntervalInput = insertionPattern2IntervalInput,
                frameCount = spriteSheetConfig.frameCount
            )
            val intervalResult = parseIntervalMsInput(insertionIntervalInput)
            val everyNResult = parseEveryNLoopsInput(insertionEveryNInput)
            val probabilityResult = parseProbabilityPercentInput(insertionProbabilityInput)
            val cooldownResult = parseCooldownLoopsInput(insertionCooldownInput)
            if (listOf(
                    patternsResult.patterns,
                    intervalResult.value,
                    everyNResult.value,
                    probabilityResult.value,
                    cooldownResult.value
                ).all { it != null }
            ) {
                InsertionAnimationSettings(
                    enabled = true,
                    patterns = patternsResult.patterns!!,
                    intervalMs = intervalResult.value!!,
                    everyNLoops = everyNResult.value!!,
                    probabilityPercent = probabilityResult.value!!,
                    cooldownLoops = cooldownResult.value!!,
                    exclusive = insertionExclusive,
                )
            } else {
                applied.insertion
            }
        }
        return AnimationDefaults(base = baseSettings, insertion = insertionSettings)
    }

    fun resolveAppliedDefaults(target: AnimationType): AnimationDefaults {
        return when (target) {
            AnimationType.READY -> AnimationDefaults(
                base = ReadyAnimationSettings(
                    frameSequence = appliedReadyFrames,
                    intervalMs = appliedReadyIntervalMs,
                ),
                insertion = InsertionAnimationSettings(
                    enabled = appliedReadyInsertionEnabled,
                    patterns = appliedReadyInsertionPatterns,
                    intervalMs = appliedReadyInsertionIntervalMs,
                    everyNLoops = appliedReadyInsertionEveryNLoops,
                    probabilityPercent = appliedReadyInsertionProbabilityPercent,
                    cooldownLoops = appliedReadyInsertionCooldownLoops,
                    exclusive = appliedReadyInsertionExclusive,
                ),
            )

            AnimationType.TALKING -> AnimationDefaults(
                base = ReadyAnimationSettings(
                    frameSequence = appliedTalkingFrames,
                    intervalMs = appliedTalkingIntervalMs,
                ),
                insertion = InsertionAnimationSettings(
                    enabled = appliedTalkingInsertionEnabled,
                    patterns = appliedTalkingInsertionPatterns,
                    intervalMs = appliedTalkingInsertionIntervalMs,
                    everyNLoops = appliedTalkingInsertionEveryNLoops,
                    probabilityPercent = appliedTalkingInsertionProbabilityPercent,
                    cooldownLoops = appliedTalkingInsertionCooldownLoops,
                    exclusive = appliedTalkingInsertionExclusive,
                ),
            )

            else -> {
                val state = resolveExtraState(target)
                AnimationDefaults(
                    base = state.appliedBase,
                    insertion = state.appliedInsertion,
                )
            }
        }
    }

    // 保存用: 全アニメ設定をDataStore向けJSONに変換する。
    fun buildAllAnimationsJsonForStorage(): String {
        val animationsObject = JSONObject()
        AnimationType.options.forEach { type ->
            val defaults = resolveAppliedDefaults(type)
            val key = when (type) {
                AnimationType.READY -> ALL_ANIMATIONS_READY_KEY
                AnimationType.TALKING -> ALL_ANIMATIONS_TALKING_KEY
                AnimationType.THINKING -> ALL_ANIMATIONS_THINKING_KEY
                else -> type.internalKey
            }
            animationsObject.put(
                key,
                JSONObject()
                    .put(JSON_BASE_KEY, defaults.base.toJsonObject())
                    .put(JSON_INSERTION_KEY, defaults.insertion.toJsonObject())
            )
        }
        return JSONObject()
            .put(JSON_VERSION_KEY, ALL_ANIMATIONS_JSON_VERSION)
            .put(JSON_ANIMATIONS_KEY, animationsObject)
            .toString()
    }

    // メモ: 全アニメJSONは animationType/internalKey と混同しないよう、表示名(Ready等)をキーに統一する。
    fun exportAllAnimationsToJson(): String {
        val animationsObject = JSONObject()
        AnimationType.options.forEach { type ->
            val defaults = resolveEditingOrApplied(type)
            animationsObject.put(
                type.displayLabel,
                JSONObject()
                    .put(JSON_BASE_KEY, defaults.base.toJsonObject())
                    .put(JSON_INSERTION_KEY, defaults.insertion.toJsonObject())
            )
        }
        return JSONObject()
            .put(JSON_VERSION_KEY, ALL_ANIMATIONS_JSON_VERSION)
            .put(JSON_ANIMATIONS_KEY, animationsObject)
            .toString(2)
    }

    fun importAllAnimationsFromJson(
        json: String,
        defaults: Map<AnimationType, AnimationDefaults> = resolveDefaultAnimations(),
    ): ValidationResult<AllAnimations> {
        val root = runCatching { JSONObject(json) }.getOrElse {
            return ValidationResult(null, "JSONの形式が不正です")
        }
        val version = root.optInt(JSON_VERSION_KEY, -1)
        if (version != ALL_ANIMATIONS_JSON_VERSION) {
            return ValidationResult(null, "versionが不正です")
        }
        val animationsObject = root.optJSONObject(JSON_ANIMATIONS_KEY)
            ?: return ValidationResult(null, "animationsがありません")
        val resolved = defaults.toMutableMap()
        for (type in AnimationType.options) {
            val key = type.displayLabel
            val animationObject = if (type == AnimationType.READY) {
                animationsObject.optJSONObject(key) ?: animationsObject.optJSONObject(READY_LEGACY_LABEL)
            } else {
                animationsObject.optJSONObject(key)
            } ?: continue
            val baseObject = animationObject.optJSONObject(JSON_BASE_KEY)
            val insertionObject = animationObject.optJSONObject(JSON_INSERTION_KEY)
            val baseResult = parseBaseFromJson(baseObject, defaults.getValue(type).base)
            if (baseResult.error != null) {
                return ValidationResult(null, "${key}.base: ${baseResult.error}")
            }
            val insertionResult = parseInsertionFromJson(insertionObject, defaults.getValue(type).insertion)
            if (insertionResult.error != null) {
                return ValidationResult(null, "${key}.insertion: ${insertionResult.error}")
            }
            resolved[type] = AnimationDefaults(
                base = baseResult.value ?: defaults.getValue(type).base,
                insertion = insertionResult.value ?: defaults.getValue(type).insertion,
            )
        }
        return ValidationResult(AllAnimations(resolved), null)
    }

    // 段階1: spriteAnimationsJson があれば優先しつつ、UI反映は段階2以降で実装予定。
    @Suppress("UNUSED_VARIABLE")
    val compositeAnimations = remember(
        spriteAnimationsJson,
        readyAnimationSettings,
        talkingAnimationSettings,
        readyInsertionAnimationSettings,
        talkingInsertionAnimationSettings,
    ) {
        val legacyDefaults = resolveLegacyDefaultsFromSettings()
        val json = spriteAnimationsJson?.takeIf { value: String ->
            value.isNotBlank()
        } ?: return@remember legacyDefaults
        importAllAnimationsFromJson(json, legacyDefaults).value?.animations ?: legacyDefaults
    }

    // 保存処理は全アニメJSONに統一しつつ、必要ならlegacy保存も併用する。
    fun persistAllAnimationsJson(onLegacySave: suspend () -> Unit = {}) {
        coroutineScope.launch {
            var rawJsonLength: Int? = null
            var normalizedJsonLength: Int? = null
            val spriteAnimationsJsonSnapshot = spriteAnimationsJson
            val storedJsonLength = when {
                spriteAnimationsJsonSnapshot == null -> "null"
                spriteAnimationsJsonSnapshot.isBlank() -> "blank"
                else -> spriteAnimationsJsonSnapshot.length.toString()
            }
            runCatching {
                val rawJson = buildAllAnimationsJsonForStorage()
                rawJsonLength = rawJson.length
                val normalizedJson = settingsPreferences.parseAndValidateAllAnimationsJson(rawJson).getOrThrow()
                normalizedJsonLength = normalizedJson.length
                settingsPreferences.saveSpriteAnimationsJson(normalizedJson)
                onLegacySave()
            }.onSuccess {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "LamiSprite",
                        "persistAllAnimationsJson success: type=${selectedAnimation.name} " +
                            "key=${selectedAnimation.internalKey} label=${selectedAnimation.displayLabel} " +
                            "rawLen=${rawJsonLength ?: "null"} normalizedLen=${normalizedJsonLength ?: "null"} " +
                            "storedLen=$storedJsonLength"
                    )
                }
                showTopSnackbarSuccess("保存しました")
            }.onFailure { throwable ->
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "LamiSprite",
                        "persistAllAnimationsJson failure: type=${selectedAnimation.name} " +
                            "key=${selectedAnimation.internalKey} label=${selectedAnimation.displayLabel} " +
                            "rawLen=${rawJsonLength ?: "null"} normalizedLen=${normalizedJsonLength ?: "null"} " +
                            "storedLen=$storedJsonLength error=${throwable.message}"
                    )
                }
                showTopSnackbarError("保存に失敗しました: ${throwable.message}")
            }
        }
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
                showTopSnackbarSuccess("保存しました")
            }.onFailure { throwable ->
                showTopSnackbarError("保存に失敗しました: ${throwable.message}")
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
                showTopSnackbarSuccess("JSONをコピーしました")
            }.onFailure { throwable ->
                showTopSnackbarError("コピーに失敗しました: ${throwable.message}")
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
                    patterns = appliedReadyInsertionPatterns,
                    intervalMs = appliedReadyInsertionIntervalMs,
                    everyNLoops = appliedReadyInsertionEveryNLoops,
                    probabilityPercent = appliedReadyInsertionProbabilityPercent,
                    cooldownLoops = appliedReadyInsertionCooldownLoops,
                    exclusive = appliedReadyInsertionExclusive,
                )
                val talkingInsertion = InsertionAnimationSettings(
                    enabled = appliedTalkingInsertionEnabled,
                    patterns = appliedTalkingInsertionPatterns,
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
                showTopSnackbarSuccess("設定JSONをコピーしました")
            }.onFailure { throwable ->
                showTopSnackbarError("コピーに失敗しました: ${throwable.message}")
            }
        }
    }

    fun copyEditingSettings(devSettings: DevPreviewSettings) {
        coroutineScope.launch {
            val validatedBase = validateBaseInputs(selectedAnimation) ?: run {
                showTopSnackbarError("入力が不正です")
                return@launch
            }
            val validatedInsertion = if (isInsertionEnabled(selectedAnimation)) {
                validateInsertionInputs(selectedAnimation) ?: run {
                    showTopSnackbarError("入力が不正です")
                    return@launch
                }
            } else null
            runCatching {
                val config = buildSpriteSheetConfig()
                val error = config.validate()
                if (error != null) {
                    throw IllegalArgumentException(error)
                }
                val readyBase = when (selectedAnimation) {
                    AnimationType.READY -> validatedBase
                    AnimationType.TALKING -> ReadyAnimationSettings(
                        frameSequence = appliedReadyFrames,
                        intervalMs = appliedReadyIntervalMs
                    )
                    else -> ReadyAnimationSettings(
                        frameSequence = appliedReadyFrames,
                        intervalMs = appliedReadyIntervalMs
                    )
                }
                val talkingBase = when (selectedAnimation) {
                    AnimationType.READY -> ReadyAnimationSettings(
                        frameSequence = appliedTalkingFrames,
                        intervalMs = appliedTalkingIntervalMs
                    )
                    AnimationType.TALKING -> validatedBase
                    else -> ReadyAnimationSettings(
                        frameSequence = appliedTalkingFrames,
                        intervalMs = appliedTalkingIntervalMs
                    )
                }
                val readyInsertion = if (selectedAnimation == AnimationType.READY) {
                    validatedInsertion ?: InsertionAnimationSettings(
                        enabled = false,
                        patterns = appliedReadyInsertionPatterns,
                        intervalMs = appliedReadyInsertionIntervalMs,
                        everyNLoops = appliedReadyInsertionEveryNLoops,
                        probabilityPercent = appliedReadyInsertionProbabilityPercent,
                        cooldownLoops = appliedReadyInsertionCooldownLoops,
                        exclusive = appliedReadyInsertionExclusive,
                    )
                } else {
                    InsertionAnimationSettings(
                        enabled = appliedReadyInsertionEnabled,
                        patterns = appliedReadyInsertionPatterns,
                        intervalMs = appliedReadyInsertionIntervalMs,
                        everyNLoops = appliedReadyInsertionEveryNLoops,
                        probabilityPercent = appliedReadyInsertionProbabilityPercent,
                        cooldownLoops = appliedReadyInsertionCooldownLoops,
                        exclusive = appliedReadyInsertionExclusive,
                    )
                }
                val talkingInsertion = if (selectedAnimation == AnimationType.TALKING) {
                    validatedInsertion ?: InsertionAnimationSettings(
                        enabled = false,
                        patterns = appliedTalkingInsertionPatterns,
                        intervalMs = appliedTalkingInsertionIntervalMs,
                        everyNLoops = appliedTalkingInsertionEveryNLoops,
                        probabilityPercent = appliedTalkingInsertionProbabilityPercent,
                        cooldownLoops = appliedTalkingInsertionCooldownLoops,
                        exclusive = appliedTalkingInsertionExclusive,
                    )
                } else {
                    InsertionAnimationSettings(
                        enabled = appliedTalkingInsertionEnabled,
                        patterns = appliedTalkingInsertionPatterns,
                        intervalMs = appliedTalkingInsertionIntervalMs,
                        everyNLoops = appliedTalkingInsertionEveryNLoops,
                        probabilityPercent = appliedTalkingInsertionProbabilityPercent,
                        cooldownLoops = appliedTalkingInsertionCooldownLoops,
                        exclusive = appliedTalkingInsertionExclusive,
                    )
                }
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
                showTopSnackbarSuccess("編集中の設定をコピーしました")
            }.onFailure { throwable ->
                showTopSnackbarError("コピーに失敗しました: ${throwable.message}")
            }
        }
    }

    // [非dp] 下: IME の insets(インセット)に関係
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val onAnimationApply: () -> Unit = onAnimationApply@{
        val validatedBase = validateBaseInputs(selectedAnimation) ?: run {
            showTopSnackbarError("入力が不正です")
            return@onAnimationApply
        }
        val validatedInsertion = if (isInsertionEnabled(selectedAnimation)) {
            validateInsertionInputs(selectedAnimation) ?: run {
                showTopSnackbarError("入力が不正です")
                return@onAnimationApply
            }
        } else null
        when (selectedAnimation) {
            AnimationType.READY -> {
                appliedReadyFrames = validatedBase.frameSequence
                appliedReadyIntervalMs = validatedBase.intervalMs
                val insertion = validatedInsertion ?: InsertionAnimationSettings(
                    enabled = false,
                    patterns = appliedReadyInsertionPatterns,
                    intervalMs = appliedReadyInsertionIntervalMs,
                    everyNLoops = appliedReadyInsertionEveryNLoops,
                    probabilityPercent = appliedReadyInsertionProbabilityPercent,
                    cooldownLoops = appliedReadyInsertionCooldownLoops,
                    exclusive = appliedReadyInsertionExclusive,
                )
                appliedReadyInsertionEnabled = insertion.enabled
                appliedReadyInsertionPatterns = insertion.patterns
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
                    patterns = appliedTalkingInsertionPatterns,
                    intervalMs = appliedTalkingInsertionIntervalMs,
                    everyNLoops = appliedTalkingInsertionEveryNLoops,
                    probabilityPercent = appliedTalkingInsertionProbabilityPercent,
                    cooldownLoops = appliedTalkingInsertionCooldownLoops,
                    exclusive = appliedTalkingInsertionExclusive,
                )
                appliedTalkingInsertionEnabled = insertion.enabled
                appliedTalkingInsertionPatterns = insertion.patterns
                appliedTalkingInsertionIntervalMs = insertion.intervalMs
                appliedTalkingInsertionEveryNLoops = insertion.everyNLoops
                appliedTalkingInsertionProbabilityPercent = insertion.probabilityPercent
                appliedTalkingInsertionCooldownLoops = insertion.cooldownLoops
                appliedTalkingInsertionExclusive = insertion.exclusive
            }

            else -> {
                val current = resolveExtraState(selectedAnimation)
                val insertion = validatedInsertion ?: current.appliedInsertion.copy(enabled = false)
                updateExtraState(selectedAnimation) { state ->
                    state.copy(
                        appliedBase = validatedBase,
                        appliedInsertion = insertion,
                    )
                }
            }
        }
        showTopSnackbarSuccess("プレビューに適用しました")
    }

    val onAnimationSave: () -> Unit = onAnimationSave@{
        val validatedBase = validateBaseInputs(selectedAnimation) ?: run {
            showTopSnackbarError("入力が不正です")
            return@onAnimationSave
        }
        val validatedInsertion = if (isInsertionEnabled(selectedAnimation)) {
            validateInsertionInputs(selectedAnimation) ?: run {
                showTopSnackbarError("入力が不正です")
                return@onAnimationSave
            }
        } else null
        when (selectedAnimation) {
            AnimationType.READY -> {
                appliedReadyFrames = validatedBase.frameSequence
                appliedReadyIntervalMs = validatedBase.intervalMs
                val insertion = validatedInsertion ?: InsertionAnimationSettings(
                    enabled = false,
                    patterns = appliedReadyInsertionPatterns,
                    intervalMs = appliedReadyInsertionIntervalMs,
                    everyNLoops = appliedReadyInsertionEveryNLoops,
                    probabilityPercent = appliedReadyInsertionProbabilityPercent,
                    cooldownLoops = appliedReadyInsertionCooldownLoops,
                    exclusive = appliedReadyInsertionExclusive,
                )
                appliedReadyInsertionEnabled = insertion.enabled
                appliedReadyInsertionPatterns = insertion.patterns
                appliedReadyInsertionIntervalMs = insertion.intervalMs
                appliedReadyInsertionEveryNLoops = insertion.everyNLoops
                appliedReadyInsertionProbabilityPercent = insertion.probabilityPercent
                appliedReadyInsertionCooldownLoops = insertion.cooldownLoops
                appliedReadyInsertionExclusive = insertion.exclusive
                persistAllAnimationsJson(
                    onLegacySave = {
                        settingsPreferences.saveReadyAnimationSettings(validatedBase)
                        settingsPreferences.saveReadyInsertionAnimationSettings(insertion)
                    }
                )
            }

            AnimationType.TALKING -> {
                appliedTalkingFrames = validatedBase.frameSequence
                appliedTalkingIntervalMs = validatedBase.intervalMs
                val insertion = validatedInsertion ?: InsertionAnimationSettings(
                    enabled = false,
                    patterns = appliedTalkingInsertionPatterns,
                    intervalMs = appliedTalkingInsertionIntervalMs,
                    everyNLoops = appliedTalkingInsertionEveryNLoops,
                    probabilityPercent = appliedTalkingInsertionProbabilityPercent,
                    cooldownLoops = appliedTalkingInsertionCooldownLoops,
                    exclusive = appliedTalkingInsertionExclusive,
                )
                appliedTalkingInsertionEnabled = insertion.enabled
                appliedTalkingInsertionPatterns = insertion.patterns
                appliedTalkingInsertionIntervalMs = insertion.intervalMs
                appliedTalkingInsertionEveryNLoops = insertion.everyNLoops
                appliedTalkingInsertionProbabilityPercent = insertion.probabilityPercent
                appliedTalkingInsertionCooldownLoops = insertion.cooldownLoops
                appliedTalkingInsertionExclusive = insertion.exclusive
                persistAllAnimationsJson(
                    onLegacySave = {
                        settingsPreferences.saveTalkingAnimationSettings(validatedBase)
                        settingsPreferences.saveTalkingInsertionAnimationSettings(insertion)
                    }
                )
            }

            else -> {
                val current = resolveExtraState(selectedAnimation)
                val insertion = validatedInsertion ?: current.appliedInsertion.copy(enabled = false)
                updateExtraState(selectedAnimation) { state ->
                    state.copy(
                        appliedBase = validatedBase,
                        appliedInsertion = insertion,
                    )
                }
                persistAllAnimationsJson()
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
                        navigateBackWithFallback()
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
    val actionButtonShape = RoundedCornerShape(999.dp)
    // [dp] 縦: 画面全体 の最小サイズ(最小サイズ)に関係
    val controlButtonHeight = 32.dp // 下部操作ボタンの見た目高さを統一
    // [dp] 縦横: 画面全体 の余白(余白)に関係
    val controlButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)

    val onMove: (Int, Int) -> Unit = { deltaX, deltaY -> updateSelectedPosition(deltaX, deltaY) }
    val onPrev: () -> Unit = { selectedNumber = if (selectedNumber <= 1) 9 else selectedNumber - 1 }
    val onNext: () -> Unit = { selectedNumber = if (selectedNumber >= 9) 1 else selectedNumber + 1 }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
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
                    actions = {
                        IconButton(
                            onClick = {
                                when (selectedTab) {
                                    SpriteTab.ANIM -> onAnimationApply()
                                    SpriteTab.ADJUST -> showTopSnackbarSuccess("プレビューに適用しました")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "プレビュー更新"
                            )
                        }
                        IconButton(
                            onClick = {
                                when (selectedTab) {
                                    SpriteTab.ANIM -> copyEditingSettings(devPreviewSettings)
                                    SpriteTab.ADJUST -> copySpriteSheetConfig()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "コピー"
                            )
                        }
                        IconButton(
                            onClick = {
                                when (selectedTab) {
                                    SpriteTab.ANIM -> onAnimationSave()
                                    SpriteTab.ADJUST -> saveSpriteSheetConfig()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = "保存"
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = adaptiveHorizontalPadding)
                )
            }
        },
        bottomBar = {
            if (selectedTab == SpriteTab.ADJUST) {
                SpriteSettingsControls(
                    buttonHeight = controlButtonHeight,
                    buttonContentPadding = controlButtonPadding,
                    buttonShape = actionButtonShape,
                    onPrev = onPrev,
                    onNext = onNext,
                    onMoveXNegative = { onMove(-1, 0) },
                    onMoveXPositive = { onMove(1, 0) },
                    onMoveYNegative = { onMove(0, -1) },
                    onMoveYPositive = { onMove(0, 1) },
                    onSizeDecrease = { updateBoxSize(-4) },
                    onSizeIncrease = { updateBoxSize(4) }
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        Box(
            // [非dp] 縦横: 画面全体 の fillMaxSize(制約)に関係
            modifier = Modifier
                .fillMaxSize()
                // [非dp] 四方向: Scaffold の innerPadding を Box で受ける(インセット)
                .padding(innerPadding)
        ) {
            val contentPadding = PaddingValues(
                // [dp] 左右: 画面全体 の余白(余白)に関係
                start = adaptiveHorizontalPadding,
                // [dp] 上: Scaffold の innerPadding を Box 側で適用済みのため 0.dp
                top = 0.dp,
                // [dp] 左右: 画面全体 の余白(余白)に関係
                end = adaptiveHorizontalPadding,
                // [dp] 下: Scaffold の innerPadding を Box 側で適用済みのため 0.dp
                bottom = 0.dp
            )

            Column(
                // [非dp] 縦横: 画面全体 の fillMaxSize(制約)に関係
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Surface(
                    // [非dp] 縦: 画面全体 の weight(制約)に関係
                    modifier = Modifier
                        .fillMaxWidth()
                        // [非dp] 横: 画面全体 の fillMaxWidth(制約)に関係
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            // [非dp] 縦横: 画面全体 の fillMaxSize(制約)に関係
                            .fillMaxSize()
                            // [非dp] 四方向: Box の contentPadding(インセット)に関係
                            .padding(contentPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        if (!didRestoreTab) {
                            Box(
                                modifier = Modifier
                                    // [dp] 縦: 復元待ちプレースホルダの最小高さ(余白)に関係
                                    .height(1.dp)
                                    .fillMaxWidth()
                            )
                        } else {
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
                        val contentTopGap = if (selectedTab == SpriteTab.ADJUST) 0.dp else 12.dp
                        // [dp] 上: TabRow の帯/位置を固定するため、コンテンツ側で上余白を調整
                        Spacer(modifier = Modifier.height(contentTopGap))
                        Box(
                            modifier = Modifier
                                // [非dp] 横: 画面全体 の fillMaxWidth(制約)に関係
                                .fillMaxWidth()
                                // [非dp] 縦: 画面全体 の weight(制約)に関係
                                .fillMaxWidth()
                        ) {
                            val animationTabContent: @Composable () -> Unit = {
                                val animationOptions = remember { AnimationType.options }
                                val selectedState = resolveInputState(selectedAnimation)
                                val selectedBaseSummary = remember(
                                    selectedAnimation,
                                    selectedState.appliedBase.frameSequence,
                                    selectedState.appliedBase.intervalMs,
                                ) {
                                    AnimationSummary(
                                        label = selectedAnimation.displayLabel,
                                        frames = selectedState.appliedBase.frameSequence,
                                        intervalMs = selectedState.appliedBase.intervalMs,
                                    )
                                }
                                val selectedInsertionPreview = remember(
                                    selectedState.insertionPattern1FramesInput,
                                    selectedState.insertionPattern1WeightInput,
                                    selectedState.insertionPattern2FramesInput,
                                    selectedState.insertionPattern2WeightInput,
                                    selectedState.insertionIntervalInput,
                                    selectedState.insertionEveryNInput,
                                    selectedState.insertionProbabilityInput,
                                    selectedState.insertionCooldownInput,
                                    selectedState.insertionEnabled,
                                    selectedState.insertionExclusive,
                                    selectedState.appliedInsertion.intervalMs,
                                    spriteSheetConfig.frameCount,
                                ) {
                                    buildInsertionPreviewSummary(
                                        label = "挿入",
                                        enabled = selectedState.insertionEnabled,
                                        pattern1FramesInput = selectedState.insertionPattern1FramesInput,
                                        pattern1WeightInput = selectedState.insertionPattern1WeightInput,
                                        pattern2FramesInput = selectedState.insertionPattern2FramesInput,
                                        pattern2WeightInput = selectedState.insertionPattern2WeightInput,
                                        intervalInput = selectedState.insertionIntervalInput,
                                        everyNInput = selectedState.insertionEveryNInput,
                                        probabilityInput = selectedState.insertionProbabilityInput,
                                        cooldownInput = selectedState.insertionCooldownInput,
                                        exclusive = selectedState.insertionExclusive,
                                        defaultIntervalMs = selectedState.appliedInsertion.intervalMs,
                                        frameCount = spriteSheetConfig.frameCount,
                                    )
                                }
                                val (selectedInsertionSummary, selectedInsertionPreviewValues) = selectedInsertionPreview
                                val selectionState = AnimationSelectionState(
                                    selectedAnimation = selectedAnimation,
                                    animationOptions = animationOptions,
                                    onSelectedAnimationChange = { updated ->
                                        if (selectedAnimation != updated) {
                                            selectedAnimation = updated
                                            coroutineScope.launch {
                                                // 段階移行：IDLEは新DataStoreへ保存のみ先行（復元は次PR/PR7）
                                                // 段階移行：READY/SPEAKING は保存のみ先行
                                                // 段階移行：THINKINGは新DataStoreへ保存のみ先行（復元は次PR/PR9）
                                                // 旧キー（sprite_last_selected_animation）も互換のため継続保存
                                                when (updated) {
                                                    AnimationType.IDLE -> settingsPreferences.setSelectedKey(
                                                        SpriteState.IDLE,
                                                        "Idle",
                                                    )

                                                    AnimationType.READY -> settingsPreferences.setSelectedKey(
                                                        SpriteState.READY,
                                                        "Ready",
                                                    )

                                                    AnimationType.TALK_SHORT,
                                                    AnimationType.TALK_LONG,
                                                    AnimationType.TALK_CALM ->
                                                        settingsPreferences.setSelectedKey(
                                                            SpriteState.SPEAKING,
                                                            updated.internalKey,
                                                        )

                                                    // TalkDefault は enum に無いため TALKING を TalkDefault として保存
                                                    AnimationType.TALKING -> settingsPreferences.setSelectedKey(
                                                        SpriteState.SPEAKING,
                                                        "TalkDefault",
                                                    )

                                                    AnimationType.THINKING -> settingsPreferences.setSelectedKey(
                                                        SpriteState.THINKING,
                                                        "Thinking",
                                                    )

                                                    else -> Unit
                                                }
                                                settingsPreferences.saveLastSelectedAnimation(updated.internalKey)
                                            }
                                        }
                                    }
                                )
                                val baseState = BaseAnimationUiState(
                                    frameInput = selectedState.frameInput,
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

                                            else -> updateExtraState(selectedAnimation) { state ->
                                                state.copy(
                                                    frameInput = updated,
                                                    framesError = null,
                                                )
                                            }
                                        }
                                    },
                                    intervalInput = selectedState.intervalInput,
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

                                            else -> updateExtraState(selectedAnimation) { state ->
                                                state.copy(
                                                    intervalInput = updated,
                                                    intervalError = null,
                                                )
                                            }
                                        }
                                    },
                                    framesError = selectedState.framesError,
                                    intervalError = selectedState.intervalError,
                                    summary = selectedBaseSummary
                                )
                                val insertionState = InsertionAnimationUiState(
                                    pattern1FramesInput = selectedState.insertionPattern1FramesInput,
                                    onPattern1FramesInputChange = { updated ->
                                        when (selectedAnimation) {
                                            AnimationType.READY -> {
                                                readyInsertionPattern1FramesInput = updated
                                                readyInsertionPattern1FramesError = null
                                            }

                                            AnimationType.TALKING -> {
                                                talkingInsertionPattern1FramesInput = updated
                                                talkingInsertionPattern1FramesError = null
                                            }

                                            else -> updateExtraState(selectedAnimation) { state ->
                                                state.copy(
                                                    insertionPattern1FramesInput = updated,
                                                    insertionPattern1FramesError = null,
                                                )
                                            }
                                        }
                                    },
                                    pattern1WeightInput = selectedState.insertionPattern1WeightInput,
                                    onPattern1WeightInputChange = { updated ->
                                        when (selectedAnimation) {
                                            AnimationType.READY -> {
                                                readyInsertionPattern1WeightInput = updated
                                                readyInsertionPattern1WeightError = null
                                            }

                                            AnimationType.TALKING -> {
                                                talkingInsertionPattern1WeightInput = updated
                                                talkingInsertionPattern1WeightError = null
                                            }

                                            else -> updateExtraState(selectedAnimation) { state ->
                                                state.copy(
                                                    insertionPattern1WeightInput = updated,
                                                    insertionPattern1WeightError = null,
                                                )
                                            }
                                        }
                                    },
                                    pattern1IntervalInput = selectedState.insertionPattern1IntervalInput,
                                    onPattern1IntervalInputChange = { updated ->
                                        when (selectedAnimation) {
                                            AnimationType.READY -> {
                                                readyInsertionPattern1IntervalInput = updated
                                                readyInsertionPattern1IntervalError = null
                                            }

                                            AnimationType.TALKING -> {
                                                talkingInsertionPattern1IntervalInput = updated
                                                talkingInsertionPattern1IntervalError = null
                                            }

                                            else -> updateExtraState(selectedAnimation) { state ->
                                                state.copy(
                                                    insertionPattern1IntervalInput = updated,
                                                    insertionPattern1IntervalError = null,
                                                )
                                            }
                                        }
                                    },
                                    pattern2FramesInput = selectedState.insertionPattern2FramesInput,
                                    onPattern2FramesInputChange = { updated ->
                                        when (selectedAnimation) {
                                            AnimationType.READY -> {
                                                readyInsertionPattern2FramesInput = updated
                                                readyInsertionPattern2FramesError = null
                                            }

                                            AnimationType.TALKING -> {
                                                talkingInsertionPattern2FramesInput = updated
                                                talkingInsertionPattern2FramesError = null
                                            }

                                            else -> updateExtraState(selectedAnimation) { state ->
                                                state.copy(
                                                    insertionPattern2FramesInput = updated,
                                                    insertionPattern2FramesError = null,
                                                )
                                            }
                                        }
                                    },
                                    pattern2WeightInput = selectedState.insertionPattern2WeightInput,
                                    onPattern2WeightInputChange = { updated ->
                                        when (selectedAnimation) {
                                            AnimationType.READY -> {
                                                readyInsertionPattern2WeightInput = updated
                                                readyInsertionPattern2WeightError = null
                                            }

                                            AnimationType.TALKING -> {
                                                talkingInsertionPattern2WeightInput = updated
                                                talkingInsertionPattern2WeightError = null
                                            }

                                            else -> updateExtraState(selectedAnimation) { state ->
                                                state.copy(
                                                    insertionPattern2WeightInput = updated,
                                                    insertionPattern2WeightError = null,
                                                )
                                            }
                                        }
                                    },
                                    pattern2IntervalInput = selectedState.insertionPattern2IntervalInput,
                                    onPattern2IntervalInputChange = { updated ->
                                        when (selectedAnimation) {
                                            AnimationType.READY -> {
                                                readyInsertionPattern2IntervalInput = updated
                                                readyInsertionPattern2IntervalError = null
                                            }

                                            AnimationType.TALKING -> {
                                                talkingInsertionPattern2IntervalInput = updated
                                                talkingInsertionPattern2IntervalError = null
                                            }

                                            else -> updateExtraState(selectedAnimation) { state ->
                                                state.copy(
                                                    insertionPattern2IntervalInput = updated,
                                                    insertionPattern2IntervalError = null,
                                                )
                                            }
                                        }
                                    },
                                    intervalInput = selectedState.insertionIntervalInput,
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

                                            else -> updateExtraState(selectedAnimation) { state ->
                                                state.copy(
                                                    insertionIntervalInput = updated,
                                                    insertionIntervalError = null,
                                                )
                                            }
                                        }
                                    },
                                    everyNInput = selectedState.insertionEveryNInput,
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

                                            else -> updateExtraState(selectedAnimation) { state ->
                                                state.copy(
                                                    insertionEveryNInput = updated,
                                                    insertionEveryNError = null,
                                                )
                                            }
                                        }
                                    },
                                    probabilityInput = selectedState.insertionProbabilityInput,
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

                                            else -> updateExtraState(selectedAnimation) { state ->
                                                state.copy(
                                                    insertionProbabilityInput = updated,
                                                    insertionProbabilityError = null,
                                                )
                                            }
                                        }
                                    },
                                    cooldownInput = selectedState.insertionCooldownInput,
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

                                            else -> updateExtraState(selectedAnimation) { state ->
                                                state.copy(
                                                    insertionCooldownInput = updated,
                                                    insertionCooldownError = null,
                                                )
                                            }
                                        }
                                    },
                                    enabled = selectedState.insertionEnabled,
                                    onEnabledChange = { checked ->
                                        when (selectedAnimation) {
                                            AnimationType.READY -> readyInsertionEnabled = checked
                                            AnimationType.TALKING -> talkingInsertionEnabled = checked
                                            else -> updateExtraState(selectedAnimation) { state ->
                                                state.copy(insertionEnabled = checked)
                                            }
                                        }
                                    },
                                    exclusive = selectedState.insertionExclusive,
                                    onExclusiveChange = { checked ->
                                        when (selectedAnimation) {
                                            AnimationType.READY -> readyInsertionExclusive = checked
                                            AnimationType.TALKING -> talkingInsertionExclusive = checked
                                            else -> updateExtraState(selectedAnimation) { state ->
                                                state.copy(insertionExclusive = checked)
                                            }
                                        }
                                    },
                                    pattern1FramesError = selectedState.insertionPattern1FramesError,
                                    pattern1WeightError = selectedState.insertionPattern1WeightError,
                                    pattern1IntervalError = selectedState.insertionPattern1IntervalError,
                                    pattern2FramesError = selectedState.insertionPattern2FramesError,
                                    pattern2WeightError = selectedState.insertionPattern2WeightError,
                                    pattern2IntervalError = selectedState.insertionPattern2IntervalError,
                                    intervalError = selectedState.insertionIntervalError,
                                    everyNError = selectedState.insertionEveryNError,
                                    probabilityError = selectedState.insertionProbabilityError,
                                    cooldownError = selectedState.insertionCooldownError,
                                    patterns = selectedState.appliedInsertion.patterns,
                                    defaultIntervalMs = selectedState.appliedInsertion.intervalMs,
                                    defaults = selectedState.appliedInsertion,
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
                                // delegated property のため smart cast 不可→ローカル束縛
                                val bmp = imageBitmap
                                val previewHeaderText = if (bmp != null) {
                                    "${bmp.width}×${bmp.height} / ${"%.2f".format(displayScale)}x"
                                } else {
                                    "画像読み込み中"
                                }
                                val coordinateText =
                                    selectedPosition?.let { position ->
                                        "座標: ${position.x},${position.y},${boxSizePx},${boxSizePx}"
                                    } ?: "座標: -, -, -, -"
                                val statusLine1Text = previewHeaderText
                                val statusLine2Text = "選択中: ${selectedNumber}/9 | サイズ: ${boxSizePx}px | $coordinateText"
                                val statusTextStyle = MaterialTheme.typography.labelMedium.copy(
                                    lineHeight = MaterialTheme.typography.labelMedium.fontSize
                                )
                                Box(
                                    modifier = Modifier
                                        // [非dp] 縦横: プレビュー の fillMaxSize(制約)に関係
                                        .fillMaxSize(),
                                    // [非dp] 縦: プレビュー/ステータス の上寄せ(配置)に関係
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    SpritePreviewBlock(
                                        imageBitmap = bmp,
                                        modifier = Modifier
                                            // [非dp] 横: プレビュー の fillMaxWidth(制約)に関係
                                            .fillMaxWidth()
                                            // [dp] 上: プレビュー の余白(余白)に関係
                                            .padding(top = 2.dp)
                                            // [非dp] 上: プレビュー の配置(配置)に関係
                                            .align(Alignment.TopCenter),
                                        onContainerSizeChanged = { newContainerSize: IntSize ->
                                            containerSize = newContainerSize
                                            if (bmp != null && bmp.width != 0) {
                                                displayScale = newContainerSize.width / bmp.width.toFloat()
                                            }
                                        },
                                        overlayContent = {
                                            if (bmp != null &&
                                                selectedPosition != null &&
                                                containerSize.width > 0 &&
                                                containerSize.height > 0
                                            ) {
                                                Canvas(modifier = Modifier.fillMaxSize()) {
                                                    val scaleX = this.size.width / bmp.width
                                                    val scaleY = this.size.height / bmp.height
                                                    val scale = min(scaleX, scaleY)
                                                    val destinationWidth = bmp.width * scale
                                                    val destinationHeight = bmp.height * scale
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
                                    Column(
                                        modifier = Modifier
                                            // [非dp] 下: ステータス行 の配置(配置)に関係
                                            .align(Alignment.BottomStart)
                                            // [非dp] 横: ステータス行 の fillMaxWidth(制約)に関係
                                            .fillMaxWidth()
                                            // [dp] 左右: ステータス行 の余白(余白)に関係
                                            .padding(horizontal = 12.dp)
                                            // [dp] 上下: ステータス行 の余白(余白)に関係
                                            .padding(vertical = 8.dp),
                                        // [dp] 縦: ステータス行 の間隔(間隔)に関係
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = statusLine1Text,
                                            style = statusTextStyle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = statusLine2Text,
                                            style = statusTextStyle,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
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
            // 上: TabRow/コンテンツの上に重ねる Snackbar の配置(配置)に関係
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(10f),
            ) { data ->
                val isError = data.visuals.actionLabel == "ERROR"
                val containerColor = if (isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.inverseSurface
                }
                val contentColor = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.inverseOnSurface
                }
                Snackbar(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = containerColor,
                    contentColor = contentColor
                ) {
                    Text(text = data.visuals.message)
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
    val pattern1FramesInput: String,
    val onPattern1FramesInputChange: (String) -> Unit,
    val pattern1WeightInput: String,
    val onPattern1WeightInputChange: (String) -> Unit,
    val pattern1IntervalInput: String,
    val onPattern1IntervalInputChange: (String) -> Unit,
    val pattern2FramesInput: String,
    val onPattern2FramesInputChange: (String) -> Unit,
    val pattern2WeightInput: String,
    val onPattern2WeightInputChange: (String) -> Unit,
    val pattern2IntervalInput: String,
    val onPattern2IntervalInputChange: (String) -> Unit,
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
    val pattern1FramesError: String?,
    val pattern1WeightError: String?,
    val pattern1IntervalError: String?,
    val pattern2FramesError: String?,
    val pattern2WeightError: String?,
    val pattern2IntervalError: String?,
    val intervalError: String?,
    val everyNError: String?,
    val probabilityError: String?,
    val cooldownError: String?,
    val patterns: List<InsertionPattern>,
    val defaultIntervalMs: Int,
    val defaults: InsertionAnimationSettings,
    val summary: AnimationSummary,
    val previewValues: InsertionPreviewValues,
)

@Composable
private fun ReadyAnimationTab(
    imageBitmap: ImageBitmap?,
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
    val baseMaxHeightDp = 300
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
    val imeBottomPaddingDp = with(density) {
        imeBottomPx.toDp()
    }
    val imeExtraPadding = if (isImeVisible) 14.dp else 0.dp
    // [dp] 下: IME の insets(インセット)に関係
    val listBottomPadding = if (isImeVisible) {
        imeBottomPaddingDp + imeExtraPadding
    } else {
        0.dp
    }
    // [dp] 四方向: リスト(アニメタブ) の余白(余白)に関係
    val listContentPadding = PaddingValues(
        // 上: リスト(アニメタブ) の余白を外側 contentPadding に統一し、二重適用を防止
        top = 0.dp,
        // 左右: リスト(アニメタブ) の余白を外側 contentPadding に統一し、二重適用を防止
        start = 0.dp,
        end = 0.dp,
        // 下: リスト(アニメタブ) の IME 回避用の余白のみ追加
        bottom = listBottomPadding
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
                insertionPatterns = insertionState.patterns,
                insertionDefaultIntervalMs = insertionState.defaultIntervalMs,
                insertionDefaults = insertionState.defaults,
                isImeVisible = isImeVisible,
                modifier = Modifier.fillMaxWidth(),
                previewUiState = readyPreviewUiState
            )
        }
        // [dp] 縦: プレビュー の間隔(間隔)に関係
        // 提案: 上余白が残る場合は A: Spacer削除 / B: 0〜2dpに縮小 / C: SpriteTab.ANIM のみに限定（現状相当）
        // 安全: C（調整タブへ影響させず、アニメタブ内の間隔だけを最小変更で調整できるため）
        Spacer(modifier = Modifier.height(6.dp))
        LazyColumn(
            modifier = Modifier
                // [非dp] 縦: リスト の weight(制約)に関係
                .fillMaxWidth()
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
            item {
                AnimatedVisibility(visible = insertionState.enabled) {
                    @OptIn(ExperimentalFoundationApi::class)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    .fillMaxWidth(),
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
                                    .fillMaxWidth(),
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
                                    .fillMaxWidth(),
                                label = { Text("パターン1 周期（ms）") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = insertionState.pattern1IntervalError != null,
                                supportingText = insertionState.pattern1IntervalError?.let { errorText ->
                                    { Text(errorText, color = Color.Red) }
                                }
                            )
                        }
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
                                    .fillMaxWidth(),
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
                                    .fillMaxWidth(),
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
                                    .fillMaxWidth(),
                                label = { Text("パターン2 周期（ms）") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = insertionState.pattern2IntervalError != null,
                                supportingText = insertionState.pattern2IntervalError?.let { errorText ->
                                    { Text(errorText, color = Color.Red) }
                                }
                            )
                        }
                        OutlinedTextField(
                            value = insertionState.intervalInput,
                            onValueChange = insertionState.onIntervalInputChange,
                            modifier = Modifier
                                // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                                .fillMaxWidth(),
                            label = { Text("デフォルト周期（ms）") },
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
            value = selectedItem.displayLabel,
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
                    text = { Text(item.displayLabel) },
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
private fun rememberReadyAnimationState(
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
                    val insertionSteps = pattern.frameSequence.map { frame ->
                        PreviewStep(frameIndex = frame, intervalMs = resolvedIntervalMs, isInsertion = true)
                    }
                    lastInsertionPatternIndex = patternIndex
                    lastInsertionResolvedIntervalMs = resolvedIntervalMs
                    lastInsertionFrames = pattern.frameSequence.toList()
                    lastInsertionLoop = loopCount
                    if (insertionSettings?.exclusive == true) {
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

@Composable
private fun ReadyAnimationCharacter(
    imageBitmap: ImageBitmap?,
    frameRegion: SpriteFrameRegion?,
    spriteSizeDp: Dp,
    charYOffsetDp: Int,
    modifier: Modifier = Modifier,
) {
    val spriteColorFilter = rememberNightSpriteColorFilterForDarkTheme()
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
private fun ReadyAnimationInfo(
    state: ReadyAnimationState,
    summary: AnimationSummary,
    insertionSummary: AnimationSummary,
    insertionPreviewValues: InsertionPreviewValues,
    insertionEnabled: Boolean,
    insertionPatterns: List<InsertionPattern>,
    insertionDefaultIntervalMs: Int,
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
                if (lastIntervalMs != insertionDefaultIntervalMs) {
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
                if (effectiveIntervalText != defaultIntervalText) {
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
            text = "N:$everyNText    P:$probabilityText    CD:$cooldownText    Excl:$exclusiveText    W:$pattern1WeightText/$pattern2WeightText",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ReadyAnimationPreviewPane(
    imageBitmap: ImageBitmap?,
    spriteSheetConfig: SpriteSheetConfig,
    baseSummary: AnimationSummary,
    insertionSummary: AnimationSummary,
    insertionPreviewValues: InsertionPreviewValues,
    insertionEnabled: Boolean,
    insertionPatterns: List<InsertionPattern>,
    insertionDefaultIntervalMs: Int,
    insertionDefaults: InsertionAnimationSettings,
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
                            insertionPatterns = insertionPatterns,
                            insertionDefaultIntervalMs = insertionDefaultIntervalMs,
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
    imageBitmap: ImageBitmap?,
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
        // [dp] 縦: プレビュー の最小サイズ(最小サイズ)に関係
        val minHeight = 180.dp
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
            // [非dp] 縦: プレビュー の配置(配置)に関係
            contentAlignment = Alignment.TopCenter
        ) {
            val spriteColorFilter = rememberNightSpriteColorFilterForDarkTheme()
            if (imageBitmap == null) {
                // 画像読み込み中でも画面骨組みを維持するため、軽量プレースホルダーを表示する
                Box(
                    modifier = Modifier
                        // [非dp] 縦横: プレビュー の fillMaxSize(制約)に関係
                        .fillMaxSize()
                        .onSizeChanged { newSize -> onContainerSizeChanged?.invoke(newSize) }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Loading...", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Sprite Preview",
                    modifier = Modifier
                        // [非dp] 縦横: プレビュー の fillMaxSize(制約)に関係
                        .fillMaxSize()
                        .onSizeChanged { newSize -> onContainerSizeChanged?.invoke(newSize) },
                    contentScale = ContentScale.Fit,
                    colorFilter = spriteColorFilter
                )
                overlayContent()
            }
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
            // [非dp] 下: ナビゲーションバー の insets(インセット)に関係
            .windowInsetsPadding(WindowInsets.navigationBars)
            // [dp] 左右: 下部バー の余白(余白)に関係
            .padding(horizontal = 12.dp)
            // [dp] 上下: 下部バー の余白(余白)に関係
            .padding(vertical = 8.dp),
        // [dp] 縦: 下部バー の間隔(間隔)に関係
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val buttonModifier = Modifier
            // [非dp] 横: 画面全体 の weight(制約)に関係
            .fillMaxWidth()
            // [dp] 縦: 画面全体 の最小サイズ(最小サイズ)に関係
            .height(buttonHeight)

        val navigatorButtonColors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF6A00FF),
            contentColor = Color.White
        )
        val defaultControlButtonColors = ButtonDefaults.filledTonalButtonColors()
        val cellModifier = Modifier
            // [非dp] 横: 画面全体 の weight(制約)に関係
            .weight(1f)
            // [dp] 縦: ボタンのタップ領域(最小サイズ)に関係
            .heightIn(min = 48.dp)

        Column(
            // [dp] 縦: 画面全体 の間隔(間隔)に関係
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                // [非dp] 横: 画面全体 の fillMaxWidth(制約)に関係
                modifier = Modifier.fillMaxWidth(),
                // [dp] 横: 画面全体 の間隔(間隔)に関係
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = cellModifier,
                    contentAlignment = Alignment.Center
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
                }
                Box(
                    modifier = cellModifier,
                    contentAlignment = Alignment.Center
                ) {
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
                }
                Box(
                    modifier = cellModifier,
                    contentAlignment = Alignment.Center
                ) {
                    FilledTonalButton(
                        onClick = onMoveXNegative,
                        modifier = buttonModifier,
                        colors = defaultControlButtonColors,
                        contentPadding = buttonContentPadding,
                        shape = buttonShape
                    ) {
                        Text("X-")
                    }
                }
                Box(
                    modifier = cellModifier,
                    contentAlignment = Alignment.Center
                ) {
                    FilledTonalButton(
                        onClick = onMoveXPositive,
                        modifier = buttonModifier,
                        colors = defaultControlButtonColors,
                        contentPadding = buttonContentPadding,
                        shape = buttonShape
                    ) {
                        Text("X+")
                    }
                }
            }
            Row(
                // [非dp] 横: 画面全体 の fillMaxWidth(制約)に関係
                modifier = Modifier.fillMaxWidth(),
                // [dp] 横: 画面全体 の間隔(間隔)に関係
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = cellModifier,
                    contentAlignment = Alignment.Center
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
                }
                Box(
                    modifier = cellModifier,
                    contentAlignment = Alignment.Center
                ) {
                    FilledTonalButton(
                        onClick = onSizeIncrease,
                        modifier = buttonModifier,
                        colors = defaultControlButtonColors,
                        contentPadding = buttonContentPadding,
                        shape = buttonShape
                    ) {
                        Text("+")
                    }
                }
                Box(
                    modifier = cellModifier,
                    contentAlignment = Alignment.Center
                ) {
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
                Box(
                    modifier = cellModifier,
                    contentAlignment = Alignment.Center
                ) {
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
    // JSON互換のため、保存キーは従来の内部名を維持する
    root.put("animationType", animationType.internalKey)
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
        .put("patterns", patterns.toPatternsJsonArray())
        .put("intervalMs", intervalMs)
        .put("everyNLoops", everyNLoops)
        .put("probabilityPercent", probabilityPercent)
        .put("cooldownLoops", cooldownLoops)
        .put("exclusive", exclusive)

private fun List<InsertionPattern>.toPatternsJsonArray(): JSONArray =
    JSONArray().apply {
        forEach { pattern ->
            val patternObject = JSONObject()
                .put("frames", pattern.frameSequence.toJsonArray())
                .put(JSON_WEIGHT_KEY, pattern.weight)
            pattern.intervalMs?.let { intervalMs ->
                patternObject.put(JSON_PATTERN_INTERVAL_MS_KEY, intervalMs)
            }
            put(patternObject)
        }
    }

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
