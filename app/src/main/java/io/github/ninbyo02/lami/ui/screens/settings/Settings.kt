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
import androidx.navigation.NavBackStackEntry
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
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRouteMode
import io.github.ninbyo02.lami.ui.screens.home.NpuStandardRoutePreferences
import io.github.ninbyo02.lami.ui.text.MarkdownStreamingMode
import io.github.ninbyo02.lami.util.PORT_ERROR_MESSAGE
import io.github.ninbyo02.lami.util.normalizeUrlInput
import io.github.ninbyo02.lami.util.validateUrlFormat
import io.github.ninbyo02.lami.viewmodels.RemoteProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
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

internal const val LEGACY_QAIRT244_DIAGNOSTIC_TITLE = "Legacy QAIRT244診断"
internal const val LEGACY_QAIRT244_DIAGNOSTIC_DESCRIPTION =
    "旧QAIRT診断経路です。S1〜S5 NPU標準ルートとは別で、通常利用は非推奨です。"

internal const val NPU_EXPERIMENTAL_BACKEND_DESCRIPTION =
    "NPU ローカル: 端末内のSM8750向けNPUモデルを使い、UI・TTS・DB保存・Markdown・擬似Streamingまで有効にします。モデル未読込時は動作しません。"

internal fun npuStandardRouteModeDisplayLabel(mode: NpuStandardRouteMode): String =
    when (mode) {
        NpuStandardRouteMode.OFF -> "OFF"
        NpuStandardRouteMode.S1_ONLY -> "S1 応答表示"
        NpuStandardRouteMode.S2_DB -> "S2 DB保存"
        NpuStandardRouteMode.S3_MARKDOWN -> "S3 Markdown"
        NpuStandardRouteMode.S4A_PSEUDO_STREAMING -> "S4 Streaming"
        NpuStandardRouteMode.FULL -> "S5 TTS"
    }

internal fun npuStandardRouteModeDescription(mode: NpuStandardRouteMode): String =
    when (mode) {
        NpuStandardRouteMode.OFF -> "無効"
        NpuStandardRouteMode.S1_ONLY -> "NPU応答を画面に表示します"
        NpuStandardRouteMode.S2_DB -> "応答をDBに保存します"
        NpuStandardRouteMode.S3_MARKDOWN -> "Markdown表示まで有効にします"
        NpuStandardRouteMode.S4A_PSEUDO_STREAMING -> "擬似Streaming表示まで有効にします"
        NpuStandardRouteMode.FULL -> "TTS読み上げまで有効にします"
    }

internal fun npuStandardRouteMaxOutputTokensDisplayLabel(maxOutputTokens: Int): String =
    maxOutputTokens.toString()

internal fun npuStandardRouteMaxOutputTokensDescription(maxOutputTokens: Int): String =
    "NPU標準ルートのmax_output_tokens=$maxOutputTokens"

fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(
    navgationController: NavController,
    onSaved: () -> Unit = {},
    settingsBackStackEntry: NavBackStackEntry? = null,
) {
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
    val localGenericModelDisplayName by settingsPreferences.localGenericModelDisplayNameFlow
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
            emptyList()
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
    val resetScrollOnReturnFromAbout by
        remember(settingsBackStackEntry) {
            settingsBackStackEntry
                ?.savedStateHandle
                ?.getStateFlow(ResetSettingsScrollOnReturnFromAboutKey, false)
                ?: flowOf(false)
        }
            .collectAsState(initial = false)
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
                ?.savedStateHandle
                ?.set(ResetSettingsScrollOnReturnFromAboutKey, false)
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
                Spacer(modifier = Modifier.height(2.dp))
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "画面の向き",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "発話中の画面回転で読み上げが途切れる場合は、縦画面固定がおすすめです。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val currentScreenOrientationMode = settingsData.screenOrientationMode
                        ScreenOrientationMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            settingsPreferences.saveScreenOrientationMode(mode)
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = currentScreenOrientationMode == mode,
                                    onClick = {
                                        scope.launch {
                                            settingsPreferences.saveScreenOrientationMode(mode)
                                        }
                                    },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = mode.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = mode.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
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
                                            InferenceStatsDisplayMode.DEVELOPER -> "回答下の推論統計カードをタップしてDEV診断を表示"
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
                                text = "推論バックエンド",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "現在ローカル推論で使う backend を選択します。\nAutomatic は当面 CPU 優先です。\nGPU は端末依存で Genericモデルの engine create timeout が起きる可能性があるため、DEV診断目的の Experimental / 非推奨です。\nNPU は標準ルート完走済みの Beta backend として表示します。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val currentBackendSelection = settingsData.inferenceBackendSelection
                            val residentPolicySummary = localInferenceResidencyPolicyForUserFacingSelection(
                                currentBackendSelection,
                            ).toSummary()
                            Text(
                                text = "ローカル常駐方針: ${residentPolicySummary.oneLine}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("settingsResidentBackendPolicySummary"),
                            )
                            if (settingsData.developerAccessEnabled) {
                                Text(
                                    text = residentPolicySummary.diagnosticText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.testTag("settingsResidentBackendPolicyDiagnostics"),
                                )
                            }
                            InferenceBackendSelection.userFacingEntries.forEach { selection ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                settingsPreferences.saveInferenceBackendSelection(selection)
                                            }
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = currentBackendSelection == selection,
                                        onClick = {
                                            scope.launch {
                                                settingsPreferences.saveInferenceBackendSelection(selection)
                                            }
                                        },
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = selection.displayLabel, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (currentBackendSelection == InferenceBackendSelection.NPU) {
                                Text(
                                    text = NPU_EXPERIMENTAL_BACKEND_DESCRIPTION,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (settingsData.developerAccessEnabled) {
                                Text(
                                    text = "NPU standard route phase（developer）",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "S1〜S5 は backend ではなく標準ルートの legacy developer phase です。通常の backend list には NPU ローカル として1項目だけ表示します。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                InferenceBackendSelection.developerNpuPhaseEntries.forEach { selection ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch {
                                                    settingsPreferences.saveInferenceBackendSelection(selection)
                                                }
                                            }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected =
                                                settingsData.npuStandardRouteSelectionSource ==
                                                    NpuStandardRouteSelectionSource.DEVELOPER_PHASE_OVERRIDE &&
                                                    settingsData.npuStandardRouteMode == selection.npuStandardRouteMode,
                                            onClick = {
                                                scope.launch {
                                                    settingsPreferences.saveInferenceBackendSelection(selection)
                                                }
                                            },
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = selection.displayLabel, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                Text(
                                    text = "Phase 6〜8 は debug.lami.npu_standard_route_phase=6..8 で確認済みです。既存 preference key は互換維持します。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    if (settingsData.developerAccessEnabled) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Card {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "チャットDEV診断",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "通常チャットではDEV診断を表示せず、推論統計カードとタップ詳細を優先します。NPU/GPUの詳細調査はこの開発者向け設定とコピー機能から確認します。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Card {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "NPU max output tokens（開発用）",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "開発者向け設定です。NPU標準ルートの出力token上限を比較します。既定は128です。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val currentNpuMaxOutputTokens = settingsData.npuStandardRouteMaxOutputTokens
                                NpuStandardRoutePreferences.selectableMaxOutputTokens.forEach { maxOutputTokens ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch {
                                                    settingsPreferences.saveNpuStandardRouteMaxOutputTokens(maxOutputTokens)
                                                }
                                            }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = currentNpuMaxOutputTokens == maxOutputTokens,
                                            onClick = {
                                                scope.launch {
                                                    settingsPreferences.saveNpuStandardRouteMaxOutputTokens(maxOutputTokens)
                                                }
                                            },
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = npuStandardRouteMaxOutputTokensDisplayLabel(maxOutputTokens),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                text = npuStandardRouteMaxOutputTokensDescription(maxOutputTokens),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Card {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = LEGACY_QAIRT244_DIAGNOSTIC_TITLE,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = LEGACY_QAIRT244_DIAGNOSTIC_DESCRIPTION,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                SettingsToggleRowItem(
                                    headline = "Legacy QAIRT244 ChatScreen route",
                                    supporting = "key=dev_enable_qairt244_sm8750_npu_route。旧QAIRT診断経路用で、NPU標準ルートとは別です。",
                                    leadingIcon = Icons.Filled.BugReport,
                                    checked = settingsData.devEnableQairt244Sm8750NpuRoute,
                                    enabled = true,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            settingsPreferences.saveDevEnableQairt244Sm8750NpuRoute(enabled)
                                        }
                                    },
                                )
                                if (BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
                                    Text(
                                        text = "customBuildExperimentDebugでも通常利用ではなくlegacy診断として扱います。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Text(
                                        text = "Legacy QAIRT244 prompt template",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "legacy ChatScreen route専用。NPU標準ルートのprompt shapingには使いません。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val currentTemplateMode = settingsData.hiddenQairt244PromptTemplateMode
                                    HiddenQairt244PromptTemplateMode.entries.forEach { mode ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    scope.launch {
                                                        settingsPreferences.saveHiddenQairt244PromptTemplateMode(mode)
                                                    }
                                                }
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(
                                                selected = currentTemplateMode == mode,
                                                onClick = {
                                                    scope.launch {
                                                        settingsPreferences.saveHiddenQairt244PromptTemplateMode(mode)
                                                    }
                                                },
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = mode.displayName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                                Text(
                                                    text = mode.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                                if (BuildConfig.CUSTOM_BUILD_EXPERIMENT) {
                                    Text(
                                        text = "DEV NPU ChatScreen route boundary",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "key=dev_enable_npu_chatscreen_route / blocked adapter only / normal selectedPath=npu disabled",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "マークダウン表示モード",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "DEBUGビルド限定の比較機能です。compose-markdownは変更せず、ストリーミング中の補正経路だけを切り替えます。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val currentMarkdownMode = settingsData.markdownStreamingMode
                            MarkdownStreamingMode.entries.forEach { mode ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                settingsPreferences.saveMarkdownStreamingMode(mode)
                                            }
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = currentMarkdownMode == mode,
                                        onClick = {
                                            scope.launch {
                                                settingsPreferences.saveMarkdownStreamingMode(mode)
                                            }
                                        },
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = when (mode) {
                                                MarkdownStreamingMode.LAMI_RECOVERY_V1 -> "Lami Recovery v1（安全補正）"
                                                MarkdownStreamingMode.EDGE_GALLERY_COMPAT -> "Edge Gallery互換（軽量）"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            text = when (mode) {
                                                MarkdownStreamingMode.LAMI_RECOVERY_V1 ->
                                                    "既存のMarkdown補正と安全なストリーミング結合を使います"
                                                MarkdownStreamingMode.EDGE_GALLERY_COMPAT ->
                                                    "\\n を改行へ置換し、repairをバイパスします"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
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
                    description = "端末内で使用するモデルを設定します",
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            item {
                Card {
                    Column {
                        SettingsNavRowItem(
                            headline = LocalModelSlot.NpuPreview.title,
                            supporting = localBaseModelDisplayName?.takeIf { it.isNotBlank() }
                                ?: LocalModelSlot.NpuPreview.description,
                            leadingIcon = null,
                            onClick = { navgationController.navigate(SettingsRoute.LocalBaseModel.route) },
                        )
                        SettingsNavRowItem(
                            headline = LocalModelSlot.GenericFallback.title,
                            supporting = localGenericModelDisplayName?.takeIf { it.isNotBlank() }
                                ?: LocalModelSlot.GenericFallback.description,
                            leadingIcon = null,
                            onClick = { navgationController.navigate(SettingsRoute.LocalGenericFallbackModel.route) },
                        )
                    }
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "プロバイダー",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "サーバーAPI形式を選択します。LemonadeはOpenAI互換プリセットとして扱います。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        RemoteProvider.entries.forEach { provider ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch { settingsPreferences.saveRemoteProvider(provider) }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = settingsData.remoteProvider == provider,
                                    onClick = {
                                        scope.launch { settingsPreferences.saveRemoteProvider(provider) }
                                    },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = provider.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = provider.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (settingsData.remoteProvider == RemoteProvider.LEMONADE) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Lemonade自動アンロード",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "応答後に未使用時間が続いたら、Lemonadeのロード済みモデルを解放して省電力化します。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LemonadeAutoUnloadMode.entries.forEach { mode ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch { settingsPreferences.saveLemonadeAutoUnloadMode(mode) }
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = settingsData.lemonadeAutoUnloadMode == mode,
                                        onClick = {
                                            scope.launch { settingsPreferences.saveLemonadeAutoUnloadMode(mode) }
                                        },
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = mode.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            text = mode.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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
                                        if (serverInputs.isNotEmpty() && serverInputs.none { it.isActive }) {
                                            serverInputs[0] = serverInputs[0].copy(isActive = true)
                                        }
                                        val inputsForValidation = normalizedInputs
                                        isValidatingConnections = true
                                        val validationResults = try {
                                            withContext(Dispatchers.IO) {
                                                validateActiveConnections(inputsForValidation) { url ->
                                                    isValidURL(url, settingsData.remoteProvider)
                                                }
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
                                                ?: "サーバー設定を更新しました"
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
            if (serverInputs.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "サーバーは登録されていません",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "+ でサーバーを追加できます",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                            enabled = true,
                            onClick = {
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
                                        url = "",
                                        isActive = serverInputs.isEmpty()
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
                            ?.savedStateHandle
                            ?.set(ResetSettingsScrollOnReturnFromAboutKey, true)
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

internal suspend fun isValidURL(
    urlString: String,
    provider: RemoteProvider = RemoteProvider.OLLAMA,
): ConnectionValidationResult {
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
        val requestUrl = if (provider.usesOpenAiCompatibleApi()) {
            URL("${provider.toOpenAiCompatibleConfig(baseUrl).baseUrl}models")
        } else {
            URL("$baseUrl/api/tags")
        }
        // LAN 内利用を想定し、体感ラグを抑えるためにタイムアウトを短めに設定する
        val connectTimeoutSeconds = 2L
        val readTimeoutSeconds = 3L
        val client = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val requestBuilder = Request.Builder().url(requestUrl).get()
        if (provider == RemoteProvider.LEMONADE) {
            requestBuilder.header("Authorization", "Bearer lemonade")
        }
        val request = requestBuilder.build()

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
