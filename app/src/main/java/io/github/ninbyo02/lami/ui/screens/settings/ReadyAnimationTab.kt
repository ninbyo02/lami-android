@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.ninbyo02.lami.ui.screens.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.ninbyo02.lami.data.SpriteSheetConfig
import io.github.ninbyo02.lami.tts.AndroidTtsController
import io.github.ninbyo02.lami.ui.components.DevMenuSectionHost
import io.github.ninbyo02.lami.ui.components.DevMenuTtsCallbacks
import io.github.ninbyo02.lami.ui.components.DevMenuTtsUiState
import io.github.ninbyo02.lami.ui.components.rememberReadyPreviewLayoutState
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.roundToInt

private data class TtsPreset(val rate: Float, val pitch: Float)

private val TtsPresetDefault = TtsPreset(
    rate = AndroidTtsController.DEFAULT_SPEECH_RATE,
    pitch = AndroidTtsController.DEFAULT_PITCH,
)
private val TtsPresetCalm = TtsPreset(rate = 0.88f, pitch = 1.10f)
private val TtsPresetBright = TtsPreset(rate = 0.98f, pitch = 1.24f)

@Composable
internal fun ReadyAnimationTab(
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
    resolvedErrorKey: String?,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscapeOrWide =
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            configuration.screenWidthDp >= configuration.screenHeightDp
    val selectedAnimation = selectionState.selectedAnimation
    var replacementEnabled by rememberSaveable { mutableStateOf(true) }
    var blinkEffectEnabled by rememberSaveable { mutableStateOf(true) }
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
    val scope = rememberCoroutineScope()
    val settingsPreferences = remember(context.applicationContext) {
        SettingsPreferences(context.applicationContext)
    }
    val storedDevMenuTtsSpeechRate by settingsPreferences.ttsSpeechRateFlow.collectAsState(
        initial = AndroidTtsController.DEFAULT_SPEECH_RATE
    )
    val storedDevMenuTtsPitch by settingsPreferences.ttsPitchFlow.collectAsState(
        initial = AndroidTtsController.DEFAULT_PITCH
    )
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeBottomDp = with(density) { imeBottomPx.toDp() }
    val navigationBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    var focusedField by remember { mutableStateOf<String?>(null) }
    var focusedRequester by remember { mutableStateOf<BringIntoViewRequester?>(null) }
    val focusedFieldStates = remember { mutableStateMapOf<String, Boolean>() }
    val baseFramesBringIntoViewRequester = remember { BringIntoViewRequester() }
    val baseIntervalBringIntoViewRequester = remember { BringIntoViewRequester() }
    fun handleFieldFocus(fieldKey: String, isFocused: Boolean, requester: BringIntoViewRequester) {
        focusedFieldStates[fieldKey] = isFocused
        if (isFocused) {
            focusedField = fieldKey
            focusedRequester = requester
            return
        }
        scope.launch {
            yield()
            if (focusedFieldStates.values.none { it }) {
                focusedField = null
                focusedRequester = null
            }
        }
    }

    LaunchedEffect(focusedField, imeBottomPx) {
        if (focusedField != null && imeBottomPx > 0) {
            withFrameNanos { }
            withFrameNanos { }
            focusedRequester?.bringIntoView()
        }
    }

    val heightScale = (configuration.screenHeightDp / 800f).coerceIn(0.85f, 1.15f)
    fun scaledInt(value: Int): Int = (value * heightScale).roundToInt()
    val readyPreviewUiState = ReadyPreviewUiState(
        charYOffsetDp = scaledInt(layoutState.charYOffsetDp),
        charXOffsetDp = scaledInt(layoutState.charXOffsetDp),
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
    // [dp] 下: IME 高さから NavigationBars 分を差し引いた純増分のみを利用して、二重適用を防止
    val imeBottomExcludingNavDp = (imeBottomDp - navigationBottomDp).coerceAtLeast(0.dp)
    // [dp] 下: IME 表示中は追加余白を最小化し、入力欄がキーボード直上へ自然に寄るようにする
    val listBottomPadding = if (isImeVisible) 0.dp else (navigationBottomDp + 16.dp)
    // [dp] 四方向: リスト(アニメタブ) の余白(余白)に関係
    val listContentPadding = PaddingValues(
        // 上: リスト(アニメタブ) の余白を外側 contentPadding に統一し、二重適用を防止
        top = 0.dp,
        // 左右: リスト(アニメタブ) の余白を外側 contentPadding に統一し、二重適用を防止
        start = 0.dp,
        end = 0.dp,
        // 下: リスト(アニメタブ) の NavigationBars + 最小余白のみ追加（IME 高さの加算は二重回避）
        bottom = listBottomPadding
    )
    var devMenuTtsSpeechRate by rememberSaveable {
        mutableFloatStateOf(AndroidTtsController.DEFAULT_SPEECH_RATE)
    }
    var devMenuTtsPitch by rememberSaveable {
        mutableFloatStateOf(AndroidTtsController.DEFAULT_PITCH)
    }
    LaunchedEffect(storedDevMenuTtsSpeechRate) {
        devMenuTtsSpeechRate = storedDevMenuTtsSpeechRate
    }
    LaunchedEffect(storedDevMenuTtsPitch) {
        devMenuTtsPitch = storedDevMenuTtsPitch
    }
    var isDevMenuTtsPlaying by remember { mutableStateOf(false) }

    val previewContent: @Composable (Modifier) -> Unit = { modifier ->
        Surface(
            // [非dp] 横: プレビュー の fillMaxWidth(制約)に関係
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background
        ) {
            ReadyAnimationPreviewPane(
                model = ReadyAnimationPreviewModel(
                    imageBitmap = imageBitmap,
                    spriteSheetConfig = spriteSheetConfig,
                    baseSummary = baseState.summary,
                    insertionSummary = insertionState.summary,
                    insertionPreviewValues = insertionState.previewValues,
                    insertionEnabled = insertionState.enabled,
                    insertionPatterns = insertionState.patterns,
                    insertionDefaultIntervalMs = insertionState.defaultIntervalMs,
                    shouldShowDefaultInterval = insertionState.shouldShowDefaultInterval,
                    insertionDefaults = insertionState.defaults,
                ),
                previewUiState = readyPreviewUiState,
                isImeVisible = isImeVisible,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    val formContent: @Composable (Modifier) -> Unit = { modifier ->
        val ttsController = remember(context.applicationContext) {
            AndroidTtsController(context.applicationContext)
        }
        LaunchedEffect(devMenuTtsSpeechRate, devMenuTtsPitch) {
            ttsController.setSpeechConfig(
                rate = devMenuTtsSpeechRate,
                pitch = devMenuTtsPitch,
            )
        }
        fun applyTtsPreset(preset: TtsPreset) {
            val updatedRate = preset.rate
                .coerceIn(AndroidTtsController.MIN_SPEECH_RATE, AndroidTtsController.MAX_SPEECH_RATE)
            val updatedPitch = preset.pitch
                .coerceIn(AndroidTtsController.MIN_PITCH, AndroidTtsController.MAX_PITCH)
            devMenuTtsSpeechRate = updatedRate
            devMenuTtsPitch = updatedPitch
            ttsController.setSpeechConfig(
                rate = updatedRate,
                pitch = updatedPitch,
            )
            scope.launch {
                settingsPreferences.setTtsSpeechRate(updatedRate)
                settingsPreferences.setTtsPitch(updatedPitch)
            }
        }
        fun resetTtsToDefaults() {
            val defaultRate = AndroidTtsController.DEFAULT_SPEECH_RATE
            val defaultPitch = AndroidTtsController.DEFAULT_PITCH
            devMenuTtsSpeechRate = defaultRate
            devMenuTtsPitch = defaultPitch
            ttsController.setSpeechConfig(
                rate = defaultRate,
                pitch = defaultPitch,
            )
            scope.launch {
                settingsPreferences.setTtsSpeechRate(defaultRate)
                settingsPreferences.setTtsPitch(defaultPitch)
            }
        }
        DisposableEffect(ttsController) {
            ttsController.setOnPlaybackStateChanged { isPlaying ->
                isDevMenuTtsPlaying = isPlaying
            }
            onDispose {
                ttsController.setOnPlaybackStateChanged { }
                ttsController.stop()
                isDevMenuTtsPlaying = false
            }
        }
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                // [dp] 下: IME 表示中のみ純IME分を追加し、入力欄がキーボード直上へ追従するようにする
                .padding(bottom = if (isImeVisible) imeBottomExcludingNavDp else 0.dp)
                .testTag("spriteAnimList"),
            state = lazyListState,
            // [dp] 縦: リスト の間隔(間隔)に関係
            verticalArrangement = Arrangement.spacedBy(10.dp),
            // [dp] 四方向: リスト の余白(余白)に関係
            contentPadding = listContentPadding
        ) {
            item {
                ReadyBaseAnimationSettingsSection(
                    selectedAnimation = selectedAnimation,
                    selectionState = selectionState,
                    baseState = baseState,
                    insertionState = insertionState,
                    baseFramesBringIntoViewRequester = baseFramesBringIntoViewRequester,
                    baseIntervalBringIntoViewRequester = baseIntervalBringIntoViewRequester,
                    onFieldFocus = { fieldKey, isFocused, requester ->
                        handleFieldFocus(fieldKey, isFocused, requester)
                    },
                )
            }
            item {
                ReadyInsertionAnimationSettings(
                    insertionState = insertionState,
                    onFieldFocus = ::handleFieldFocus,
                )
            }
            item {
                DevMenuSectionHost(
                    devUnlocked = devUnlocked,
                    layoutState = layoutState,
                    previewUiState = readyPreviewUiState,
                    ttsUiState = DevMenuTtsUiState(
                        isPlaying = isDevMenuTtsPlaying,
                        speechRate = devMenuTtsSpeechRate,
                        pitch = devMenuTtsPitch,
                    ),
                    ttsCallbacks = DevMenuTtsCallbacks(
                        onSpeakReferencePhrase = { ttsController.speakReferencePhrase() },
                        onSpeakReferencePhrase2 = { ttsController.speakReferencePhrase2() },
                        onSpeakReferencePhrase3 = { ttsController.speakReferencePhrase3() },
                        onSpeakReferencePhrase4 = { ttsController.speakReferencePhrase4() },
                        onStopTts = { ttsController.stop() },
                        onResetTtsDefaults = { resetTtsToDefaults() },
                        onApplyTtsPresetDefault = {
                            applyTtsPreset(TtsPresetDefault)
                        },
                        onApplyTtsPresetCalm = {
                            applyTtsPreset(TtsPresetCalm)
                        },
                        onApplyTtsPresetBright = {
                            applyTtsPreset(TtsPresetBright)
                        },
                        onIncreaseTtsSpeechRate = {
                            val updatedRate = (devMenuTtsSpeechRate + 0.02f)
                                .coerceAtMost(AndroidTtsController.MAX_SPEECH_RATE)
                            devMenuTtsSpeechRate = updatedRate
                            scope.launch {
                                settingsPreferences.setTtsSpeechRate(updatedRate)
                            }
                        },
                        onDecreaseTtsSpeechRate = {
                            val updatedRate = (devMenuTtsSpeechRate - 0.02f)
                                .coerceAtLeast(AndroidTtsController.MIN_SPEECH_RATE)
                            devMenuTtsSpeechRate = updatedRate
                            scope.launch {
                                settingsPreferences.setTtsSpeechRate(updatedRate)
                            }
                        },
                        onIncreaseTtsPitch = {
                            val updatedPitch = (devMenuTtsPitch + 0.02f)
                                .coerceAtMost(AndroidTtsController.MAX_PITCH)
                            devMenuTtsPitch = updatedPitch
                            scope.launch {
                                settingsPreferences.setTtsPitch(updatedPitch)
                            }
                        },
                        onDecreaseTtsPitch = {
                            val updatedPitch = (devMenuTtsPitch - 0.02f)
                                .coerceAtLeast(AndroidTtsController.MIN_PITCH)
                            devMenuTtsPitch = updatedPitch
                            scope.launch {
                                settingsPreferences.setTtsPitch(updatedPitch)
                            }
                        },
                    ),
                    replacementEnabled = replacementEnabled,
                    onReplacementEnabledChange = { enabled -> replacementEnabled = enabled },
                    blinkEffectEnabled = blinkEffectEnabled,
                    onBlinkEffectEnabledChange = { enabled -> blinkEffectEnabled = enabled },
                )
            }
        }
    }

    ReadyAnimationResponsiveLayout(
        isLandscapeOrWide = isLandscapeOrWide,
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
        previewContent = previewContent,
        formContent = formContent,
    )
}
