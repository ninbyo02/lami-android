package io.github.ninbyo02.lami.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.R
import io.github.ninbyo02.lami.api.RetrofitClient
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import io.github.ninbyo02.lami.viewmodels.LamiState
import io.github.ninbyo02.lami.viewmodels.LamiStatus
import io.github.ninbyo02.lami.viewmodels.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.SystemClock
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LamiAvatar(
    baseUrl: String,
    selectedModel: String?,
    lastError: String?,
    lamiStatus: LamiStatus = LamiStatus.CONNECTING,
    lamiState: LamiState,
    availableModels: List<ModelInfo> = emptyList(),
    modifier: Modifier = Modifier,
    avatarShape: Shape = RoundedCornerShape(8.dp),
    backgroundColor: Color? = null,
    initialAvatarSize: Dp = 36.dp,
    minAvatarSize: Dp = 32.dp,
    maxAvatarSize: Dp = 64.dp,
    onSelectModel: (String) -> Unit = {},
    onNavigateSettings: (() -> Unit)? = null,
    selectedInferenceTarget: InferenceTarget = InferenceTarget.SERVER,
    onSelectInferenceTarget: (InferenceTarget) -> Unit = {},
    localInferenceEngineState: LocalInferenceEngineState = LocalInferenceEngineState.UNINITIALIZED,
    debugOverlayEnabled: Boolean = true,
    syncEpochMs: Long = 0L,
    openControlRequestKey: Int = 0,
) {
    val haptic = LocalHapticFeedback.current
    val selectModelAndKeepSheetOpen: (String) -> Unit = { modelName ->
        onSelectModel(modelName)
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
    }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val settingsPreferences = remember(context) { SettingsPreferences(context) }
    val animationsEnabled by settingsPreferences.characterAnimationEnabledFlow.collectAsState(initial = true)
    var replacementEnabled by rememberSaveable { mutableStateOf(true) }
    // 左上アバターもセンターと同じ Ready アニメになるよう既定は true
    var blinkEffectEnabled by rememberSaveable { mutableStateOf(true) }
    val clampedInitialSize = initialAvatarSize.value
        .roundToInt()
        .coerceIn(minAvatarSize.value.roundToInt(), maxAvatarSize.value.roundToInt())
    var avatarSize by rememberSaveable(
        inputs = arrayOf<Any>(minAvatarSize.value, maxAvatarSize.value, clampedInitialSize)
    ) {
        mutableStateOf(clampedInitialSize)
    }
    LaunchedEffect(clampedInitialSize) {
        if (avatarSize != clampedInitialSize) {
            avatarSize = clampedInitialSize
        }
    }
    var lastUpdated by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val initializationState = RetrofitClient.getLastInitializationState()
    val fallbackActive = initializationState?.usedFallback == true
    val fallbackMessage = initializationState?.errorMessage
    val debugEnabled = BuildConfig.DEBUG
    val outlineColor = MaterialTheme.colorScheme.outline
    val resolvedBackgroundColor = resolveLamiSpriteBackgroundColor(
        state = lamiState,
        backgroundColor = backgroundColor,
    )
    // センターのスプライトと同じ State<LamiStatus> 経路に合わせる
    val avatarStatusState = rememberUpdatedState(lamiStatus)
    val controlUiText = remember(
        selectedInferenceTarget,
        baseUrl,
        lamiStatus,
        availableModels,
    ) {
        resolveLamiControlUiText(
            selectedInferenceTarget = selectedInferenceTarget,
            baseUrl = baseUrl,
            lamiStatus = lamiStatus,
            availableModels = availableModels,
        )
    }
    val latencyMs by produceState<Long?>(
        initialValue = null,
        showSheet,
        baseUrl,
        lamiStatus,
        selectedInferenceTarget,
    ) {
        value = if (showSheet && selectedInferenceTarget == InferenceTarget.SERVER) {
            measureConnectionLatency(baseUrl = baseUrl, lamiStatus = lamiStatus)
        } else {
            null
        }
    }
    val latencyQualityLevel = remember(latencyMs) { latencyMsToQualityLevel(latencyMs) }
    val latencyText = remember(latencyMs) { formatLatencyText(latencyMs) }
    val isLocalBaseModelAvailable by produceState<Boolean?>(
        initialValue = null,
        showSheet,
    ) {
        value = if (showSheet) {
            settingsPreferences.getValidLocalBaseModelPathOrNull() != null
        } else {
            null
        }
    }
    LaunchedEffect(isLocalBaseModelAvailable) {
        if (isLocalBaseModelAvailable == false && selectedInferenceTarget == InferenceTarget.LOCAL) {
            onSelectInferenceTarget(InferenceTarget.SERVER)
        }
    }

    LaunchedEffect(baseUrl, selectedModel, lastError, fallbackActive, fallbackMessage) {
        lastUpdated = formatter.format(Date())
    }
    LaunchedEffect(openControlRequestKey) {
        if (openControlRequestKey > 0) {
            showSheet = true
        }
    }

    Box(
        modifier = modifier
            .size(avatarSize.dp)
            // 背景色の責務はアバター側に統一して外部依存を減らす
            .background(resolvedBackgroundColor, avatarShape)
            .clip(avatarShape)
            .combinedClickable(
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    showSheet = true
                }
            )
            .semantics {
                contentDescription = "Lami コントロールを開く"
            }
            .then(
                if (debugEnabled && debugOverlayEnabled) {
                    Modifier.border(1.dp, outlineColor)
                } else {
                    Modifier
                }
            )
    ) {
        val minSizeDpInt = minAvatarSize.value.roundToInt()
        val computedOffsetDp =
            if (avatarSize <= minSizeDpInt) {
                AVATAR_SPRITE_OFFSET_X_DP - AVATAR_MIN_OFFSET_ADJUST_DP
            } else {
                AVATAR_SPRITE_OFFSET_X_DP
            }
        // 表示サイズごとに非線形の補正を入れる。
        // 54dp 以上は、丸め誤差とスプライト余白の見え方が急に強くなるため左補正を段階的に増やす。
        // 調整ポイント: headerAvatarExtraOffsetBySizeDp() の dp -> offset テーブル値。
        val sizeBasedOffsetAdjustDp = headerAvatarExtraOffsetBySizeDp(avatarSize.dp)
        val adjustedOffsetDp = computedOffsetDp + sizeBasedOffsetAdjustDp
        LamiStatusSprite(
            status = avatarStatusState,
            lamiState = lamiState,
            sizeDp = avatarSize.dp,
            modifier = Modifier
                .offset(x = adjustedOffsetDp)
                .fillMaxWidth()
                .drawWithContent { drawContent() },
            contentOffsetDp = 0.dp,
            animationsEnabled = animationsEnabled,
            replacementEnabled = replacementEnabled,
            blinkEffectEnabled = blinkEffectEnabled,
            debugOverlayEnabled = debugOverlayEnabled,
            syncEpochMs = syncEpochMs,
        )
        if (debugEnabled && debugOverlayEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val offsetDx = adjustedOffsetDp.toPx()
                val shiftedCenterX = centerX + offsetDx
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = outlineColor,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = outlineColor,
                    start = Offset(shiftedCenterX, 0f),
                    end = Offset(shiftedCenterX, size.height),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = outlineColor,
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = strokeWidth,
                )
            }
        }
        if (showSheet) {
            ModalBottomSheet(
                sheetState = sheetState,
                onDismissRequest = { showSheet = false }
            ) {
                val sheetMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.94f
                val listState: LazyListState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                var searchQuery by rememberSaveable { mutableStateOf("") }
                val lamiSheetBg = MaterialTheme.colorScheme.surface
                val filteredModels by remember(availableModels, searchQuery) {
                    derivedStateOf {
                        availableModels.filter { model ->
                            searchQuery.isBlank() || model.name.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = sheetMaxHeight),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        state = listState,
                        // 上下の視認性を維持しつつ、初期表示でより多くの項目を見せるため最小限に詰める
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        // シート先頭・末尾の余白のみ半歩だけ縮め、一覧の操作範囲を広げる
                        contentPadding = PaddingValues(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 14.dp)
                    ) {
                        stickyHeader {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(lamiSheetBg)
                                    .padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Lami コントロール",
                                        modifier = Modifier.padding(start = 20.dp),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = when (selectedInferenceTarget) {
                                        InferenceTarget.LOCAL -> "ローカル推論"
                                        InferenceTarget.SERVER -> selectedModel ?: "未選択"
                                    },
                                    modifier = Modifier.padding(start = 20.dp),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 18.sp,
                                        letterSpacing = 0.sp,
                                    ),
                                    minLines = 2,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                        }
                        }
                    item {
                        ConnectionSummaryStatusRow(
                            label = "接続状態",
                            value = controlUiText.connectionLabel,
                            valueStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.sp,
                            ),
                            latencyText = latencyText,
                            qualityLevel = latencyQualityLevel,
                            showLatency = selectedInferenceTarget == InferenceTarget.SERVER && baseUrl.isNotBlank(),
                        )
                    }
                    item {
                        StatusInfoItem(
                            label = "接続先",
                            value = controlUiText.destinationLabel,
                            valueStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.sp,
                            ),
                        )
                    }
                    item {
                        StatusInfoItem(
                            label = "最終更新",
                            value = lastUpdated,
                            valueStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.sp,
                            ),
                        )
                    }
                    item {
                        StatusInfoItem(
                            label = "ローカル基本モデル",
                            value = if (isLocalBaseModelAvailable == true) "利用可能" else "未設定",
                            valueStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.sp,
                            ),
                        )
                    }
                    item {
                        StatusInfoItem(
                            label = "ローカル推論エンジン",
                            value = when (localInferenceEngineState) {
                                LocalInferenceEngineState.UNINITIALIZED -> "未初期化"
                                LocalInferenceEngineState.PREPARING -> "準備中"
                                LocalInferenceEngineState.READY -> "利用可能"
                                LocalInferenceEngineState.ERROR -> "エラー"
                            },
                            valueStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.sp,
                            ),
                        )
                    }
                    item {
                        InferenceTargetSelectorRow(
                            selectedTarget = selectedInferenceTarget,
                            isLocalTargetEnabled = isLocalBaseModelAvailable == true,
                            onSelectTarget = { target ->
                                onSelectInferenceTarget(target)
                            },
                        )
                    }
                    item {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = controlUiText.modelListTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            if (controlUiText.showModelSearch) {
                                LamiControlSearchPill(
                                    value = searchQuery,
                                    lamiSheetBg = lamiSheetBg,
                                    onValueChange = { query -> searchQuery = query },
                                    onClear = { searchQuery = "" },
                                    onSearch = { scope.launch { listState.animateScrollToItem(0) } },
                                )
                            }
                        }
                    }
                    val modelListMessage = controlUiText.modelListMessage
                    if (modelListMessage != null) {
                        item { Text(modelListMessage) }
                    } else if (filteredModels.isEmpty()) {
                        item { Text("モデルを取得できませんでした") }
                    } else {
                        items(filteredModels, key = { model -> model.name }) { model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // 行の圧迫感を避けながら縦密度を半歩だけ上げる
                                    .padding(vertical = 1.dp)
                                    .selectable(
                                        selected = selectedModel == model.name,
                                        onClick = {
                                            selectModelAndKeepSheetOpen(model.name)
                                        },
                                        role = Role.RadioButton
                                    )
                                    .semantics {
                                        contentDescription = "モデル ${model.name} を選択"
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedModel == model.name,
                                        onClick = null,
                                        modifier = Modifier.semantics { contentDescription = "モデル ${model.name}" }
                                    )
                                    Text(
                                        text = model.name,
                                        modifier = Modifier
                                            .weight(1f)
                                            // ラジオボタンとの可読性を保ちつつ、モデル名の実効横幅を優先する
                                            .padding(start = 4.dp, end = 0.dp),
                                        maxLines = Int.MAX_VALUE,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Normal,
                                            lineHeight = 18.sp,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    if (controlUiText.showSettingsButton) {
                        item {
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    onNavigateSettings?.invoke()
                                    showSheet = false
                                }
                            ) {
                                Text("設定画面へ移動")
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

enum class InferenceTarget {
    SERVER,
    LOCAL,
}

enum class LocalInferenceEngineState {
    UNINITIALIZED,
    PREPARING,
    READY,
    ERROR,
}

internal data class LamiControlUiText(
    val connectionLabel: String,
    val destinationLabel: String,
    val modelListTitle: String,
    val modelListMessage: String?,
    val showModelSearch: Boolean,
    val showSettingsButton: Boolean,
)

internal fun resolveLamiControlUiText(
    selectedInferenceTarget: InferenceTarget,
    baseUrl: String,
    lamiStatus: LamiStatus,
    availableModels: List<ModelInfo>,
): LamiControlUiText {
    val normalizedBaseUrl = baseUrl.trim()
    if (selectedInferenceTarget == InferenceTarget.LOCAL) {
        return LamiControlUiText(
            connectionLabel = "ローカルモード",
            destinationLabel = "ローカル推論",
            modelListTitle = "利用可能なモデル",
            modelListMessage = "ローカルモードではサーバーモデルを使用しません",
            showModelSearch = false,
            showSettingsButton = false,
        )
    }
    if (normalizedBaseUrl.isBlank()) {
        return LamiControlUiText(
            connectionLabel = "サーバー未設定",
            destinationLabel = "なし",
            modelListTitle = "利用可能なモデル",
            modelListMessage = "サーバーが登録されていません",
            showModelSearch = false,
            showSettingsButton = true,
        )
    }

    val connectionLabel = when (lamiStatus) {
        LamiStatus.CONNECTING -> "接続中"
        LamiStatus.READY,
        LamiStatus.TALKING,
        LamiStatus.NO_MODELS -> "接続OK"
        LamiStatus.DEGRADED,
        LamiStatus.OFFLINE,
        LamiStatus.ERROR -> "接続失敗"
    }
    val connectionFailed = lamiStatus == LamiStatus.DEGRADED ||
        lamiStatus == LamiStatus.OFFLINE ||
        lamiStatus == LamiStatus.ERROR
    return LamiControlUiText(
        connectionLabel = connectionLabel,
        destinationLabel = normalizedBaseUrl,
        modelListTitle = "利用可能なモデル",
        modelListMessage = when {
            connectionFailed -> "モデルを取得できませんでした"
            availableModels.isEmpty() -> "モデルを取得できませんでした"
            else -> null
        },
        showModelSearch = !connectionFailed && availableModels.isNotEmpty(),
        showSettingsButton = connectionFailed,
    )
}


private val ConnectionSummaryRowStartPadding = 20.dp
private val ConnectionSummaryLabelMinWidth = 72.dp
private val ConnectionSummaryLabelValueSpacing = 12.dp

@Composable
private fun StatusInfoItem(
    label: String,
    value: String,
    valueStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ConnectionSummaryRowStartPadding),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier
                .widthIn(min = ConnectionSummaryLabelMinWidth)
                .alignByBaseline(),
        )
        Spacer(modifier = Modifier.width(ConnectionSummaryLabelValueSpacing))
        Text(
            text = value,
            modifier = Modifier
                .weight(1f)
                .alignByBaseline(),
            style = valueStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConnectionSummaryStatusRow(
    label: String,
    value: String,
    valueStyle: TextStyle,
    latencyText: String,
    qualityLevel: Int,
    showLatency: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ConnectionSummaryRowStartPadding),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier
                .widthIn(min = ConnectionSummaryLabelMinWidth)
                .alignByBaseline(),
        )
        Spacer(modifier = Modifier.width(ConnectionSummaryLabelValueSpacing))
        Text(
            text = value,
            style = valueStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alignByBaseline(),
        )
        if (showLatency) {
            // 接続状態テキストと品質バーを少し離して視認性を整える
            Spacer(modifier = Modifier.width(12.dp))
            LatencyQualityIndicator(
                qualityLevel = qualityLevel,
                modifier = Modifier.align(Alignment.Bottom).offset(y = (-5).dp),
            )
            // 品質バーと遅延表示は意味のまとまりを優先して最小限だけ空ける
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = latencyText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp,
                ),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@Composable
private fun InferenceTargetSelectorRow(
    selectedTarget: InferenceTarget,
    isLocalTargetEnabled: Boolean,
    onSelectTarget: (InferenceTarget) -> Unit,
) {
    val options = remember {
        listOf(
            InferenceTarget.SERVER to "サーバー",
            InferenceTarget.LOCAL to "ローカル",
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ConnectionSummaryRowStartPadding)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "推論先",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
        )
        options.forEach { (target, label) ->
            val enabled = target != InferenceTarget.LOCAL || isLocalTargetEnabled
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selectedTarget == target,
                        enabled = enabled,
                        onClick = { onSelectTarget(target) },
                        role = Role.RadioButton,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selectedTarget == target,
                    onClick = null,
                    enabled = enabled,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun LatencyQualityIndicator(
    qualityLevel: Int,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.onSurface
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val barHeights = remember { listOf(8.dp, 11.dp, 14.dp, 17.dp) }
    Row(
        modifier = modifier.semantics {
            stateDescription = "接続品質 ${qualityLevel.coerceIn(0, 4)}/4"
        },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        barHeights.forEachIndexed { index, height ->
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = height)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (index < qualityLevel) activeColor else inactiveColor)
            )
        }
    }
}

internal fun latencyMsToQualityLevel(latencyMs: Long?): Int = when {
    latencyMs == null -> 0
    latencyMs <= 120L -> 4
    latencyMs <= 250L -> 3
    latencyMs <= 500L -> 2
    else -> 1
}

internal fun formatLatencyText(latencyMs: Long?): String = latencyMs?.let { "${it}ms" } ?: "--ms"

private suspend fun measureConnectionLatency(
    baseUrl: String,
    lamiStatus: LamiStatus,
): Long? {
    if (baseUrl.isBlank()) return null
    if (lamiStatus == LamiStatus.OFFLINE || lamiStatus == LamiStatus.ERROR) return null
    return withContext(Dispatchers.IO) {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val requestUrl = "$normalizedBaseUrl/api/tags"
        var connection: HttpURLConnection? = null
        runCatching {
            connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1500
                readTimeout = 1500
                setRequestProperty("Accept", "application/json")
                useCaches = false
            }
            val startMs = SystemClock.elapsedRealtime()
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@runCatching null
            }
            val stream = connection.inputStream
            stream.close()
            (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(0L)
        }.getOrNull().also {
            connection?.disconnect()
        }
    }
}

@Composable
private fun LamiControlSearchPill(
    value: String,
    lamiSheetBg: Color,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = 40.dp
    val shape = RoundedCornerShape(height / 2)
    val textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    )
    val placeholderStyle: TextStyle = textStyle.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(lamiSheetBg, shape)
            // 左：検索アイコンとの間を取り、ピル内の詰まりを防ぐ。
            .padding(start = 16.dp, top = 1.dp, end = 0.dp, bottom = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "モデル検索",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 左アイコンと入力テキストの間に最小限の余白を確保。
            Spacer(modifier = Modifier.size(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = textStyle,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = "モデルを検索",
                                style = placeholderStyle,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "検索文字列をクリア",
                    )
                }
            }
        }
    }
}

// 視認が難しい場合は一時的にオフセットを変更して確認する
private val AVATAR_SPRITE_OFFSET_X_DP = 1.dp
private val AVATAR_MIN_OFFSET_ADJUST_DP = 1.dp

private fun headerAvatarExtraOffsetBySizeDp(sizeDp: Dp): Dp {
    val offsetTable = listOf(
        48 to 0.00f,
        50 to -1.00f,
        52 to -1.00f,
        54 to -0.68f,
        56 to -0.87f,
        58 to -0.85f,
        60 to -1.03f,
        62 to -0.92f,
        64 to -1.10f,
    )

    val roundedSizeDp = sizeDp.value.roundToInt()
    offsetTable.firstOrNull { it.first == roundedSizeDp }?.let { return it.second.dp }

    val lower = offsetTable.lastOrNull { it.first < roundedSizeDp }
    val upper = offsetTable.firstOrNull { it.first > roundedSizeDp }

    if (lower == null && upper == null) return 0.dp
    if (lower == null) return upper!!.second.dp
    if (upper == null) return lower.second.dp

    val t = (roundedSizeDp - lower.first).toFloat() / (upper.first - lower.first).toFloat()
    val interpolated = lower.second + (upper.second - lower.second) * t
    return interpolated.dp
}
