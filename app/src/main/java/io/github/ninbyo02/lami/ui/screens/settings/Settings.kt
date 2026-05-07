package io.github.ninbyo02.lami.ui.screens.settings
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.annotation.VisibleForTesting
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.navigation.Routes
import io.github.ninbyo02.lami.navigation.SettingsRoute
import io.github.ninbyo02.lami.R
import io.github.ninbyo02.lami.api.BaseUrlInitializationState
import io.github.ninbyo02.lami.api.RetrofitClient
import io.github.ninbyo02.lami.db.AppDatabase
import io.github.ninbyo02.lami.db.entity.BaseUrl
import io.github.ninbyo02.lami.db.repository.BaseUrlRepository
import io.github.ninbyo02.lami.db.repository.ModelPreferenceRepository
import io.github.ninbyo02.lami.ui.common.LocalAppSnackbarHostState
import io.github.ninbyo02.lami.ui.common.PROJECT_SNACKBAR_SHORT_MS
import io.github.ninbyo02.lami.ui.theme.LamiTypographyTokens
import io.github.ninbyo02.lami.ui.common.BottomFadeOverlay
import io.github.ninbyo02.lami.ui.common.TopFadeOverlay
import io.github.ninbyo02.lami.util.PORT_ERROR_MESSAGE
import io.github.ninbyo02.lami.util.normalizeUrlInput
import io.github.ninbyo02.lami.util.validateUrlFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.UUID

internal data class ConnectionValidationResult(
    val normalizedUrl: String,
    val isSuccess: Boolean,
    val isReachable: Boolean,
    val warningMessage: String? = null,
    val errorMessage: String? = null
)

private fun normalizeUrlForSave(url: String): String {
    return url.trim().trimEnd('/')
}

internal data class ServerInput(
    val localId: String = UUID.randomUUID().toString(),
    val id: Int? = null,
    val url: String,
    val isActive: Boolean = false
)

// サーバー接続検証中インジケータの視覚的中心補正
private val ServerValidationIndicatorYOffset = 3.dp

private const val ResetSettingsScrollOnReturnFromAboutKey = "reset_settings_scroll_on_return_from_about"

// サーバー行右端の削除ボタン領域（48dpタップ領域を確保）
private val ServerRowTrailingSlotWidth = 32.dp

fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(navgationController: NavController, onSaved: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    val baseUrlRepository = remember { BaseUrlRepository(db.baseUrlDao()) }
    val modelPreferenceRepository = remember { ModelPreferenceRepository(db.modelPreferenceDao()) }
    val snackbarHostState = LocalAppSnackbarHostState.current
    val serverInputs = remember { mutableStateListOf<ServerInput>() }
    var connectionStatuses by remember { mutableStateOf<Map<String, ConnectionValidationResult>>(emptyMap()) }
    var duplicateUrls by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var isValidatingConnections by remember { mutableStateOf(false) }
    val settingsPreferences = remember { SettingsPreferences(context) }
    val settingsData by settingsPreferences.settingsData.collectAsState(initial = SettingsData())
    val savedLamiAvatarSizeDp by settingsPreferences.chatLamiAvatarSizeDpFlow
        .collectAsState(initial = DEFAULT_CHAT_LAMI_AVATAR_SIZE_DP)
    val localBaseModelDisplayName by settingsPreferences.localBaseModelDisplayNameFlow
        .collectAsState(initial = null)
    val ttsEnabled by settingsPreferences.ttsEnabledFlow.collectAsState(initial = true)
    val devEnableStreamingSentenceTts by settingsPreferences.devEnableStreamingSentenceTtsFlow
        .collectAsState(initial = false)
    val effectiveSentenceStreamingTtsChecked = ttsEnabled && devEnableStreamingSentenceTts
    val maxServers = 5
    val serverInputIds = serverInputs.map { it.localId }

    LaunchedEffect(Unit) {
        // 戻る履歴/再起動時の復元のため、表示開始時に最後の画面を保存する
        settingsPreferences.saveLastRoute(Routes.SETTINGS)
    }

    fun onBackRequested() {
        val popped = navgationController.popBackStack()
        if (!popped) {
            navgationController.navigate(Routes.CHATS) {
                launchSingleTop = true
                popUpTo(Routes.CHATS) { inclusive = true }
            }
        }
    }

    fun showSuccessSnackbarShort(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val dismissJob = launch {
                delay(PROJECT_SNACKBAR_SHORT_MS)
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            dismissJob.cancel()
        }
    }

    fun getNormalizedInputs(): List<ServerInput> {
        return serverInputs.map { input ->
            input.copy(url = normalizeUrlInput(input.url))
        }
    }

    fun detectDuplicateUrls(normalizedInputs: List<ServerInput>): Map<String, Boolean> {
        return normalizedInputs
            .groupBy { it.url }
            .filter { it.value.size > 1 }
            .flatMap { (_, inputs) ->
                inputs.map { it.localId to true }
            }
            .toMap()
    }

    LaunchedEffect(Unit) {
        val storedUrls = withContext(Dispatchers.IO) { baseUrlRepository.getAll() }
        val hasActive = storedUrls.any { it.isActive }
        val initialList = if (storedUrls.isNotEmpty()) {
            storedUrls.mapIndexed { index, baseUrl ->
                ServerInput(
                    id = baseUrl.id,
                    url = baseUrl.url,
                    isActive = if (hasActive) baseUrl.isActive else index == 0
                )
            }
        } else {
            listOf(
                ServerInput(
                    url = "http://localhost:13511/",
                    isActive = true
                )
            )
        }
        serverInputs.clear()
        serverInputs.addAll(initialList)
        val normalizedInputs = getNormalizedInputs()
        duplicateUrls = detectDuplicateUrls(normalizedInputs)
    }

    LaunchedEffect(serverInputIds) {
        connectionStatuses = connectionStatuses.filterKeys { key -> key in serverInputIds }
        val normalizedInputs = getNormalizedInputs()
        duplicateUrls = detectDuplicateUrls(normalizedInputs)
    }

    val horizontalPadding = 16.dp
    val verticalPadding = 12.dp
    val density = LocalDensity.current
    val imeBottomDp = WindowInsets.ime.asPaddingValues(density).calculateBottomPadding()
    val navBottomDp = WindowInsets.navigationBars.asPaddingValues(density).calculateBottomPadding()
    val bottomDp = (imeBottomDp - navBottomDp).coerceAtLeast(0.dp)
    val listState = rememberLazyListState()
    // Use the explicit SETTINGS NavBackStackEntry as the owner of the
    // SavedStateHandle used for About→Settings return events.
    // This avoids relying on currentBackStackEntry and makes the state
    // owner explicit and stable across recompositions.
    val settingsBackStackEntry = remember(navgationController) {
        navgationController.getBackStackEntry(Routes.SETTINGS)
    }
    val resetScrollOnReturnFromAbout by
        settingsBackStackEntry
            .savedStateHandle
            .getStateFlow(ResetSettingsScrollOnReturnFromAboutKey, false)
            .collectAsState()
    val fadeHeight = 32.dp
    val showTopFade by remember { derivedStateOf { listState.canScrollBackward } }
    val showBottomFade by remember { derivedStateOf { listState.canScrollForward } }
    val scaffoldBg = MaterialTheme.colorScheme.background
    var previewLamiAvatarSizeDp by remember { mutableStateOf(savedLamiAvatarSizeDp.toFloat()) }

    LaunchedEffect(savedLamiAvatarSizeDp) {
        previewLamiAvatarSizeDp = savedLamiAvatarSizeDp.toFloat()
    }

    LaunchedEffect(resetScrollOnReturnFromAbout) {
        if (resetScrollOnReturnFromAbout) {
            listState.scrollToItem(0)
            settingsBackStackEntry
                .savedStateHandle
                .set(ResetSettingsScrollOnReturnFromAboutKey, false)
        }
    }

    Scaffold(
        modifier = Modifier.testTag("settingsScreenRoot"),
        containerColor = scaffoldBg,
        // Settings 系では Scaffold 自体は Insets を受けず、topBar/content の座標だけを返す
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                // 上端余白の責務は Scaffold/content に寄せ、TopAppBar 自身は 0 inset に固定する
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .fillMaxHeight()
                            .wrapContentHeight(Alignment.CenterVertically),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        IconButton(onClick = { onBackRequested() }) {
                            Icon(
                                painterResource(R.drawable.back),
                                "exit",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .wrapContentHeight(Alignment.CenterVertically)
                    ) {
                        Text("Settings")
                    }
                },
                modifier = Modifier
                    // [dp] 縦: TopAppBar 本体の描画領域は従来どおり 48.dp に保つ
                    .fillMaxWidth()
                    .height(48.dp)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Scaffold の描画領域（TopAppBar 下）に座標系を統一する
                .padding(paddingValues)
                // Scaffold の Insets はこの階層で消費し、子で二重適用しない
                .consumeWindowInsets(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // 下: IME とナビゲーションバーの差分だけを適用し、キーボードとの隙間をなくす
                    .padding(bottom = bottomDp),
                // 上: 視認性維持のため最小限の top padding、下: 表示領域最大化のため 0dp
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 0.dp,
                    bottom = 0.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                CardSectionHeader(
                    title = "デバッグツール",
                    description = "スプライト関連の挙動を確認・調整するためのツールです",
                    modifier = Modifier.padding(bottom = 2.dp),
                    topPadding = 20.dp
                )
                Card {
                    SettingsNavRowItem(
                        headline = "Sprite Settings",
                        supporting = "スプライト画像を表示します",
                        leadingIcon = Icons.Filled.BugReport,
                        onClick = { navgationController.navigate(SettingsRoute.SpriteSettings.route) }
                    )
                }
                // 同一セクション内の Sprite カード同士だけ 2dp の間隔を確保
                Spacer(modifier = Modifier.height(2.dp))
                Card {
                    SettingsNavRowItem(
                        headline = "Sprite Editor",
                        supporting = "スプライト画像の編集・書き出しを行います",
                        leadingIcon = Icons.Filled.BugReport,
                        onClick = { navgationController.navigate(SettingsRoute.SpriteEditor.route) }
                    )
                }
            }
            item {
                CardSectionHeader(
                    title = "表示設定",
                    description = "テーマカラーなどの外観設定を変更できます",
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // カード高さを最小化しつつ可読性を維持する
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "キャラクター表示サイズ",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${previewLamiAvatarSizeDp.toInt()}dp",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            val activeTrackColor = MaterialTheme.colorScheme.primary
                            val inactiveTrackColor =
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
                            val defaultMarkerColor = inactiveTrackColor

                            Slider(
                                value = previewLamiAvatarSizeDp,
                                onValueChange = { value ->
                                    val snappedValue = value.toInt()
                                        .coerceIn(
                                            MIN_CHAT_LAMI_AVATAR_SIZE_DP,
                                            MAX_CHAT_LAMI_AVATAR_SIZE_DP
                                        )
                                    previewLamiAvatarSizeDp = snappedValue.toFloat()
                                },
                                onValueChangeFinished = {
                                    scope.launch {
                                        settingsPreferences.setChatLamiAvatarSizeDp(
                                            previewLamiAvatarSizeDp.toInt()
                                        )
                                    }
                                },
                                valueRange =
                                    MIN_CHAT_LAMI_AVATAR_SIZE_DP.toFloat()..
                                        MAX_CHAT_LAMI_AVATAR_SIZE_DP.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = activeTrackColor,
                                    activeTrackColor = activeTrackColor,
                                    inactiveTrackColor = inactiveTrackColor,
                                ),
                                track = { sliderState ->
                                    SliderDefaults.Track(
                                        sliderState = sliderState,
                                        modifier = Modifier.scale(1f, 0.5f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = activeTrackColor,
                                            activeTrackColor = activeTrackColor,
                                            inactiveTrackColor = inactiveTrackColor,
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // スライダー自体の上下余白も0dpに固定する
                                    .padding(vertical = 0.dp)
                                    .drawBehind {
                                        val min = MIN_CHAT_LAMI_AVATAR_SIZE_DP.toFloat()
                                        val max = MAX_CHAT_LAMI_AVATAR_SIZE_DP.toFloat()
                                        val defaultValue = DEFAULT_CHAT_LAMI_AVATAR_SIZE_DP.toFloat()
                                        val fraction = (defaultValue - min) / (max - min)
                                        val thumbRadius = 10.dp.toPx()
                                        val trackWidth = size.width - thumbRadius * 2
                                        val x = thumbRadius + trackWidth * fraction
                                        val markerShiftPx = 1.dp.toPx()
                                        val centerExtendPx = 2.dp.toPx()
                                        val gapAdjustPx = 1.dp.toPx()
                                        val outerTrimPx = 2.dp.toPx()
                                        val opticalAdjustPx = 1.dp.toPx()
                                        val upperMarkerNudgePx = 0.2.dp.toPx()
                                        val upperStartExtendPx = 1.dp.toPx()
                                        val markerX = x + markerShiftPx

                                        drawLine(
                                            color = defaultMarkerColor,
                                            start = Offset(markerX, size.height * 0.28f + outerTrimPx + opticalAdjustPx + upperMarkerNudgePx - upperStartExtendPx),
                                            end = Offset(markerX, size.height * 0.46f + centerExtendPx - gapAdjustPx + upperMarkerNudgePx),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                        drawLine(
                                            color = defaultMarkerColor,
                                            start = Offset(markerX, size.height * 0.54f - centerExtendPx + gapAdjustPx),
                                            end = Offset(markerX, size.height * 0.72f - outerTrimPx),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                    }
                            )
                        }
                    }
                }
                // 表示設定カード同士の視認性を保つため、最小限の間隔を確保する
                Spacer(modifier = Modifier.height(2.dp))
                Card {
                    SettingsToggleRowItem(
                        headline = "キャラクターアニメーション",
                        supporting = "キャラクターの動きを有効にします",
                        leadingIcon = null,
                        checked = settingsData.characterAnimationEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsPreferences.setCharacterAnimationEnabled(enabled) }
                        }
                    )
                }
                // 表示設定カード同士の視認性を保つため、最小限の間隔を確保する
                Spacer(modifier = Modifier.height(2.dp))
                Card {
                    SettingsToggleRowItem(
                        headline = "ダイナミックカラー",
                        supporting = "システムカラーに合わせて配色を自動調整します",
                        leadingIcon = null,
                        checked = settingsData.useDynamicColor,
                        enabled = true,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsPreferences.updateDynamicColor(enabled) }
                        }
                    )
                }

                // 表示設定カード同士の視認性を保つため、最小限の間隔を確保する
                Spacer(modifier = Modifier.height(2.dp))
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "推論統計表示モード",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "シンプル / 詳細 / 開発者向け から表示量を選べます",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val currentMode = settingsData.inferenceStatsDisplayMode
                        InferenceStatsDisplayMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            settingsPreferences.saveInferenceStatsDisplayMode(mode)
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = currentMode == mode,
                                    onClick = {
                                        scope.launch {
                                            settingsPreferences.saveInferenceStatsDisplayMode(mode)
                                        }
                                    },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = when (mode) {
                                            InferenceStatsDisplayMode.SIMPLE -> "シンプル"
                                            InferenceStatsDisplayMode.DETAILED -> "詳細"
                                            InferenceStatsDisplayMode.DEVELOPER -> "開発者向け"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = when (mode) {
                                            InferenceStatsDisplayMode.SIMPLE -> "主要な統計のみを表示"
                                            InferenceStatsDisplayMode.DETAILED -> "通常の詳細統計を表示"
                                            InferenceStatsDisplayMode.DEVELOPER -> "DEV診断を含む全情報を表示"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                if (BuildConfig.DEBUG) {
                    // DEV診断向けの実験設定のため、DEBUGビルドのみ表示する
                    Spacer(modifier = Modifier.height(2.dp))
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "MediaPipe preferredBackend（実験）",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "DEBUGビルド限定の実験機能です。LiteRT-LM EngineConfig に DEFAULT / CPU / GPU を指定します。Qualcomm QNN/NPUは前提ライブラリとAPI候補を診断し、不足時は実推論をGPUへフォールバックします。変更後はローカルエンジン再作成が必要です。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val currentDryRun = settingsData.preferredBackendDryRunSetting
                            PreferredBackendDryRunSetting.entries.forEach { setting ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                settingsPreferences.savePreferredBackendDryRunSetting(setting)
                                            }
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = currentDryRun == setting,
                                        onClick = {
                                            scope.launch {
                                                settingsPreferences.savePreferredBackendDryRunSetting(setting)
                                            }
                                        },
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val settingLabel = when (setting) {
                                        PreferredBackendDryRunSetting.NPU -> "NPU（候補検出のみ・実推論はGPU）"
                                        PreferredBackendDryRunSetting.QUALCOMM_QNN_NPU -> "Qualcomm QNN/NPU（安全試行・不足時GPU）"
                                        else -> setting.name
                                    }
                                    Text(text = settingLabel, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            item {
                CardSectionHeader(
                    title = "音声",
                    description = "回答の読み上げ設定を変更できます",
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Card {
                    SettingsToggleRowItem(
                        headline = "音声読み上げ",
                        supporting = "OFFにすると回答の読み上げを行いません",
                        leadingIcon = null,
                        checked = ttsEnabled,
                        enabled = true,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsPreferences.setTtsEnabled(enabled) }
                        }
                    )
                }
                if (BuildConfig.DEBUG) {
                    // 開発者向け実験機能のため、DEBUGビルドのみ表示する
                    Spacer(modifier = Modifier.height(2.dp))
                    Card {
                        SettingsToggleRowItem(
                            headline = "文区切りストリーミングTTS",
                            supporting = if (ttsEnabled) {
                                "応答生成中に、文の区切りごとに順次読み上げます（開発者向け）"
                            } else {
                                "音声読み上げがOFFのため変更できません"
                            },
                            leadingIcon = null,
                            checked = effectiveSentenceStreamingTtsChecked,
                            enabled = ttsEnabled,
                            switchColors = SwitchDefaults.colors(
                                disabledUncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledUncheckedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            onCheckedChange = { enabled ->
                                scope.launch { settingsPreferences.setDevEnableStreamingSentenceTts(enabled) }
                            }
                        )
                    }
                }
            }
            item {
                CardSectionHeader(
                    title = "ローカルモデル",
                    description = "端末内で使用する基本モデルを設定します",
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            item {
                Card {
                    SettingsNavRowItem(
                        headline = "ローカル基本モデル",
                        supporting = localBaseModelDisplayName?.takeIf { it.isNotBlank() } ?: "未設定",
                        leadingIcon = null,
                        onClick = { navgationController.navigate(SettingsRoute.LocalBaseModel.route) },
                    )
                }
            }
            item {
                CardSectionHeader(
                    title = "サーバー設定",
                    description = "接続するLLMサーバーのURLと接続状態を管理します",
                    modifier = Modifier.padding(
                        // 下: サーバー設定の見出しとカードの間隔を最小限確保
                        bottom = 2.dp
                    )
                )
            }
            item {
                Card {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            ListItem(
                                headlineContent = {
                                    Text("設定を保存", style = MaterialTheme.typography.titleMedium)
                                },
                                supportingContent = {
                                    Text(
                                        "サーバー設定の変更内容を保存します",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 0.dp)
                                    .padding(end = ServerRowTrailingSlotWidth)
                            )
                            IconButton(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 0.dp),
                                onClick = {
                                    scope.launch {
                                        if (serverInputs.any { it.url.isBlank() }) {
                                            snackbarHostState.showSnackbar(
                                                message = "空のURLを保存できません",
                                                duration = SnackbarDuration.Short
                                            )
                                            return@launch
                                        }
                                        val normalizedInputs = getNormalizedInputs().map { input ->
                                            input.copy(url = normalizeUrlForSave(input.url))
                                        }
                                        val duplicates = detectDuplicateUrls(normalizedInputs)
                                        duplicateUrls = duplicates
                                        if (duplicates.isNotEmpty()) {
                                            connectionStatuses = emptyMap()
                                            snackbarHostState.showSnackbar(
                                                message = "同じURLは複数登録できません",
                                                duration = SnackbarDuration.Short
                                            )
                                            return@launch
                                        }
                                        if (normalizedInputs.any { !validateUrlFormat(it.url).isValid }) {
                                            snackbarHostState.showSnackbar(
                                                message = PORT_ERROR_MESSAGE,
                                                duration = SnackbarDuration.Short
                                            )
                                            return@launch
                                        }
                                        if (serverInputs.none { it.isActive }) {
                                            serverInputs[0] = serverInputs[0].copy(isActive = true)
                                        }
                                        val inputsForValidation = normalizedInputs
                                        isValidatingConnections = true
                                        val validationResults = try {
                                            withContext(Dispatchers.IO) {
                                                validateActiveConnections(inputsForValidation, ::isValidURL)
                                            }
                                        } finally {
                                            isValidatingConnections = false
                                        }
                                        connectionStatuses = validationResults
                                        val unreachableConnections = validationResults.filterValues { !it.isReachable }
                                        val warningMessages = validationResults.values.mapNotNull { it.warningMessage }
                                        if (unreachableConnections.isNotEmpty()) {
                                            snackbarHostState.showSnackbar(
                                                message = "選択中のサーバーに接続できません。入力内容を確認してください",
                                                actionLabel = "ERROR",
                                                duration = SnackbarDuration.Short
                                            )
                                            return@launch
                                        }
                                        if (warningMessages.isNotEmpty()) {
                                            snackbarHostState.showSnackbar(
                                                message = warningMessages.joinToString("\n"),
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                        connectionStatuses = validationResults.mapValues { entry ->
                                            entry.value.copy(errorMessage = null)
                                        }
                                        duplicateUrls = emptyMap()
                                        val inputsToSave = inputsForValidation.mapIndexed { _, input ->
                                            BaseUrl(
                                                id = input.id ?: 0,
                                                url = input.url,
                                                isActive = input.isActive
                                            )
                                        }
                                        val initializationState = saveServers(
                                            inputsToSave,
                                            baseUrlRepository,
                                            modelPreferenceRepository,
                                            RetrofitClient::refreshBaseUrl
                                        )
                                        if (initializationState.usedFallback) {
                                            val fallbackMessage = initializationState.errorMessage
                                                ?: "有効なURLがないためデフォルトにフォールバックしました"
                                            snackbarHostState.showSnackbar(
                                                message = fallbackMessage,
                                                duration = SnackbarDuration.Short
                                            )
                                            val storedUrls = withContext(Dispatchers.IO) { baseUrlRepository.getAll() }
                                            val hasActive = storedUrls.any { it.isActive }
                                            serverInputs.clear()
                                            serverInputs.addAll(
                                                storedUrls.mapIndexed { index, baseUrl ->
                                                    ServerInput(
                                                        id = baseUrl.id,
                                                        url = baseUrl.url,
                                                        isActive = if (hasActive) baseUrl.isActive else index == 0
                                                    )
                                                }
                                            )
                                            connectionStatuses = emptyMap()
                                            val normalizedInputs = getNormalizedInputs()
                                            duplicateUrls = detectDuplicateUrls(normalizedInputs)
                                        } else {
                                            val normalizedActiveBaseUrl =
                                                normalizeUrlForSave(normalizeUrlInput(initializationState.baseUrl))
                                            serverInputs.indices.forEach { i ->
                                                val current = serverInputs[i]
                                                val normalized = normalizeUrlForSave(normalizeUrlInput(current.url))
                                                serverInputs[i] = current.copy(
                                                    url = normalized,
                                                    isActive = normalized == normalizedActiveBaseUrl
                                                )
                                            }
                                            showSuccessSnackbarShort("サーバー設定を保存しました")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Save,
                                    contentDescription = "Save settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            itemsIndexed(serverInputs, key = { _, item -> item.localId }) { index, serverInput ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .padding(end = ServerRowTrailingSlotWidth),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.width(32.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                RadioButton(
                                    modifier = Modifier.offset(x = (-2).dp),
                                    selected = serverInput.isActive,
                                    onClick = {
                                        serverInputs.indices.forEach { i ->
                                            val current = serverInputs[i]
                                            serverInputs[i] = current.copy(isActive = i == index)
                                        }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = serverInput.url,
                                        onValueChange = { newValue ->
                                            val normalized = normalizeUrlInput(newValue)
                                            serverInputs[index] = serverInput.copy(url = normalized)
                                            val normalizedInputs = getNormalizedInputs()
                                            duplicateUrls = detectDuplicateUrls(normalizedInputs)
                                        },
                                        placeholder = { Text("http://host:port", style = LamiTypographyTokens.fieldPlaceholder()) },
                                        label = { Text("Server ${index + 1}", style = LamiTypographyTokens.fieldLabel()) },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = FontFamily.Default,
                                            fontWeight = FontWeight.Normal,
                                        ),
                                        isError = duplicateUrls[serverInput.localId] == true ||
                                            !validateUrlFormat(serverInput.url).isValid ||
                                            connectionStatuses[serverInput.localId]?.isReachable == false,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = if (serverInput.isActive) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            unfocusedBorderColor = if (serverInput.isActive) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            errorBorderColor = MaterialTheme.colorScheme.error,
                                            errorCursorColor = MaterialTheme.colorScheme.error,
                                            errorLabelColor = MaterialTheme.colorScheme.error,
                                            errorLeadingIconColor = MaterialTheme.colorScheme.error,
                                            errorTrailingIconColor = MaterialTheme.colorScheme.error
                                        ),
                                    )
                                    if (isValidatingConnections && serverInput.isActive) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier
                                                        .align(Alignment.Center)
                                                        .offset(y = ServerValidationIndicatorYOffset)
                                                        .size(28.dp),
                                                    strokeWidth = 6.dp
                                                )
                                    }
                                }
                                when {
                                    duplicateUrls[serverInput.localId] == true -> {
                                        Text(
                                            text = "このURLは既に追加されています",
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 4.dp) // 上：入力枠と文言の間隔を最小限確保
                                        )
                                    }
                                    isValidatingConnections && serverInput.isActive -> {
                                        Text(
                                            text = "接続確認中…",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp) // 上：入力枠と文言の間隔を最小限確保
                                        )
                                    }
                                    connectionStatuses[serverInput.localId]?.isReachable == false -> {
                                        val message = connectionStatuses[serverInput.localId]?.errorMessage
                                            ?: "接続できません"
                                        Text(
                                            text = message,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 4.dp) // 上：入力枠と文言の間隔を最小限確保
                                        )
                                    }
                                    connectionStatuses[serverInput.localId]?.warningMessage != null -> {
                                        val message = connectionStatuses[serverInput.localId]?.warningMessage
                                        if (message != null) {
                                            Text(
                                                text = message,
                                                modifier = Modifier.padding(top = 4.dp) // 上：入力枠と文言の間隔を最小限確保
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        IconButton(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 0.dp),
                            enabled = serverInputs.size > 1,
                            onClick = {
                                if (serverInputs.size <= 1) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "最低1件のサーバーを残してください",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                    return@IconButton
                                }
                                val wasActive = serverInputs[index].isActive
                                val updatedInvalidConnections =
                                    connectionStatuses.toMutableMap().apply {
                                        remove(serverInput.localId)
                                    }
                                serverInputs.removeAt(index)
                                connectionStatuses = updatedInvalidConnections
                                val normalizedInputs = getNormalizedInputs()
                                duplicateUrls = detectDuplicateUrls(normalizedInputs)
                                if (wasActive && serverInputs.isNotEmpty()) {
                                    serverInputs[0] = serverInputs[0].copy(isActive = true)
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove server")
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        enabled = serverInputs.size < maxServers,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        onClick = {
                            if (serverInputs.size >= maxServers) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "追加できるサーバー数は最大${maxServers}件です",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            } else {
                                serverInputs.add(
                                    ServerInput(
                                        url = "http://localhost:13511/",
                                        isActive = false
                                    )
                                )
                                val normalizedInputs = getNormalizedInputs()
                                duplicateUrls = detectDuplicateUrls(normalizedInputs)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.width(32.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "Add server",
                                    modifier = Modifier.offset(x = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                CardSectionHeader(
                    title = "アプリ情報",
                    description = "このアプリについて",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                Card(
                    onClick = {
                        settingsBackStackEntry
                            .savedStateHandle
                            .set(ResetSettingsScrollOnReturnFromAboutKey, true)
                        navgationController.navigate(Routes.ABOUT)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    SettingsNavRowItem(
                        headline = stringResource(R.string.about),
                        supporting = "バージョン情報とオープンソースライセンス",
                        leadingIcon = null,
                        onClick = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp)
                            .padding(vertical = 4.dp)
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(36.dp))
            }
            }
            TopFadeOverlay(
                show = showTopFade,
                bg = scaffoldBg,
                height = fadeHeight,
                modifier = Modifier.align(Alignment.TopCenter).zIndex(1f),
                label = "settingsTopFade",
            )
            BottomFadeOverlay(
                show = showBottomFade,
                bg = scaffoldBg,
                height = fadeHeight,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 下: IME 表示時にフェードがキーボードに隠れないよう最小限だけ持ち上げる
                    .padding(bottom = bottomDp)
                    .zIndex(1f),
                label = "settingsBottomFade",
            )
        }
    }
}



@Composable
private fun CardSectionHeader(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    topPadding: Dp = 16.dp
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(top = topPadding)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal suspend fun isValidURL(urlString: String): ConnectionValidationResult {
    val formatResult = validateUrlFormat(urlString)
    if (!formatResult.isValid) {
        return ConnectionValidationResult(
            normalizedUrl = formatResult.normalizedUrl,
            isSuccess = false,
            isReachable = false,
            errorMessage = formatResult.errorMessage
        )
    }
    return try {
        val baseUrl = formatResult.normalizedUrl.trimEnd('/')
        val requestUrl = URL("$baseUrl/api/tags")
        // LAN 内利用を想定し、体感ラグを抑えるためにタイムアウトを短めに設定する
        val connectTimeoutSeconds = 2L
        val readTimeoutSeconds = 3L
        val client = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val request = Request.Builder().url(requestUrl).get().build()

        client.newCall(request).execute().use { response ->
            val code = response.code
            val isSuccess = code in 200..299
            val warningMessage = when (code) {
                301, 302 -> "${requestUrl.host} はリダイレクトを返しました (HTTP $code)。認証やプロキシ設定を確認してください"
                401 -> "${requestUrl.host} に認証が必要です (HTTP $code)。"
                else -> null
            }

            ConnectionValidationResult(
                normalizedUrl = formatResult.normalizedUrl,
                isSuccess = isSuccess,
                isReachable = isSuccess || warningMessage != null,
                warningMessage = warningMessage,
                errorMessage = if (!isSuccess && warningMessage == null) "接続できません (HTTP $code)" else null
            )
        }
    } catch (e: MalformedURLException) {
        ConnectionValidationResult(
            normalizedUrl = formatResult.normalizedUrl,
            isSuccess = false,
            isReachable = false,
            errorMessage = PORT_ERROR_MESSAGE
        )
    } catch (e: IllegalArgumentException) {
        ConnectionValidationResult(
            normalizedUrl = formatResult.normalizedUrl,
            isSuccess = false,
            isReachable = false,
            errorMessage = PORT_ERROR_MESSAGE
        )
    } catch (e: IOException) {
        ConnectionValidationResult(
            normalizedUrl = formatResult.normalizedUrl,
            isSuccess = false,
            isReachable = false,
            errorMessage = "接続できません"
        )
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal suspend fun validateActiveConnections(
    inputs: List<ServerInput>,
    validateConnection: suspend (String) -> ConnectionValidationResult
): Map<String, ConnectionValidationResult> {
    val activeInputs = inputs.filter { it.isActive }
    return activeInputs.associate { input ->
        val validation = validateConnection(input.url)
        input.localId to validation
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal suspend fun saveServers(
    inputsToSave: List<BaseUrl>,
    baseUrlRepository: BaseUrlRepository,
    modelPreferenceRepository: ModelPreferenceRepository,
    refreshBaseUrl: suspend (BaseUrlRepository, ModelPreferenceRepository) -> BaseUrlInitializationState
): BaseUrlInitializationState {
    val initializationState = withContext(Dispatchers.IO) {
        baseUrlRepository.replaceAll(inputsToSave, refreshActive = false)
        refreshBaseUrl(baseUrlRepository, modelPreferenceRepository)
    }
    baseUrlRepository.updateActiveBaseUrl(initializationState.baseUrl)
    return initializationState
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    val dummyNav = rememberNavController()
    MaterialTheme(colorScheme = darkColorScheme()) {
        Settings(dummyNav)
    }
}
