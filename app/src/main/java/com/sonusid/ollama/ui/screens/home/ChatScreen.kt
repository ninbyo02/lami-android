package com.sonusid.ollama.ui.screens.home

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.sonusid.ollama.BuildConfig
import com.sonusid.ollama.R
import com.sonusid.ollama.UiState
import com.sonusid.ollama.db.entity.Chat
import com.sonusid.ollama.db.entity.Message
import com.sonusid.ollama.db.entity.toInferenceStats
import com.sonusid.ollama.db.entity.isInferenceStatsMissing
import com.sonusid.ollama.db.entity.TitleSource
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.tts.AndroidTtsController
import com.sonusid.ollama.ui.common.LocalAppSnackbarHostState
import com.sonusid.ollama.ui.common.PROJECT_SNACKBAR_SHORT_MS
import com.sonusid.ollama.ui.components.HeaderAvatar
import com.sonusid.ollama.ui.components.InferenceTarget
import com.sonusid.ollama.ui.components.LamiHeaderStatus
import com.sonusid.ollama.ui.components.LocalInferenceEngineState
import com.sonusid.ollama.ui.screens.settings.DEFAULT_CHAT_LAMI_AVATAR_SIZE_DP
import com.sonusid.ollama.ui.screens.settings.MAX_CHAT_LAMI_AVATAR_SIZE_DP
import com.sonusid.ollama.ui.screens.settings.MIN_CHAT_LAMI_AVATAR_SIZE_DP
import com.sonusid.ollama.ui.screens.settings.SettingsPreferences
import com.sonusid.ollama.ui.model.ContextWindowFetchState
import com.sonusid.ollama.ui.model.InferenceStats
import com.sonusid.ollama.ui.theme.LamiTypographyTokens
import com.sonusid.ollama.ui.util.formatOutputTokens
import com.sonusid.ollama.ui.util.formatInferenceTime
import com.sonusid.ollama.ui.util.formatFinishReason
import com.sonusid.ollama.ui.util.formatGenerationDuration
import com.sonusid.ollama.ui.util.formatTimeToFirstToken
import com.sonusid.ollama.ui.util.formatImageInputCount
import com.sonusid.ollama.ui.util.formatModelLoadDuration
import com.sonusid.ollama.ui.util.formatModelName
import com.sonusid.ollama.ui.util.formatPromptEvalDuration
import com.sonusid.ollama.ui.util.formatTokenPerSec
import com.sonusid.ollama.ui.util.formatTotalTokens
import com.sonusid.ollama.util.RuntimeFlags
import com.sonusid.ollama.viewmodels.OllamaViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.yield
import kotlin.math.roundToInt

private val ComposerMinHeight = 44.dp
private val ComposerPillRadius = ComposerMinHeight / 2
private val ComposerButtonSize = 44.dp
private val ComposerButtonVisualSize = ComposerButtonSize - 8.dp
private val ComposerButtonIconSize = 20.dp
private val ComposerButtonIconVisualSize = ComposerButtonIconSize - 4.dp
private val ComposerBottomGapHeight = 8.dp
private val TopGradientOverlayHeight = 24.dp
private val TopGradientOverlayTopOffset = 34.dp
// DEBUG: 上部グラデーションの視認確認で 4dp 上へずらす（調整完了後に 0.dp へ戻しやすくする）
private val TopGradientOverlayYOffset = (-4).dp
private val ChatListTopGapFromGradientBottom = 24.dp
private val EmptyNewConversationBaseTopPadding = 12.dp
// Empty / New conversation のメッセージ開始位置を sprite 直下に近づける補正。
// 現在のレイアウトは gradient bottom を anchor にしているため、
// gradient → sprite bottom の視覚差分をここで補正している。
// UI調整用パラメータなので、位置調整はこの値のみ変更する。
private val EmptyNewConversationTopAdjust = (-120).dp
private val SpriteMessageGap = 16.dp
// メッセージ間の縦余白は初回ペアも含めて常に同値で統一する
private val ChatMessageVerticalGap = 8.dp
private const val MaxComposerAttachments = 10
private const val LOCAL_INFERENCE_PROBE_PROMPT = "hi"
private const val LOCAL_INIT_TIMEOUT_MS = 3000L
private const val LOCAL_GENERATE_TIMEOUT_MS = 30000L

private enum class LocalLiteRtProbeResult {
    SUCCESS,
    API_NOT_CONNECTED,
    CREATE_METHOD_NOT_FOUND,
    CREATE_FAILED,
    GENERATE_METHOD_NOT_FOUND,
    GENERATE_FAILED,
}

private data class LocalInferenceInitializationResult(
    val state: LocalInferenceEngineState,
    val probeResult: LocalLiteRtProbeResult?,
)

private data class LocalInferenceRunResult(
    val state: LocalInferenceEngineState,
    val response: String? = null,
    val trace: LocalInferenceTrace = LocalInferenceTrace(),
)

private enum class LocalStatsAvailability {
    AVAILABLE_NOW,
    DERIVABLE_NOW,
    API_CANDIDATE_ONLY,
    NOT_FOUND,
}

private data class LocalStatsCandidateProbe(
    val availability: LocalStatsAvailability,
    val signature: String? = null,
    val returnTypeName: String? = null,
    val valueSummary: String? = null,
)

private data class LocalInferenceTrace(
    val createMethodSignature: String? = null,
    val optionsBuildPath: String? = null,
    val generateMethodSignature: String? = null,
    val streamingCandidateDetected: Boolean? = null,
    val modelNameProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
    val finishReasonProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
    val outputTokenProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
    val loadTimeProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
    val promptEvalTimeProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
    val evalTimeProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
    val firstTokenProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
    val estimatedTokenProbe: LocalStatsCandidateProbe = LocalStatsCandidateProbe(LocalStatsAvailability.NOT_FOUND),
)

private data class LocalLiteRtGeneratedResponse(
    val response: String? = null,
    val trace: LocalInferenceTrace = LocalInferenceTrace(),
)

private data class LocalLiteRtOptionsBuildResult(
    val options: Any,
    val buildPath: String,
)

private enum class TopPaddingMode {
    NewConversation,
    ExistingConversation,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    navHostController: NavHostController,
    viewModel: OllamaViewModel,
    chatId: Int? = null,
) {

    val uiState by viewModel.uiState.collectAsState()
    val chats by viewModel.chats.collectAsState()
    var effectiveChatId by rememberSaveable { mutableStateOf<Int?>(chatId) }
    var isCreatingChat by rememberSaveable { mutableStateOf(false) }
    var suppressAutoNewChat by rememberSaveable { mutableStateOf(false) }
    var suppressChatContentWhileClosingDrawer by rememberSaveable { mutableStateOf(false) }
    var pendingNavigateChatId by rememberSaveable { mutableStateOf<Int?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    var userPrompt: String by rememberSaveable { mutableStateOf("") }
    var prompt: String by remember { mutableStateOf("") }
    val allChatsState = effectiveChatId?.let {
        viewModel.allMessages(it)
            .map { messages -> messages as List<Message>? }
            .collectAsState(initial = null)
    }
    val allChatsOrNull = allChatsState?.value
    var toggle by remember { mutableStateOf(false) }
    var placeholder by rememberSaveable { mutableStateOf("Enter your prompt ...") }
    var attachSheetOpen by rememberSaveable { mutableStateOf(false) }
    var expandDialogOpen by rememberSaveable { mutableStateOf(false) }
    val selectedModel by viewModel.selectedModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val lamiAnimationStatus by viewModel.lamiAnimationStatus.collectAsState()
    val animationEpochMs by viewModel.animationEpochMs.collectAsState()
    val latestInferenceStats by viewModel.latestInferenceStats.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val snackbarHostState = LocalAppSnackbarHostState.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val settingsPreferences = remember(context.applicationContext) {
        SettingsPreferences(context.applicationContext)
    }
    val savedChatLamiAvatarSizeDp by settingsPreferences.chatLamiAvatarSizeDpFlow.collectAsState(
        initial = DEFAULT_CHAT_LAMI_AVATAR_SIZE_DP,
    )
    val clipboardManager = LocalClipboardManager.current
    val ttsController = remember { AndroidTtsController(context.applicationContext) }
    var selectedImageUriStrings by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var pendingAssistantImageInputCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedInferenceTarget by rememberSaveable { mutableStateOf(InferenceTarget.SERVER) }
    var isLocalInferenceRunning by rememberSaveable { mutableStateOf(false) }
    val localBaseModelFilePath by settingsPreferences.localBaseModelFilePathFlow.collectAsState(initial = null)
    var localInferenceEngineState by rememberSaveable {
        mutableStateOf(LocalInferenceEngineState.UNINITIALIZED)
    }
    // composer fullscreen viewer は回転（構成変更）で閉じないよう Saveable で保持する。
    // Uri は Saveable ではないため String で保持し、表示時に Uri.parse で復元する。
    var composerViewerUriStrings by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var composerViewerInitialIndex by rememberSaveable { mutableStateOf(0) }
    val selectedImageUris = selectedImageUriStrings.map(Uri::parse)
    LaunchedEffect(localBaseModelFilePath) {
        if (localBaseModelFilePath.isNullOrBlank()) {
            localInferenceEngineState = LocalInferenceEngineState.UNINITIALIZED
        }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MaxComposerAttachments),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val imageUris = uris.filter { uri ->
            context.contentResolver.getType(uri)?.startsWith("image/") == true
        }
        if (imageUris.size != uris.size) {
            coroutineScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = "画像のみ添付できます",
                    duration = SnackbarDuration.Short,
                )
            }
        }
        selectedImageUriStrings = (selectedImageUriStrings + imageUris.map(Uri::toString))
            .distinct()
            .take(MaxComposerAttachments)
    }
    val errorMessage = (uiState as? UiState.Error)?.errorMessage
    val streamingResponseText = (uiState as? UiState.Streaming)?.partialText
    val isLocalRespondingUi =
        selectedInferenceTarget == InferenceTarget.LOCAL &&
            isLocalInferenceRunning
    val isServerLoadingUi = uiState is UiState.Loading
    val headerStatusTitleOverride = if (isLocalRespondingUi) "Responding..." else null
    val showLocalRespondingAssistantRow = isLocalRespondingUi
    val lamiUiState by viewModel.lamiUiState.collectAsState()
    // NOTE: debug-only top gradient adjustments. Default OFF.
    val debugTopGradientOrange = false
    val debugTopGradientDownshift = 32.dp
    val debugOverlayEnabled = false
    val topGradientBottomDp = TopGradientOverlayTopOffset + TopGradientOverlayYOffset + TopGradientOverlayHeight
    val chatListTopPaddingDp = topGradientBottomDp + ChatListTopGapFromGradientBottom
    var measuredTopGradientBottomPx by remember { mutableStateOf<Float?>(null) }
    var measuredSpriteBottomPx by remember { mutableStateOf<Float?>(null) }
    var measuredContentTopPx by remember { mutableStateOf<Float?>(null) }
    var openLamiControlRequestKey by remember { mutableStateOf(0) }
    val measuredTopGradientBottomDp = with(LocalDensity.current) { (measuredTopGradientBottomPx ?: 0f).toDp() }
    val effectiveTopGradientBottomDp = if (measuredTopGradientBottomPx != null) measuredTopGradientBottomDp else topGradientBottomDp
    val topPaddingModeMap = remember {
        mutableStateMapOf<Int, TopPaddingMode>()
    }
    val fixedEmptyNewAnchorTopPaddingByChatId = remember {
        mutableStateMapOf<Int, Dp>()
    }
    val lastUserMessageCountByChatId = remember {
        mutableStateMapOf<Int, Int>()
    }
    var measuredComposerTopY by remember { mutableStateOf(0f) }
    var overlayRootTopY by remember { mutableStateOf(0f) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var chatSearchQuery by rememberSaveable { mutableStateOf("") }
    val sortedChats = remember(chats) { chats.sortedByDescending { it.chatId } }
    val filteredChats = remember(sortedChats, chatSearchQuery) {
        filterChatsByTitle(sortedChats, chatSearchQuery)
    }
    var latestMessagePreviewByChatId by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var activeReplayMessageId by remember { mutableStateOf<Int?>(null) }
    var isReplayPlaying by remember { mutableStateOf(false) }
    var selectedInferenceStats by remember { mutableStateOf<InferenceStats?>(null) }
    var selectedLocalTraceForDevSheet by remember { mutableStateOf<LocalInferenceTrace?>(null) }
    var latestLocalTraceForDev by remember { mutableStateOf<LocalInferenceTrace?>(null) }
    var showInferenceStatsSheet by remember { mutableStateOf(false) }

    DisposableEffect(ttsController) {
        ttsController.setOnPlaybackStateChanged { isPlaying ->
            viewModel.onTtsPlaybackChanged(isPlaying)
            isReplayPlaying = isPlaying
            if (!isPlaying) {
                activeReplayMessageId = null
            }
        }
        onDispose {
            viewModel.stopTtsPlayback()
            activeReplayMessageId = null
            isReplayPlaying = false
            ttsController.shutdown()
        }
    }

    LaunchedEffect(chatId) {
        if (chatId != null) {
            suppressAutoNewChat = false
            suppressChatContentWhileClosingDrawer = false
        }
    }

    LaunchedEffect(pendingNavigateChatId) {
        val targetChatId = pendingNavigateChatId ?: return@LaunchedEffect
        try {
            if (drawerState.isOpen) {
                runCatching { drawerState.close() }
            }
            if (RuntimeFlags.isUiTestRuntime()) {
                yield()
            }
            navHostController.navigate(Routes.chat(targetChatId)) {
                launchSingleTop = true
            }
        } finally {
            pendingNavigateChatId = null
            suppressChatContentWhileClosingDrawer = false
        }
    }

    LaunchedEffect(chatId, chats) {
        val resolvedChatId = resolveDefaultChatId(chatId, chats)
        effectiveChatId = resolvedChatId

        if (shouldAutoCreateNewChat(suppressAutoNewChat, resolvedChatId, isCreatingChat)) {
            isCreatingChat = true
            val newChatId = viewModel.insertChatAndReturnId(
                Chat(title = "New chat", titleSource = TitleSource.TEMP)
            )
            effectiveChatId = newChatId
            pendingNavigateChatId = newChatId
        }

        if (resolvedChatId != null) {
            isCreatingChat = false
            suppressAutoNewChat = false
        }
    }

    LaunchedEffect(sortedChats) {
        if (sortedChats.isEmpty()) {
            latestMessagePreviewByChatId = emptyMap()
            return@LaunchedEffect
        }
        val latestMessages = viewModel.getLatestMessagesByChatIds(sortedChats.map { it.chatId })
        latestMessagePreviewByChatId = latestMessages.associate { latestMessage ->
            latestMessage.chatId to formatChatPreview(latestMessage.message)
        }
    }

    LaunchedEffect(availableModels, selectedModel) {
        if (availableModels.size == 1) {
            val singleModelName = availableModels.first().name
            if (selectedModel != singleModelName) {
                viewModel.updateSelectedModel(singleModelName)
            }
        }
    }

    LaunchedEffect(uiState, effectiveChatId) {
        if (toggle) {
            val currentChatId = effectiveChatId
            when (uiState) {
                is UiState.Success -> {
                    val response = (uiState as UiState.Success).outputText
                    if (currentChatId != null) {
                        viewModel.insert(
                            createAssistantMessage(
                                chatId = currentChatId,
                                response = response,
                                latestInferenceStats = latestInferenceStats,
                                imageInputCount = pendingAssistantImageInputCount,
                            )
                        )
                    }
                    if (!ttsController.isInCooldown()) {
                        ttsController.speak(response)
                    }
                    placeholder = "Enter your prompt..."
                    pendingAssistantImageInputCount = null
                    viewModel.resetUiState()
                }

                is UiState.Error -> {
                    if (currentChatId != null) {
                        viewModel.insert(
                            createAssistantMessage(
                                chatId = currentChatId,
                                response = (uiState as UiState.Error).errorMessage,
                            )
                        )
                    }
                    placeholder = "Enter your prompt..."
                    pendingAssistantImageInputCount = null
                    viewModel.resetUiState()
                }

                is UiState.Streaming -> Unit
                else -> Unit
            }
        }
    }

    LaunchedEffect(lamiUiState.lastInteractionTimeMs, lamiUiState.state) {
        val referenceTime = lamiUiState.lastInteractionTimeMs
        val idleTimeoutMs = 6_000L
        delay(idleTimeoutMs)
        viewModel.moveToIdleIfStale(referenceTime, idleTimeoutMs)
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(
                message = errorMessage,
                duration = SnackbarDuration.Short,
                actionLabel = "ERROR"
            )
        }
    }

    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val imeOnlyPx = (imeBottomPx - navBottomPx).coerceAtLeast(0)
    val bottomDp = with(density) { imeOnlyPx.toDp() }

    val createNewChatAndNavigate: () -> Unit = {
        coroutineScope.launch {
            val newChatId = viewModel.insertChatAndReturnId(
                Chat(title = "New chat", titleSource = TitleSource.TEMP)
            )
            effectiveChatId = newChatId
            pendingNavigateChatId = newChatId
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // drawerBg は必ず @Composable スコープ（Home() 内）で評価すること
            val drawerBg = MaterialTheme.colorScheme.surface
            val drawerColor = drawerBg

            // Golden ratio based midpoint (1/φ)
            val fadeMidPos = 0.618f
            val fadeMidAlpha = 0.55f
            val fadeMaxAlpha = 0.68f

            // 下端フェード（透明→濃い）
            val bottomFadeStops = arrayOf(
                0.0f to drawerColor.copy(alpha = 0.0f),
                fadeMidPos to drawerColor.copy(alpha = fadeMidAlpha),
                1.0f to drawerColor.copy(alpha = fadeMaxAlpha),
            )

            // 上端フェード（濃い→透明）※ bottom の反転
            val topFadeStops = arrayOf(
                0.0f to drawerColor.copy(alpha = fadeMaxAlpha),
                fadeMidPos to drawerColor.copy(alpha = fadeMidAlpha),
                1.0f to drawerColor.copy(alpha = 0.0f),
            )
            ModalDrawerSheet(
                // 上：Drawer 側のデフォルト safe drawing inset を無効化して検索窓の先頭位置を詰める
                windowInsets = WindowInsets(0, 0, 0, 0),
                drawerContainerColor = drawerBg,
                drawerTonalElevation = 0.dp,
            ) {
                val newChatButtonHeight = 40.dp
                val newChatListTopGap = 0.dp
                // 下端フェード：帯感を減らすため高さを少し詰める（overlayのみでレイアウトは壊さない）
                val drawerBottomFadeHeight = 32.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .fillMaxWidth()
                        // 上：詰めすぎ防止のため最小限の top padding を残す
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    DrawerSearchPill(
                        value = chatSearchQuery,
                        onValueChange = { chatSearchQuery = it },
                        onClear = { chatSearchQuery = "" },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            // 上：検索ピルと New chat ボタンの間隔を 8dp 維持する
                            .padding(top = 8.dp)
                    ) {
                        if (filteredChats.isEmpty()) {
                            Text(
                                text = "該当なし",
                                // 上：New chat ボタン下から空状態メッセージを表示する
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = newChatButtonHeight + newChatListTopGap + 12.dp,
                                    bottom = 12.dp,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(
                                    top = newChatButtonHeight + newChatListTopGap,
                                    // 最終行がフェードに被らないように bottom padding を増やす
                                    bottom = 12.dp + drawerBottomFadeHeight,
                                )
                            ) {
                                items(filteredChats, key = { it.chatId }) { chat ->
                                    val previewText = latestMessagePreviewByChatId[chat.chatId].orEmpty()
                                    TextButton(
                                        onClick = {
                                            suppressChatContentWhileClosingDrawer = true
                                            suppressAutoNewChat = true
                                            pendingNavigateChatId = chat.chatId
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = chat.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            if (previewText.isNotEmpty()) {
                                                Text(
                                                    text = previewText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 下端フェードは帯に見えないよう max alpha を落として midpoint を前倒し
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(drawerBottomFadeHeight)
                                .drawBehind {
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = bottomFadeStops
                                        )
                                    )
                                }
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                // New chat ボタン下端より 8dp 下までフェードを伸ばし、透明側の境目をさらに目立たせない
                                // ※スレッド開始位置は維持（LazyColumn の contentPadding.top は変更しない）
                                .height(newChatButtonHeight + 6.dp)
                                .drawBehind {
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = topFadeStops
                                        )
                                    )
                                }
                        )
                        ElevatedButton(
                            onClick = createNewChatAndNavigate,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .zIndex(1f)
                                .height(newChatButtonHeight),
                            elevation = ButtonDefaults.elevatedButtonElevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 8.dp,
                                focusedElevation = 6.dp,
                                hoveredElevation = 6.dp,
                                disabledElevation = 0.dp,
                            )
                        ) {
                            Text("New chat")
                        }
                    }
                }
            }
        }
    ) {
    if (suppressChatContentWhileClosingDrawer) {
        Box(modifier = Modifier.fillMaxSize())
    } else {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
    Scaffold(
        // 上部の自動 Insets を無効化し、TopAppBar 側でのみ安全領域を制御する
        contentWindowInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
        topBar = {
            val topAppBarContainerColor = MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(topAppBarContainerColor)
                    .zIndex(1f)
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = topAppBarContainerColor),
                    // TopAppBar の自動 Insets は無効化し、余白発生を防ぐ
                    windowInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    // Chats 画面とヘッダー位置を揃えるため下余白を統一
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                ) {
                    Box(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            measuredSpriteBottomPx = coordinates.positionInRoot().y + coordinates.size.height
                        }
                    ) {
                        HeaderAvatar(
                            baseUrl = baseUrl,
                            selectedModel = selectedModel,
                            lastError = errorMessage,
                            lamiStatus = lamiAnimationStatus,
                            lamiState = lamiUiState.state,
                            availableModels = availableModels,
                            initialAvatarSize = savedChatLamiAvatarSizeDp.dp,
                            minAvatarSize = MIN_CHAT_LAMI_AVATAR_SIZE_DP.dp,
                            maxAvatarSize = MAX_CHAT_LAMI_AVATAR_SIZE_DP.dp,
                            onSelectModel = { modelName ->
                                viewModel.onUserInteraction()
                                viewModel.updateSelectedModel(modelName)
                            },
                            onNavigateSettings = { navHostController.navigate(Routes.SETTINGS) },
                            selectedInferenceTarget = selectedInferenceTarget,
                            onSelectInferenceTarget = { target ->
                                selectedInferenceTarget = target
                            },
                            localInferenceEngineState = localInferenceEngineState,
                            debugOverlayEnabled = false,
                            syncEpochMs = animationEpochMs,
                            openControlRequestKey = openLamiControlRequestKey,
                        )
                    }
                    // ヘッダー内の最小間隔だけ確保して左余白を増やさない
                    Spacer(modifier = Modifier.size(2.dp))
                        LamiHeaderStatus(
                            baseUrl = baseUrl,
                            selectedModel = selectedModel,
                            lastError = errorMessage,
                        lamiStatus = lamiAnimationStatus,
                        lamiState = lamiUiState.state,
                        availableModels = availableModels,
                        onSelectModel = { modelName ->
                            viewModel.onUserInteraction()
                            viewModel.updateSelectedModel(modelName)
                        },
                            onNavigateSettings = { navHostController.navigate(Routes.SETTINGS) },
                            selectedInferenceTarget = selectedInferenceTarget,
                            onSelectInferenceTarget = { target ->
                                selectedInferenceTarget = target
                            },
                            localInferenceEngineState = localInferenceEngineState,
                            debugOverlayEnabled = false,
                            syncEpochMs = animationEpochMs,
                            initialAvatarSize = savedChatLamiAvatarSizeDp.dp,
                        minAvatarSize = MIN_CHAT_LAMI_AVATAR_SIZE_DP.dp,
                        maxAvatarSize = MAX_CHAT_LAMI_AVATAR_SIZE_DP.dp,
                        // title 内で HeaderAvatar を表示しているため二重表示を防ぐ
                        showAvatar = false,
                        onOpenControl = {
                            viewModel.onUserInteraction()
                            openLamiControlRequestKey += 1
                        },
                        statusTitleOverride = headerStatusTitleOverride,
                    )
                }
            },
            actions = {
                IconButton(onClick = {
                    viewModel.onUserInteraction()
                    coroutineScope.launch { drawerState.open() }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "チャット一覧",
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = {
                    viewModel.onUserInteraction()
                    navHostController.navigate(Routes.SETTINGS)
                }) {
                    Icon(
                        painter = painterResource(R.drawable.settings),
                        contentDescription = "設定",
                        modifier = Modifier.size(26.dp)
                    )
                }
                    },
                )
            }
        }, bottomBar = {
        val textMeasurer = rememberTextMeasurer()
        val maxComposerLines = 6
        val composerTextStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            fontSynthesis = FontSynthesis.None,
            fontFamily = FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurface
        )
        val overlayBase = MaterialTheme.colorScheme.background
        val composerBottomGradientEnabled = true

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // IME 分のみを下余白に反映し、非表示時の余白は 0dp にする
                .padding(bottom = bottomDp)
                .onGloballyPositioned { coordinates ->
                    overlayRootTopY = coordinates.positionInRoot().y
                }
                .let { modifier ->
                    if (composerBottomGradientEnabled) {
                        modifier.drawWithContent {
                            val localTop = (measuredComposerTopY - overlayRootTopY).coerceAtLeast(0f)
                            val overlayHeight = (size.height - localTop).coerceAtLeast(1f)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to overlayBase.copy(alpha = 0.0f),
                                        0.5f to overlayBase.copy(alpha = 0.5f),
                                        1.0f to overlayBase.copy(alpha = 1.0f)
                                    ),
                                    startY = localTop,
                                    endY = localTop + overlayHeight
                                ),
                                topLeft = Offset(0f, localTop),
                                size = Size(size.width, overlayHeight)
                            )

                            drawContent()
                        }
                    } else {
                        modifier
                    }
                }
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                Box {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 17.dp)
                    ) {
                    // Surface 内の実幅から固定要素（左右 Spacer/左右ボタン）と TextField 内部余白を差し引く
                    val availableTextWidthDp =
                        maxWidth - 0.dp - ComposerButtonSize - ComposerButtonSize - 0.dp - (4.dp * 2)
                    val availableTextWidthPx = with(density) {
                        availableTextWidthDp.coerceAtLeast(0.dp).toPx().roundToInt().coerceAtLeast(1)
                    }
                    val measuredLines by remember(userPrompt, availableTextWidthPx, composerTextStyle) {
                        derivedStateOf {
                            if (userPrompt.isEmpty()) {
                                1
                            } else {
                                textMeasurer.measure(
                                    text = AnnotatedString(userPrompt),
                                    style = composerTextStyle,
                                    softWrap = true,
                                    maxLines = maxComposerLines,
                                    overflow = TextOverflow.Clip,
                                    constraints = Constraints(maxWidth = availableTextWidthPx)
                                ).lineCount.coerceIn(1, maxComposerLines)
                            }
                        }
                    }
                    val composerShape = RoundedCornerShape(ComposerPillRadius)
                    Surface(
                        shape = composerShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(1f)
                            .onGloballyPositioned { coordinates ->
                                measuredComposerTopY = coordinates.positionInRoot().y
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (selectedImageUris.isNotEmpty()) {
                                    AttachmentPreviewRow(
                                        uris = selectedImageUris,
                                        onOpen = { index ->
                                            composerViewerUriStrings = selectedImageUriStrings.toList()
                                            composerViewerInitialIndex = index
                                        },
                                        onRemoveAt = { removeIndex ->
                                            selectedImageUriStrings = selectedImageUriStrings.filterIndexed { index, _ ->
                                                index != removeIndex
                                            }
                                        },
                                        inComposer = true,
                                    )
                                    // サムネイルと入力行を視認分離する最小限の余白
                                    Spacer(modifier = Modifier.height(2.dp))
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = ComposerMinHeight),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    // 左ボタンを外側へ寄せるための最小余白
                                    Spacer(modifier = Modifier.width(0.dp))

                                    IconButton(
                                        onClick = { attachSheetOpen = true },
                                        modifier = Modifier
                                            .size(ComposerButtonSize)
                                            .align(Alignment.Bottom)
                                            .clip(CircleShape)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(ComposerButtonVisualSize)
                                                .clip(CircleShape)
                                                .background(Color.LightGray.copy(alpha = 0.25f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = "Tools",
                                                modifier = Modifier.size(ComposerButtonIconVisualSize)
                                            )
                                        }
                                    }

                                    BasicTextField(
                                        value = userPrompt,
                                        onValueChange = { newValue ->
                                            userPrompt = newValue
                                            viewModel.onUserInteraction()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .align(Alignment.CenterVertically)
                                            .heightIn(min = 44.dp, max = 180.dp),
                                        singleLine = false,
                                        maxLines = maxComposerLines,
                                        textStyle = composerTextStyle,
                                        interactionSource = interactionSource,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        decorationBox = { innerTextField ->
                                            OutlinedTextFieldDefaults.DecorationBox(
                                                value = userPrompt,
                                                innerTextField = innerTextField,
                                                enabled = true,
                                                singleLine = false,
                                                visualTransformation = VisualTransformation.None,
                                                interactionSource = interactionSource,
                                                placeholder = {
                                                    Text(
                                                        placeholder,
                                                        style = LamiTypographyTokens.chatPlaceholder(),
                                                    )
                                                },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    unfocusedBorderColor = Color.Transparent,
                                                    focusedBorderColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    focusedContainerColor = Color.Transparent
                                                ),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                                            )
                                        }
                                    )

                                    IconButton(
                                        enabled = !selectedModel.isNullOrBlank() &&
                                            (userPrompt.isNotEmpty() || selectedImageUriStrings.isNotEmpty()) &&
                                            !(selectedInferenceTarget == InferenceTarget.LOCAL && isLocalInferenceRunning),
                                        onClick = {
                                            viewModel.onUserInteraction()
                                            if (selectedModel.isNullOrBlank()) {
                                                coroutineScope.launch {
                                                    snackbarHostState.currentSnackbarData?.dismiss()
                                                    snackbarHostState.showSnackbar(
                                                        message = "モデルを選択してください",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                }
                                                return@IconButton
                                            }

                                            Log.i("ChatScreen", "send entry selectedInferenceTarget=$selectedInferenceTarget")
                                            when (selectedInferenceTarget) {
                                                InferenceTarget.SERVER -> {
                                                    val currentChatId = effectiveChatId
                                                    if (currentChatId != null) {
                                                        val requestPrompt = userPrompt
                                                        val requestAttachmentUris = selectedImageUris
                                                        pendingAssistantImageInputCount = requestAttachmentUris.size
                                                        if (requestPrompt.isNotEmpty() || requestAttachmentUris.isNotEmpty()) {
                                                            placeholder = "I'm thinking ... "
                                                            toggle = true
                                                        }
                                                        ttsController.stop()
                                                        viewModel.stopTtsPlayback()
                                                        prompt = requestPrompt
                                                        viewModel.sendPrompt(
                                                            prompt = requestPrompt,
                                                            model = selectedModel,
                                                            attachmentUris = requestAttachmentUris,
                                                            context = context.applicationContext,
                                                            onAttachmentPrepared = { savedAttachmentUriStrings ->
                                                                if (requestPrompt.isNotEmpty() || !savedAttachmentUriStrings.isNullOrEmpty()) {
                                                                    val attachmentJson = savedAttachmentUriStrings
                                                                        ?.takeIf { it.isNotEmpty() }
                                                                        ?.toAttachmentUriStringsJson()
                                                                    viewModel.insert(
                                                                        Message(
                                                                            chatId = currentChatId,
                                                                            message = requestPrompt,
                                                                            isSendbyMe = true,
                                                                            attachmentUriString = savedAttachmentUriStrings?.singleOrNull(),
                                                                            attachmentUriStringsJson = attachmentJson,
                                                                        )
                                                                    )
                                                                }
                                                            },
                                                        )
                                                        prompt = ""
                                                        userPrompt = ""
                                                        selectedImageUriStrings = emptyList()
                                                    } else {
                                                        placeholder = "Setting up a new chat ..."
                                                    }
                                                }

                                                InferenceTarget.LOCAL -> {
                                                    coroutineScope.launch {
                                                        if (isLocalInferenceRunning) return@launch
                                                        isLocalInferenceRunning = true
                                                        try {
                                                            val currentChatId = effectiveChatId
                                                            if (currentChatId == null) {
                                                                placeholder = "Setting up a new chat ..."
                                                                return@launch
                                                            }
                                                            if (selectedImageUriStrings.isNotEmpty()) {
                                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                                snackbarHostState.showSnackbar(
                                                                    message = "ローカル推論では画像入力はまだ未対応です",
                                                                    duration = SnackbarDuration.Short,
                                                                )
                                                                return@launch
                                                            }
                                                            val requestPrompt = userPrompt
                                                            if (requestPrompt.isBlank()) {
                                                                return@launch
                                                            }
                                                            viewModel.insert(
                                                                Message(
                                                                    chatId = currentChatId,
                                                                    message = requestPrompt,
                                                                    isSendbyMe = true,
                                                                )
                                                            )
                                                            prompt = ""
                                                            userPrompt = ""
                                                            selectedImageUriStrings = emptyList()
                                                            ttsController.stop()
                                                            viewModel.stopTtsPlayback()
                                                            localInferenceEngineState = LocalInferenceEngineState.PREPARING
                                                            val localRunStartedAtMs = SystemClock.elapsedRealtime()
                                                            val runResult = withContext(Dispatchers.IO) {
                                                                val executor = Executors.newSingleThreadExecutor()
                                                                val future = executor.submit<LocalInferenceRunResult> {
                                                                    runBlocking {
                                                                        runLocalInferenceOnceEntry(
                                                                            context = context.applicationContext,
                                                                            settingsPreferences = settingsPreferences,
                                                                            localBaseModelFilePath = localBaseModelFilePath,
                                                                            prompt = requestPrompt,
                                                                        )
                                                                    }
                                                                }
                                                                try {
                                                                    future.get(LOCAL_GENERATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                                                                } catch (timeout: TimeoutException) {
                                                                    null
                                                                } finally {
                                                                    future.cancel(true)
                                                                    executor.shutdownNow()
                                                                }
                                                            }
                                                            localInferenceEngineState = runResult?.state
                                                                ?: LocalInferenceEngineState.ERROR
                                                            val localGenerationTimeMs =
                                                                (SystemClock.elapsedRealtime() - localRunStartedAtMs).coerceAtLeast(0L)
                                                            val inventoryState = runResult?.state ?: LocalInferenceEngineState.ERROR
                                                            val inventoryResponseChars = runResult?.response?.length ?: -1
                                                            val timedOut = runResult == null
                                                            Log.i(
                                                                "ChatScreen",
                                                                "LOCAL inference run entry completed. state=${runResult?.state ?: LocalInferenceEngineState.ERROR}, responseBlank=${runResult?.response.isNullOrBlank()}, responseLength=${runResult?.response?.length ?: -1}, responseHead=${runResult?.response?.take(80)}, timedOut=${runResult == null}",
                                                            )
                                                            Log.i(
                                                                "ChatScreen",
                                                                "LOCAL stats inventory: generationTimeMs=$localGenerationTimeMs, responseChars=$inventoryResponseChars, state=$inventoryState, timedOut=$timedOut, responseBlank=${runResult?.response.isNullOrBlank()}, streamingCandidate=${runResult?.trace?.streamingCandidateDetected}, createPath=${runResult?.trace?.createMethodSignature != null}, optionsBuildPath=${runResult?.trace?.optionsBuildPath}, generateMethod=${runResult?.trace?.generateMethodSignature}",
                                                            )
                                                            latestLocalTraceForDev = runResult?.trace
                                                            logLocalStatsInventoryClassification(runResult = runResult)
                                                            if (
                                                                runResult?.state == LocalInferenceEngineState.READY &&
                                                                !runResult.response.isNullOrBlank()
                                                            ) {
                                                                val assistantResponse = sanitizeLocalAssistantResponse(runResult.response)
                                                                Log.i(
                                                                    "ChatScreen",
                                                                    "LOCAL assistant insert payload length=${assistantResponse.length}, head=${assistantResponse.take(80)}",
                                                                )
                                                                viewModel.insert(
                                                                    createAssistantMessage(
                                                                        chatId = currentChatId,
                                                                        response = assistantResponse,
                                                                        generationTimeMs = localGenerationTimeMs,
                                                                    )
                                                                )
                                                                return@launch
                                                            }
                                                            snackbarHostState.currentSnackbarData?.dismiss()
                                                            val dismissJob = launch {
                                                                delay(PROJECT_SNACKBAR_SHORT_MS)
                                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                            }
                                                            snackbarHostState.showSnackbar(
                                                                message = when (runResult?.state) {
                                                                    null -> "ローカル推論エンジンの確認がタイムアウトしました"
                                                                    LocalInferenceEngineState.READY -> "ローカル推論の応答取得に失敗しました"
                                                                    LocalInferenceEngineState.UNINITIALIZED -> "ローカル基本モデルが未設定です"
                                                                    LocalInferenceEngineState.ERROR -> "ローカル推論の応答取得に失敗しました"
                                                                    LocalInferenceEngineState.PREPARING -> "ローカル推論エンジンを準備中です"
                                                                },
                                                                duration = SnackbarDuration.Short,
                                                            )
                                                            dismissJob.cancel()
                                                        } finally {
                                                            isLocalInferenceRunning = false
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(ComposerButtonSize)
                                            .align(Alignment.Bottom)
                                            .clip(CircleShape)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(ComposerButtonVisualSize)
                                                .clip(CircleShape)
                                                .background(Color.LightGray.copy(alpha = 0.25f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ArrowUpward,
                                                contentDescription = "Send Button",
                                                modifier = Modifier.size(ComposerButtonIconVisualSize)
                                            )
                                        }
                                    }

                                    // 右ボタンを外側へ寄せるための最小余白
                                    Spacer(modifier = Modifier.width(0.dp))
                                }
                            }

                        if (measuredLines >= 5) {
                            IconButton(
                                onClick = { expandDialogOpen = true },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.OpenInFull,
                                    contentDescription = "Expand"
                                )
                            }
                        }

                        }
                    }
                }

                }
            }
            // 入力欄の背景外に透明な 8dp ギャップを確保する
            Spacer(
                modifier = Modifier
                    .height(ComposerBottomGapHeight)
            )
        }

        if (expandDialogOpen) {
            Dialog(onDismissRequest = { expandDialogOpen = false }) {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("全体表示", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { expandDialogOpen = false }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close expand dialog"
                                )
                            }
                        }
                        OutlinedTextField(
                            value = userPrompt,
                            onValueChange = {
                                userPrompt = it
                                viewModel.onUserInteraction()
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 360.dp),
                            singleLine = false,
                            maxLines = 16,
                            placeholder = { Text("ここで全文を編集") }
                        )
                        TextButton(
                            onClick = { expandDialogOpen = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("閉じる")
                        }
                    }
                }
            }
        }
    }) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // TopAppBar 配下への潜り込みを防ぐため、Scaffold の上下 inset をここで一元適用
                .padding(
                    top = paddingValues.calculateTopPadding()
                )
                .onGloballyPositioned { coordinates ->
                    measuredContentTopPx = coordinates.positionInRoot().y
                }
                // LazyColumn 側で Insets を二重適用しないよう、この階層で消費する
                .consumeWindowInsets(paddingValues)
        ) {
            val contentModifier = Modifier
                .fillMaxSize()

            if (effectiveChatId == null) {
                Column(
                    modifier = contentModifier.padding(top = effectiveTopGradientBottomDp),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (isCreatingChat) "Creating new chat..." else "Preparing chat...")
                }
            } else if (allChatsOrNull == null) {
                Box(modifier = contentModifier)
            } else {
                val currentChatId = effectiveChatId
                val messagesForListBase: List<Message> = allChatsOrNull
                val messagesForList: List<Message> = if (
                    currentChatId != null &&
                    !streamingResponseText.isNullOrBlank()
                ) {
                    messagesForListBase + Message(
                        chatId = currentChatId,
                        message = streamingResponseText,
                        isSendbyMe = false,
                    )
                } else {
                    messagesForListBase
                }
                LaunchedEffect(effectiveChatId, messagesForList.size, messagesForList.lastOrNull()?.messageID) {
                    if (!BuildConfig.DEBUG) return@LaunchedEffect
                    val assistantMessages = messagesForList.filterNot { it.isSendbyMe }
                    val missingCount = assistantMessages.count { it.isInferenceStatsMissing() }
                    val storedCount = assistantMessages.size - missingCount
                    Log.d(
                        "InferenceStatsAudit",
                        "chatId=$effectiveChatId assistant=${assistantMessages.size} stored=$storedCount missing=$missingCount",
                    )
                }
                val isListForCurrentChatForUi =
                    currentChatId != null &&
                        (messagesForListBase.isEmpty() || messagesForListBase.all { it.chatId == currentChatId })
                val latestAssistantIndex = messagesForList.indexOfLast { !it.isSendbyMe }

                if (!isListForCurrentChatForUi) {
                    Box(modifier = contentModifier)
                } else {
                    key(effectiveChatId) {
                        val anchor = computeLatestUserAnchor(messagesForList)
                        // 仕上げチェック: 初回のみ anchor を使い、それ以降は Saveable な復元位置を優先する
                        val listState = rememberSaveable(effectiveChatId, saver = LazyListState.Saver) {
                            LazyListState(firstVisibleItemIndex = anchor)
                        }
                        // LazyColumn tail layout:
                        // [messages...] + [assistant_streaming_indicator?] + [composer_spacer]
                        val hasLoadingTailItem = isServerLoadingUi

                        val lastContentIndex = remember(messagesForList.size, hasLoadingTailItem) {
                            val lastMessageIndex = messagesForList.lastIndex
                            if (lastMessageIndex < 0) {
                                -1
                            } else {
                                if (hasLoadingTailItem) lastMessageIndex + 1 else lastMessageIndex
                            }
                        }
                        val fabScrollTargetIndex = remember(lastContentIndex) {
                            if (lastContentIndex < 0) {
                                -1
                            } else {
                                // FAB押下時は composer_spacer まで送って、最新回答末尾が見える位置へ寄せる
                                lastContentIndex + 1
                            }
                        }
                        val isNearBottom by remember(listState, lastContentIndex) {
                            derivedStateOf {
                                val layoutInfo = listState.layoutInfo
                                val visibleItems = layoutInfo.visibleItemsInfo
                                val nearBottomEpsilonPx = 24
                                if (lastContentIndex < 0) {
                                    true
                                } else {
                                    val lastVisibleContentItem =
                                        visibleItems.lastOrNull { it.index <= lastContentIndex }
                                    lastVisibleContentItem != null &&
                                        (lastVisibleContentItem.offset + lastVisibleContentItem.size) <=
                                        (layoutInfo.viewportEndOffset + nearBottomEpsilonPx)
                                }
                            }
                        }
                        val shouldShowScrollToBottomFab by remember(
                            listState,
                            latestAssistantIndex,
                            messagesForList.size,
                        ) {
                            derivedStateOf {
                                if (messagesForList.isEmpty()) {
                                    false
                                } else {
                                    val targetMessageIndex =
                                        if (latestAssistantIndex >= 0) {
                                            latestAssistantIndex
                                        } else {
                                            messagesForList.lastIndex
                                        }
                                    if (targetMessageIndex < 0) {
                                        false
                                    } else {
                                        val layoutInfo = listState.layoutInfo
                                        val targetMessageItem =
                                            layoutInfo.visibleItemsInfo.lastOrNull {
                                                it.index == targetMessageIndex
                                            }
                                        val nearBottomEpsilonPx = 24
                                        if (targetMessageItem == null) {
                                            true
                                        } else {
                                            val targetMessageBottom =
                                                targetMessageItem.offset + targetMessageItem.size
                                            targetMessageBottom >
                                                (layoutInfo.viewportEndOffset + nearBottomEpsilonPx)
                                        }
                                    }
                                }
                            }
                        }
                        var isNearBottomSnapshot by remember(effectiveChatId) { mutableStateOf(true) }
                        var autoFollowEnabled by remember(effectiveChatId) { mutableStateOf(true) }
                        var previousMessageCount by remember(effectiveChatId) { mutableStateOf(-1) }
                        var lastAppliedAnchor by remember(effectiveChatId) { mutableStateOf(anchor) }
                        var suppressFollowOnce by remember(effectiveChatId) { mutableStateOf(false) }

                        LaunchedEffect(effectiveChatId) {
                            previousMessageCount = messagesForList.size
                            lastAppliedAnchor = computeLatestUserAnchor(messagesForList)
                            suppressFollowOnce = true
                            autoFollowEnabled = true
                        }

                        LaunchedEffect(listState) {
                            snapshotFlow { listState.isScrollInProgress to isNearBottom }
                                .collect { (isScrolling, nearBottom) ->
                                    isNearBottomSnapshot = nearBottom
                                    if (isScrolling && !nearBottom) {
                                        autoFollowEnabled = false
                                    }
                                    if (nearBottom) {
                                        autoFollowEnabled = true
                                    }
                                }
                        }

                        LaunchedEffect(effectiveChatId, allChatsOrNull?.size) {
                            val currentChatId = effectiveChatId ?: return@LaunchedEffect
                            val allChats = allChatsOrNull ?: return@LaunchedEffect
                            val isListForCurrentChat =
                                allChats.isEmpty() ||
                                    allChats.all { it.chatId == currentChatId }

                            if (!isListForCurrentChat) return@LaunchedEffect

                            if (allChats.isEmpty()) {
                                lastUserMessageCountByChatId[currentChatId] = 0
                                return@LaunchedEffect
                            }

                            val userCount = allChats.count { it.isSendbyMe }
                            val previousUserCount = lastUserMessageCountByChatId[currentChatId]

                            // 初回表示時は記録のみ（スクロールしない）
                            if (previousUserCount == null) {
                                lastUserMessageCountByChatId[currentChatId] = userCount
                                return@LaunchedEffect
                            }

                            // 仕上げチェック: scrollToItem はユーザー送信が増えた時のみ実行
                            if (userCount > previousUserCount) {
                                val newAnchor = computeLatestUserAnchor(allChats)
                                listState.scrollToItem(newAnchor)
                            }

                            lastUserMessageCountByChatId[currentChatId] = userCount
                        }

                        LaunchedEffect(listState.isScrollInProgress) {
                            if (listState.isScrollInProgress) {
                                viewModel.onUserInteraction()
                            }
                        }

                        LaunchedEffect(effectiveChatId, messagesForList) {
                            try {
                                val currentChatId = effectiveChatId ?: return@LaunchedEffect

                                // 初期同期ガード
                                if (previousMessageCount == -1) {
                                    previousMessageCount = messagesForList.size
                                    lastAppliedAnchor = computeLatestUserAnchor(messagesForList)
                                    return@LaunchedEffect
                                }

                                val isListForCurrentChat =
                                    messagesForList.isEmpty() ||
                                        messagesForList.all { it.chatId == currentChatId }

                                if (!isListForCurrentChat) return@LaunchedEffect
                                val currentMessageCount = messagesForList.size
                                val appended = currentMessageCount > previousMessageCount
                                val currentAnchor = computeLatestUserAnchor(messagesForList)
                                val followSuppressedByAnchorUpdate = currentAnchor != lastAppliedAnchor

                                if (followSuppressedByAnchorUpdate) {
                                    lastAppliedAnchor = currentAnchor
                                    suppressFollowOnce = true
                                }

                                if (messagesForList.isNotEmpty()) {
                                    val lastIndex = messagesForList.lastIndex
                                    if (appended && isNearBottomSnapshot && autoFollowEnabled && !suppressFollowOnce && lastIndex >= 0) {
                                        listState.scrollToItem(lastIndex)
                                    }
                                }

                                previousMessageCount = currentMessageCount

                                if (messagesForList.isEmpty()) return@LaunchedEffect

                                if (!topPaddingModeMap.containsKey(currentChatId)) {
                                    val firstIsUser =
                                        messagesForList.firstOrNull()?.isSendbyMe == true

                                    topPaddingModeMap[currentChatId] =
                                        if (firstIsUser) {
                                            TopPaddingMode.NewConversation
                                        } else {
                                            TopPaddingMode.ExistingConversation
                                        }
                                }
                            } finally {
                                suppressFollowOnce = false
                            }
                        }
                        val mode = topPaddingModeMap[effectiveChatId]
                            ?: TopPaddingMode.ExistingConversation
                        val resolvedGradientStartTopPaddingDp =
                            effectiveTopGradientBottomDp + EmptyNewConversationBaseTopPadding
                        val resolvedSpriteAnchorTopPaddingDp =
                            if (measuredSpriteBottomPx != null && measuredContentTopPx != null) {
                                with(LocalDensity.current) {
                                    val spriteAnchorDeltaPx =
                                        (measuredSpriteBottomPx!! - measuredContentTopPx!!)
                                            .coerceAtLeast(0f)
                                            .roundToInt()
                                    spriteAnchorDeltaPx.toDp()
                                } + SpriteMessageGap
                            } else {
                                null
                            }
                        val fixedEmptyNewAnchorTopPaddingDp =
                            effectiveChatId?.let { currentChatId ->
                                fixedEmptyNewAnchorTopPaddingByChatId[currentChatId]
                            }
                        LaunchedEffect(effectiveChatId, resolvedSpriteAnchorTopPaddingDp) {
                            val currentChatId = effectiveChatId ?: return@LaunchedEffect
                            val resolved = resolvedSpriteAnchorTopPaddingDp ?: return@LaunchedEffect
                            if (!fixedEmptyNewAnchorTopPaddingByChatId.containsKey(currentChatId)) {
                                fixedEmptyNewAnchorTopPaddingByChatId[currentChatId] = resolved
                            }
                        }
                        val emptyNewConversationAnchorTopPaddingDp =
                            ((fixedEmptyNewAnchorTopPaddingDp
                                ?: resolvedSpriteAnchorTopPaddingDp)
                                ?: (resolvedGradientStartTopPaddingDp + EmptyNewConversationTopAdjust))
                                .coerceAtLeast(0.dp)
                        val messageListTopPaddingDp = when {
                            // Empty / New は共通アンカーを利用する
                            messagesForList.isEmpty() -> emptyNewConversationAnchorTopPaddingDp
                            mode == TopPaddingMode.NewConversation ->
                                emptyNewConversationAnchorTopPaddingDp
                            // Existing は従来どおり会話一覧の top gap を利用する
                            else -> chatListTopPaddingDp
                        }
                        Box(modifier = contentModifier) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                // 入力欄の背後まで本文を描画し、ガター領域を透明表示にする
                                contentPadding = PaddingValues(
                                    // 新規/既存の初期判定で top padding を固定し、会話途中で切り替えないことでジャンプを防ぐ
                                    top = messageListTopPaddingDp,
                                    start = 0.dp,
                                    end = 0.dp,
                                    bottom = 0.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(ChatMessageVerticalGap),
                                state = listState,
                            ) {
                                if (messagesForList.isEmpty()) {
                                    item(key = "empty-state") {
                                        PlainAssistantMessage(
                                            message = "ラミィがお手伝いします。\n今日は何をしますか？",
                                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 10.dp)
                                        )
                                    }
                                } else {
                                    itemsIndexed(
                                        items = messagesForList,
                                        key = { _, message -> message.messageID.takeIf { it != 0 } ?: "${message.chatId}-${message.message}" }
                                    ) { index, message ->
                                        if (message.isSendbyMe) {
                                            ChatBubble(
                                                message = message.message,
                                                isSentByMe = message.isSendbyMe,
                                                attachmentUriString = message.attachmentUriString,
                                                attachmentUriStringsJson = message.attachmentUriStringsJson,
                                            )
                                        } else {
                                            val messageInferenceStats =
                                                // 推論統計は保存済み assistant message の値のみを表示する。
                                                message.toInferenceStats()
                                            PlainAssistantMessage(
                                                message = message.message,
                                                showMessageActions = true,
                                                isReplaying = isReplayPlaying && activeReplayMessageId == message.messageID,
                                                onReplayClick = {
                                                    activeReplayMessageId = message.messageID
                                                    ttsController.speak(message.message)
                                                },
                                                onStopReplayClick = {
                                                    ttsController.stop()
                                                    activeReplayMessageId = null
                                                },
                                                onCopyAllClick = {
                                                    clipboardManager.setText(AnnotatedString(message.message))
                                                },
                                                inferenceStats = messageInferenceStats,
                                                onInferenceStatsClick = messageInferenceStats?.let {
                                                    {
                                                        selectedInferenceStats = it
                                                        selectedLocalTraceForDevSheet = if (isLocalMinimalInferenceStats(it)) {
                                                            latestLocalTraceForDev
                                                        } else {
                                                            null
                                                        }
                                                        showInferenceStatsSheet = true
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                                if (showLocalRespondingAssistantRow) {
                                    item(key = "local_responding_indicator") {
                                        PlainAssistantMessage(
                                            message = "応答中...",
                                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 10.dp)
                                        )
                                    }
                                }
                                if (isServerLoadingUi) {
                                    item(key = "assistant_streaming_indicator") {
                                        AssistantStreamingIndicator()
                                    }
                                }
                                item(key = "composer_spacer") {
                                    // IME 表示中でも末尾メッセージへ到達できるよう、既存の IME 分だけ末尾余白へ加算する
                                    Spacer(modifier = Modifier.height(ComposerMinHeight + ComposerBottomGapHeight + bottomDp))
                                }
                            }

                            if (shouldShowScrollToBottomFab) {
                                SmallFloatingActionButton(
                                    onClick = {
                                        if (fabScrollTargetIndex >= 0) {
                                            autoFollowEnabled = true
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(fabScrollTargetIndex)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        // 入力欄と下部グラデーションの上に重ねるため、末尾ガターより上へ配置する
                                        .padding(end = 16.dp, bottom = ComposerMinHeight + ComposerBottomGapHeight + bottomDp + 16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "最新へ"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

            if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // エラーバナーの上端だけは詰めて、他方向の余白を維持
                        .padding(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    ElevatedButton(onClick = { viewModel.loadAvailableModels() }) {
                        Text("再試行")
                    }
                }
            }

        }

        val topColor = MaterialTheme.colorScheme.background
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                // 上部グラデーションはスクロール領域と独立した画面固定オーバーレイとして描画する
                .zIndex(10f)
                // 上部グラデーションの見た目サイズは維持し、表示位置のみ固定する
                .offset(y = TopGradientOverlayTopOffset + TopGradientOverlayYOffset + debugTopGradientDownshift)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // IME の表示有無に関係なく上部グラデの高さを固定する
                    .height(TopGradientOverlayHeight)
                    .onGloballyPositioned { coordinates ->
                        measuredTopGradientBottomPx = coordinates.positionInRoot().y + coordinates.size.height
                    }
                    .clipToBounds()
                    .background(
                        brush = run {
                            // 既存挙動を維持しつつ、デバッグ時のみ先頭カラーをオレンジ系に差し替える。
                            val debugTint = if (debugTopGradientOrange) MaterialTheme.colorScheme.tertiary else null
                            val topGradientColor = debugTint ?: topColor
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to topGradientColor.copy(alpha = 1.0f),
                                    0.5f to topColor.copy(alpha = 0.6f),
                                    1.0f to topColor.copy(alpha = 0.0f)
                                )
                            )
                        }
                    )
            )
        }
    }

    if (showInferenceStatsSheet && selectedInferenceStats != null) {
        val stats = selectedInferenceStats
        val inferenceStatsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            sheetState = inferenceStatsSheetState,
            onDismissRequest = {
                showInferenceStatsSheet = false
                selectedInferenceStats = null
                selectedLocalTraceForDevSheet = null
            },
        ) {
            stats?.let { InferenceStatsSheetContent(it, localTraceForDev = selectedLocalTraceForDevSheet) }
        }
    }

    if (attachSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { attachSheetOpen = false },
        ) {
            ListItem(
                modifier = Modifier.clickable {
                    attachSheetOpen = false
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                headlineContent = { Text("Attach image") },
            )
            ListItem(
                modifier = Modifier.clickable { attachSheetOpen = false },
                headlineContent = { Text("Paste from clipboard") },
            )
            ListItem(
                modifier = Modifier.clickable { attachSheetOpen = false },
                headlineContent = { Text("Settings") },
            )
        }
    }

    composerViewerUriStrings?.let { uriStrings ->
        if (uriStrings.isEmpty()) {
            composerViewerUriStrings = null
            composerViewerInitialIndex = 0
        } else {
            val attachmentUris = uriStrings.map(Uri::parse)
            val safeIndex = composerViewerInitialIndex.coerceIn(0, attachmentUris.lastIndex.coerceAtLeast(0))
            AttachmentFullscreenViewer(
                attachmentUris = attachmentUris,
                initialIndex = safeIndex,
                onDismiss = {
                    composerViewerUriStrings = null
                    composerViewerInitialIndex = 0
                },
            )
        }
    }
}
}
}

private suspend fun initializeLocalInferenceEngineEntry(
    context: Context,
    settingsPreferences: SettingsPreferences,
    localBaseModelFilePath: String?,
): LocalInferenceInitializationResult {
    val modelPath = resolveLocalBaseModelPathOrNull(
        settingsPreferences = settingsPreferences,
        localBaseModelFilePath = localBaseModelFilePath,
    ) ?: return LocalInferenceInitializationResult(
        state = LocalInferenceEngineState.UNINITIALIZED,
        probeResult = null,
    )

    val probeResult = loadLocalInferenceEngine(context = context, modelPath = modelPath)
    val state = if (probeResult == LocalLiteRtProbeResult.SUCCESS) {
        LocalInferenceEngineState.READY
    } else {
        LocalInferenceEngineState.ERROR
    }
    return LocalInferenceInitializationResult(state = state, probeResult = probeResult)
}

private suspend fun runLocalInferenceOnceEntry(
    context: Context,
    settingsPreferences: SettingsPreferences,
    localBaseModelFilePath: String?,
    prompt: String,
): LocalInferenceRunResult {
    val modelPath = resolveLocalBaseModelPathOrNull(
        settingsPreferences = settingsPreferences,
        localBaseModelFilePath = localBaseModelFilePath,
    ) ?: return LocalInferenceRunResult(state = LocalInferenceEngineState.UNINITIALIZED)

    val generated = generateLiteRtResponseViaReflection(
        context = context,
        modelPath = modelPath,
        prompt = prompt,
    )
    val response = generated.response
    return if (response.isNullOrBlank()) {
        LocalInferenceRunResult(
            state = LocalInferenceEngineState.ERROR,
            trace = generated.trace,
        )
    } else {
        LocalInferenceRunResult(
            state = LocalInferenceEngineState.READY,
            response = response,
            trace = generated.trace,
        )
    }
}

private suspend fun resolveLocalBaseModelPathOrNull(
    settingsPreferences: SettingsPreferences,
    localBaseModelFilePath: String?,
): String? {
    val validPathFromSettings = runCatching {
        settingsPreferences.getValidLocalBaseModelPathOrNull()
    }.getOrElse {
        Log.e("ChatScreen", "Failed to resolve valid local base model path from settings", it)
        return null
    }
    if (validPathFromSettings != null) {
        return validPathFromSettings
    }

    val fallbackPath = localBaseModelFilePath?.takeIf { it.isNotBlank() } ?: return null
    val fallbackFile = File(fallbackPath)
    return fallbackPath.takeIf {
        fallbackFile.isFile &&
            fallbackFile.canRead() &&
            fallbackFile.name.endsWith(".litertlm", ignoreCase = true)
    }
}

private fun loadLocalInferenceEngine(
    context: Context,
    modelPath: String,
): LocalLiteRtProbeResult {
    val modelFile = File(modelPath)
    if (!modelFile.isFile || !modelFile.canRead()) {
        Log.w("ChatScreen", "Local model file is not readable. path=$modelPath")
        return LocalLiteRtProbeResult.CREATE_FAILED
    }

    return runCatching {
        tryLoadLiteRtLmViaReflection(context = context, modelPath = modelPath)
    }.getOrElse {
        Log.e("ChatScreen", "Failed to load local inference engine. path=$modelPath", it)
        LocalLiteRtProbeResult.CREATE_FAILED
    }
}

private fun tryLoadLiteRtLmViaReflection(
    context: Context,
    modelPath: String,
): LocalLiteRtProbeResult {
    val llmInferenceClass = runCatching {
        Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference")
    }.getOrElse { throwable ->
        Log.w("ChatScreen", "LiteRT-LM class not found: API is not connected in this build.", throwable)
        return LocalLiteRtProbeResult.API_NOT_CONNECTED
    }

    val createFromOptionsMethod = llmInferenceClass.methods.firstOrNull { method ->
        method.name == "createFromOptions" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == Context::class.java
    } ?: run {
        Log.w("ChatScreen", "LiteRT-LM createFromOptions(Context, Options) method not found.")
        return LocalLiteRtProbeResult.CREATE_METHOD_NOT_FOUND
    }
    Log.i("ChatScreen", "LiteRT-LM createFromOptions found: ${createFromOptionsMethod.toGenericString()}")

    val optionsClass = createFromOptionsMethod.parameterTypes[1]
    Log.i("ChatScreen", "LiteRT-LM options class: ${optionsClass.name}")
    val options = runCatching {
        buildLiteRtOptionsViaReflection(optionsClass = optionsClass, modelPath = modelPath)
    }.getOrElse { throwable ->
        if (throwable is NoSuchMethodException) {
            Log.w("ChatScreen", "LiteRT-LM options/builder method not found.", throwable)
            return LocalLiteRtProbeResult.CREATE_METHOD_NOT_FOUND
        }
        Log.e("ChatScreen", "LiteRT-LM options build failed.", throwable)
        return LocalLiteRtProbeResult.CREATE_FAILED
    }

    val created = runCatching {
        createFromOptionsMethod.invoke(null, context, options)
    }.getOrElse { throwable ->
        Log.e("ChatScreen", "LiteRT-LM createFromOptions invocation failed.", throwable)
        return LocalLiteRtProbeResult.CREATE_FAILED
    }
    Log.i("ChatScreen", "LiteRT-LM createFromOptions invoke succeeded.")

    if (created == null) {
        Log.w("ChatScreen", "LiteRT-LM createFromOptions returned null instance.")
        return LocalLiteRtProbeResult.CREATE_FAILED
    }

    val hasStreamingApiCandidate = probeLiteRtStreamingApiViaReflection()
    if (hasStreamingApiCandidate) {
        Log.i("ChatScreen", "LiteRT-LM streaming API candidate detected via reflection probe.")
    } else {
        Log.i("ChatScreen", "LiteRT-LM streaming API candidate not detected in this build.")
    }

    return try {
        tryCheckLiteRtLmGenerateViaReflection(created)
    } finally {
        runCatching {
            (created as? AutoCloseable)?.close()
        }
    }
}

private fun generateLiteRtResponseViaReflection(
    context: Context,
    modelPath: String,
    prompt: String,
): LocalLiteRtGeneratedResponse {
    var trace = LocalInferenceTrace()
    val llmInferenceClass = runCatching {
        Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference")
    }.getOrElse { throwable ->
        Log.w("ChatScreen", "LiteRT-LM class not found for response generation.", throwable)
        return LocalLiteRtGeneratedResponse(trace = trace)
    }

    val createFromOptionsMethod = llmInferenceClass.methods.firstOrNull { method ->
        method.name == "createFromOptions" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == Context::class.java
    } ?: run {
        Log.w("ChatScreen", "LiteRT-LM createFromOptions(Context, Options) method not found for response generation.")
        return LocalLiteRtGeneratedResponse(trace = trace)
    }
    trace = trace.copy(createMethodSignature = createFromOptionsMethod.toGenericString())
    val optionsBuildResult = runCatching {
        buildLiteRtOptionsViaReflection(
            optionsClass = createFromOptionsMethod.parameterTypes[1],
            modelPath = modelPath,
        )
    }.getOrElse { throwable ->
        Log.e("ChatScreen", "LiteRT-LM options build failed for response generation.", throwable)
        return LocalLiteRtGeneratedResponse(trace = trace)
    }
    trace = trace.copy(optionsBuildPath = optionsBuildResult.buildPath)

    val inferenceInstance = runCatching {
        createFromOptionsMethod.invoke(null, context, optionsBuildResult.options)
    }.getOrElse { throwable ->
        Log.e("ChatScreen", "LiteRT-LM createFromOptions invocation failed for response generation.", throwable)
        return LocalLiteRtGeneratedResponse(trace = trace)
    } ?: run {
        Log.w("ChatScreen", "LiteRT-LM createFromOptions returned null instance for response generation.")
        return LocalLiteRtGeneratedResponse(trace = trace)
    }

    val streamingCandidateDetected = probeLiteRtStreamingApiViaReflection()
    trace = trace.copy(streamingCandidateDetected = streamingCandidateDetected)
    return try {
        generateLiteRtStringResponseOnceViaReflection(
            inferenceInstance = inferenceInstance,
            prompt = prompt,
            trace = trace,
        )
    } finally {
        runCatching {
            (inferenceInstance as? AutoCloseable)?.close()
        }
    }
}

@Throws(NoSuchMethodException::class)
private fun buildLiteRtOptionsViaReflection(
    optionsClass: Class<*>,
    modelPath: String,
): LocalLiteRtOptionsBuildResult {
    var buildPath = "builderMethod"
    val builder = optionsClass.methods.firstOrNull { method ->
        method.name == "builder" &&
            method.parameterTypes.isEmpty() &&
            java.lang.reflect.Modifier.isStatic(method.modifiers)
    }?.let { method ->
        Log.i("ChatScreen", "LiteRT-LM builder factory found: ${method.toGenericString()}")
        runCatching { method.invoke(null) }.getOrElse { throwable ->
            throw IllegalStateException("LiteRT-LM builder() invoke failed.", throwable)
        }
    } ?: run {
        buildPath = "builderConstructor"
        val builderClass = optionsClass.declaredClasses.firstOrNull { it.simpleName == "Builder" }
            ?: throw NoSuchMethodException("LiteRT-LM Options.Builder class not found.")
        Log.i("ChatScreen", "LiteRT-LM builder class found: ${builderClass.name}")
        val constructor = builderClass.declaredConstructors.firstOrNull { it.parameterTypes.isEmpty() }
            ?: throw NoSuchMethodException("LiteRT-LM Options.Builder() constructor not found.")
        runCatching {
            constructor.isAccessible = true
            constructor.newInstance()
        }.getOrElse { throwable ->
            throw IllegalStateException("LiteRT-LM Options.Builder() invoke failed.", throwable)
        }
    }

    val builderClass = builder.javaClass
    val modelPathSetter = listOf("setModelPath", "setModelFilePath", "setLlmModelPath")
        .firstNotNullOfOrNull { methodName ->
            builderClass.methods.firstOrNull { method ->
                method.name == methodName &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == String::class.java
            }
        } ?: throw NoSuchMethodException("LiteRT-LM modelPath setter not found.")
    Log.i("ChatScreen", "LiteRT-LM modelPath setter found: ${modelPathSetter.toGenericString()}")

    val configuredBuilder = runCatching {
        modelPathSetter.invoke(builder, modelPath) ?: builder
    }.getOrElse { throwable ->
        throw IllegalStateException("LiteRT-LM modelPath setter invoke failed.", throwable)
    }

    var optionalConfiguredBuilder = configuredBuilder
    fun applyOptionalSetter(
        methodName: String,
        expectedType: Class<*>,
        value: Any,
    ) {
        val setter = optionalConfiguredBuilder.javaClass.methods.firstOrNull { method ->
            method.name == methodName &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == expectedType
        }
        if (setter == null) {
            Log.i("ChatScreen", "LiteRT-LM optional setter not found: $methodName(${expectedType.simpleName})")
            return
        }
        optionalConfiguredBuilder = runCatching {
            setter.invoke(optionalConfiguredBuilder, value) ?: optionalConfiguredBuilder
        }.onSuccess {
            Log.i("ChatScreen", "LiteRT-LM optional setter applied: $methodName(${expectedType.simpleName})=$value")
        }.getOrElse { throwable ->
            Log.w("ChatScreen", "LiteRT-LM optional setter invoke failed: $methodName(${expectedType.simpleName})", throwable)
            optionalConfiguredBuilder
        }
    }

    applyOptionalSetter(methodName = "setMaxTokens", expectedType = Int::class.javaPrimitiveType!!, value = 16)
    applyOptionalSetter(methodName = "setMaxTokens", expectedType = Int::class.javaObjectType, value = 16)
    applyOptionalSetter(methodName = "setMaxOutputTokens", expectedType = Int::class.javaPrimitiveType!!, value = 16)
    applyOptionalSetter(methodName = "setMaxOutputTokens", expectedType = Int::class.javaObjectType, value = 16)
    applyOptionalSetter(methodName = "setTopK", expectedType = Int::class.javaPrimitiveType!!, value = 1)
    applyOptionalSetter(methodName = "setTopK", expectedType = Int::class.javaObjectType, value = 1)
    applyOptionalSetter(methodName = "setTemperature", expectedType = Float::class.javaPrimitiveType!!, value = 0.0f)
    applyOptionalSetter(methodName = "setTemperature", expectedType = Float::class.javaObjectType, value = 0.0f)
    applyOptionalSetter(methodName = "setRandomSeed", expectedType = Int::class.javaPrimitiveType!!, value = 1)
    applyOptionalSetter(methodName = "setRandomSeed", expectedType = Int::class.javaObjectType, value = 1)
    applyOptionalSetter(
        methodName = "setEnableVisionModality",
        expectedType = Boolean::class.javaPrimitiveType!!,
        value = false,
    )
    applyOptionalSetter(
        methodName = "setEnableVisionModality",
        expectedType = Boolean::class.javaObjectType,
        value = false,
    )
    applyOptionalSetter(
        methodName = "setEnableAudioModality",
        expectedType = Boolean::class.javaPrimitiveType!!,
        value = false,
    )
    applyOptionalSetter(
        methodName = "setEnableAudioModality",
        expectedType = Boolean::class.javaObjectType,
        value = false,
    )

    listOf("setPreferredBackend", "setBackend", "setPreferredDelegate").forEach { methodName ->
        val backendLikeSetter = optionalConfiguredBuilder.javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterTypes.size == 1
        }
        when {
            backendLikeSetter == null -> {
                Log.i("ChatScreen", "LiteRT-LM optional setter not found: $methodName(?)")
            }
            backendLikeSetter.parameterTypes[0] == String::class.java -> {
                optionalConfiguredBuilder = runCatching {
                    backendLikeSetter.invoke(optionalConfiguredBuilder, "CPU") ?: optionalConfiguredBuilder
                }.onSuccess {
                    Log.i("ChatScreen", "LiteRT-LM optional setter applied: $methodName(String)=CPU")
                }.getOrElse { throwable ->
                    Log.w("ChatScreen", "LiteRT-LM optional setter invoke failed: $methodName(String)", throwable)
                    optionalConfiguredBuilder
                }
            }
            backendLikeSetter.parameterTypes[0] == Int::class.javaPrimitiveType ||
                backendLikeSetter.parameterTypes[0] == Int::class.javaObjectType -> {
                optionalConfiguredBuilder = runCatching {
                    backendLikeSetter.invoke(optionalConfiguredBuilder, 0) ?: optionalConfiguredBuilder
                }.onSuccess {
                    Log.i("ChatScreen", "LiteRT-LM optional setter applied: $methodName(Int)=0")
                }.getOrElse { throwable ->
                    Log.w("ChatScreen", "LiteRT-LM optional setter invoke failed: $methodName(Int)", throwable)
                    optionalConfiguredBuilder
                }
            }
            else -> {
                Log.i(
                    "ChatScreen",
                    "LiteRT-LM optional setter skipped: $methodName(${backendLikeSetter.parameterTypes[0].name})",
                )
            }
        }
    }

    // --- generation length tuning (safe optional) ---
    applyOptionalSetter(
        methodName = "setMaxTokens",
        expectedType = Int::class.java,
        value = 128,
    )

    applyOptionalSetter(
        methodName = "setMaxOutputTokens",
        expectedType = Int::class.java,
        value = 128,
    )

    // 一部API互換用（念のため）
    applyOptionalSetter(
        methodName = "maxTokens",
        expectedType = Int::class.java,
        value = 128,
    )

    applyOptionalSetter(
        methodName = "maxOutputTokens",
        expectedType = Int::class.java,
        value = 128,
    )

    val buildMethod = optionalConfiguredBuilder.javaClass.methods.firstOrNull { method ->
        method.name == "build" && method.parameterTypes.isEmpty()
    } ?: throw NoSuchMethodException("LiteRT-LM build() method not found.")

    val builtOptions = runCatching {
        buildMethod.invoke(optionalConfiguredBuilder)
    }.getOrElse { throwable ->
        throw IllegalStateException("LiteRT-LM build() invoke failed.", throwable)
    } ?: throw IllegalStateException("LiteRT-LM build() returned null.")
    Log.i("ChatScreen", "LiteRT-LM options build succeeded.")
    return LocalLiteRtOptionsBuildResult(options = builtOptions, buildPath = buildPath)
}

private fun probeLiteRtStreamingApiViaReflection(): Boolean {
    val candidateMethodNames = listOf(
        "generateResponseAsync",
        "setResultListener",
        "setProgressListener",
        "addResultListener",
        "createSession",
        "createSessionFromOptions",
    )
    val targetClassNames = listOf(
        "com.google.mediapipe.tasks.genai.llminference.LlmInference",
        "com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession",
    )
    val detectedSignatures = mutableListOf<String>()

    targetClassNames.forEach { className ->
        val targetClass = runCatching { Class.forName(className) }.onFailure { throwable ->
            Log.i("ChatScreen", "LiteRT-LM streaming probe skipped class=$className", throwable)
        }.getOrNull() ?: return@forEach

        val classMethods = targetClass.methods
        candidateMethodNames.forEach { candidateMethodName ->
            val method = classMethods.firstOrNull { it.name == candidateMethodName } ?: return@forEach
            val parameterTypeNames = method.parameterTypes.joinToString(prefix = "[", postfix = "]") { it.name }
            val signature = method.toGenericString()
            Log.i(
                "ChatScreen",
                "LiteRT-LM streaming candidate class=${targetClass.name}, method=${method.name}, parameterTypes=$parameterTypeNames, signature=$signature",
            )
            detectedSignatures += signature
        }
    }

    if (detectedSignatures.isEmpty()) {
        Log.i(
            "ChatScreen",
            "LiteRT-LM streaming probe completed: no candidate methods found. candidates=$candidateMethodNames",
        )
        return false
    }

    Log.i("ChatScreen", "LiteRT-LM streaming probe completed: detected=${detectedSignatures.size}")
    return true
}

private fun tryCheckLiteRtLmGenerateViaReflection(
    inferenceInstance: Any,
): LocalLiteRtProbeResult {
    val candidateMethodNames = listOf("generateResponse", "generate", "infer")
    val candidateMethods = candidateMethodNames.flatMap { methodName ->
        inferenceInstance.javaClass.methods.filter { method ->
            method.name == methodName &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == String::class.java
        }
    }
    if (candidateMethods.isEmpty()) {
        Log.w("ChatScreen", "LiteRT-LM generate-like method not found. candidates=$candidateMethodNames")
        return LocalLiteRtProbeResult.GENERATE_METHOD_NOT_FOUND
    }

    candidateMethods.forEach { method ->
        val invoked = runCatching {
            method.invoke(inferenceInstance, LOCAL_INFERENCE_PROBE_PROMPT)
        }.onFailure { throwable ->
            Log.w("ChatScreen", "LiteRT-LM generate probe failed on ${method.name}(String)", throwable)
        }.isSuccess
        if (invoked) {
            Log.i("ChatScreen", "LiteRT-LM generate probe passed on ${method.name}(String)")
            return LocalLiteRtProbeResult.SUCCESS
        }
    }

    Log.e("ChatScreen", "LiteRT-LM generate probe failed for all candidate methods.")
    return LocalLiteRtProbeResult.GENERATE_FAILED
}

private fun generateLiteRtStringResponseOnceViaReflection(
    inferenceInstance: Any,
    prompt: String,
    trace: LocalInferenceTrace,
): LocalLiteRtGeneratedResponse {
    val candidateMethodNames = listOf("generateResponse", "generate", "infer")
    val candidateMethods = candidateMethodNames.flatMap { methodName ->
        inferenceInstance.javaClass.methods.filter { method ->
            method.name == methodName &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == String::class.java
        }
    }
    if (candidateMethods.isEmpty()) {
        Log.w("ChatScreen", "LiteRT-LM generate-like method not found for response generation.")
        val inventoryTrace = trace.merge(probeLocalStatsCandidates(inferenceInstance))
        return LocalLiteRtGeneratedResponse(trace = inventoryTrace)
    }

    candidateMethods.forEach { method ->
        val result = runCatching {
            method.invoke(inferenceInstance, prompt)
        }.onFailure { throwable ->
            Log.w("ChatScreen", "LiteRT-LM generate invocation failed on ${method.name}(String)", throwable)
        }.getOrNull()
        when (result) {
            is String -> {
                val inventoryTrace = trace.copy(generateMethodSignature = method.toGenericString())
                    .merge(probeLocalStatsCandidates(inferenceInstance))
                return LocalLiteRtGeneratedResponse(response = result, trace = inventoryTrace)
            }
            is CharSequence -> {
                val inventoryTrace = trace.copy(generateMethodSignature = method.toGenericString())
                    .merge(probeLocalStatsCandidates(inferenceInstance))
                return LocalLiteRtGeneratedResponse(response = result.toString(), trace = inventoryTrace)
            }
        }
    }
    Log.e("ChatScreen", "LiteRT-LM string response generation failed for all candidate methods.")
    val inventoryTrace = trace.merge(probeLocalStatsCandidates(inferenceInstance))
    return LocalLiteRtGeneratedResponse(trace = inventoryTrace)
}

private fun LocalInferenceTrace.merge(probe: LocalInferenceTrace): LocalInferenceTrace {
    return copy(
        modelNameProbe = if (modelNameProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.modelNameProbe else modelNameProbe,
        finishReasonProbe = if (finishReasonProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.finishReasonProbe else finishReasonProbe,
        outputTokenProbe = if (outputTokenProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.outputTokenProbe else outputTokenProbe,
        loadTimeProbe = if (loadTimeProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.loadTimeProbe else loadTimeProbe,
        promptEvalTimeProbe = if (promptEvalTimeProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.promptEvalTimeProbe else promptEvalTimeProbe,
        evalTimeProbe = if (evalTimeProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.evalTimeProbe else evalTimeProbe,
        firstTokenProbe = if (firstTokenProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.firstTokenProbe else firstTokenProbe,
        estimatedTokenProbe = if (estimatedTokenProbe.availability == LocalStatsAvailability.NOT_FOUND) probe.estimatedTokenProbe else estimatedTokenProbe,
    )
}

private fun probeLocalStatsCandidates(
    inferenceInstance: Any,
): LocalInferenceTrace {
    val metadataRoot = findAndInvokeNoArgMethodByNames(
        target = inferenceInstance,
        candidateNames = listOf("getMetadata", "metadata", "getResult", "result", "getSession", "session"),
        label = "metadataRoot",
    )?.value
    val primaryTarget = metadataRoot ?: inferenceInstance
    return LocalInferenceTrace(
        modelNameProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "modelName",
            candidateNames = listOf("getModelName", "modelName", "getModel", "model"),
        ),
        finishReasonProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "finishReason",
            candidateNames = listOf("getFinishReason", "finishReason", "getDoneReason", "doneReason"),
        ),
        outputTokenProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "outputTokens",
            candidateNames = listOf("getOutputTokenCount", "outputTokenCount", "getCompletionTokens", "completionTokens", "getEvalCount", "evalCount"),
        ),
        loadTimeProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "loadTime",
            candidateNames = listOf("getLoadDuration", "loadDuration", "getLoadTimeMs", "loadTimeMs"),
        ),
        promptEvalTimeProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "promptEvalTime",
            candidateNames = listOf("getPromptEvalDuration", "promptEvalDuration", "getPromptEvalTimeMs", "promptEvalTimeMs"),
        ),
        evalTimeProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "evalTime",
            candidateNames = listOf("getEvalDuration", "evalDuration", "getGenerationDuration", "generationDuration"),
        ),
        firstTokenProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "firstToken",
            candidateNames = listOf("getTimeToFirstTokenMs", "timeToFirstTokenMs", "getFirstToken", "firstToken"),
        ),
        estimatedTokenProbe = probeSingleCandidate(
            target = primaryTarget,
            label = "estimatedTokenCount",
            candidateNames = listOf("getEstimatedTokenCount", "estimatedTokenCount", "getTokenCount", "tokenCount"),
        ),
    )
}

private data class LocalInvokedMethodResult(
    val signature: String,
    val returnTypeName: String,
    val value: Any?,
)

private fun findAndInvokeNoArgMethodByNames(
    target: Any,
    candidateNames: List<String>,
    label: String,
): LocalInvokedMethodResult? {
    candidateNames.forEach { candidateName ->
        val method = target.javaClass.methods.firstOrNull { reflectedMethod ->
            reflectedMethod.name.equals(candidateName, ignoreCase = true) &&
                reflectedMethod.parameterTypes.isEmpty()
        } ?: return@forEach
        val signature = method.toGenericString()
        Log.i("ChatScreen", "LOCAL stats candidate method found: label=$label signature=$signature")
        val invoked = runCatching { method.invoke(target) }.onFailure { throwable ->
            Log.w("ChatScreen", "LOCAL stats candidate invoke failed: label=$label signature=$signature", throwable)
        }.getOrNull()
        return LocalInvokedMethodResult(
            signature = signature,
            returnTypeName = method.returnType.name,
            value = invoked,
        )
    }
    return null
}

private fun probeSingleCandidate(
    target: Any,
    label: String,
    candidateNames: List<String>,
): LocalStatsCandidateProbe {
    val invoked = findAndInvokeNoArgMethodByNames(target, candidateNames, label)
        ?: return LocalStatsCandidateProbe(availability = LocalStatsAvailability.NOT_FOUND)
    val availability = if (invoked.value != null) {
        LocalStatsAvailability.AVAILABLE_NOW
    } else {
        LocalStatsAvailability.API_CANDIDATE_ONLY
    }
    return LocalStatsCandidateProbe(
        availability = availability,
        signature = invoked.signature,
        returnTypeName = invoked.returnTypeName,
        valueSummary = invoked.value?.toString(),
    )
}

private fun logLocalStatsInventoryClassification(runResult: LocalInferenceRunResult?) {
    val trace = runResult?.trace ?: LocalInferenceTrace()
    val responseAvailability = if (runResult?.response != null) {
        LocalStatsAvailability.DERIVABLE_NOW
    } else {
        LocalStatsAvailability.NOT_FOUND
    }
    val generationAvailability = if (runResult != null) {
        LocalStatsAvailability.AVAILABLE_NOW
    } else {
        LocalStatsAvailability.NOT_FOUND
    }
    val timeoutAvailability = LocalStatsAvailability.AVAILABLE_NOW
    val initializationAvailability = LocalStatsAvailability.AVAILABLE_NOW
    val streamingAvailability = when (trace.streamingCandidateDetected) {
        true -> LocalStatsAvailability.API_CANDIDATE_ONLY
        false -> LocalStatsAvailability.NOT_FOUND
        null -> LocalStatsAvailability.NOT_FOUND
    }
    val generateMethodAvailability = if (trace.generateMethodSignature != null) {
        LocalStatsAvailability.API_CANDIDATE_ONLY
    } else {
        LocalStatsAvailability.NOT_FOUND
    }
    val createPathAvailability = if (trace.createMethodSignature != null) {
        LocalStatsAvailability.API_CANDIDATE_ONLY
    } else {
        LocalStatsAvailability.NOT_FOUND
    }
    val optionsBuildPathAvailability = if (trace.optionsBuildPath != null) {
        LocalStatsAvailability.API_CANDIDATE_ONLY
    } else {
        LocalStatsAvailability.NOT_FOUND
    }
    Log.i(
        "ChatScreen",
        "LOCAL stats availability: responseTimeMs=$generationAvailability, responseCharCount=$responseAvailability, modelName=${trace.modelNameProbe.availability}, finishReason=${trace.finishReasonProbe.availability}, timeoutFlag=$timeoutAvailability, initState=$initializationAvailability, streamingProbe=$streamingAvailability, generateMethod=$generateMethodAvailability, createPath=$createPathAvailability, optionsBuildPath=$optionsBuildPathAvailability, firstToken=${trace.firstTokenProbe.availability}, loadTime=${trace.loadTimeProbe.availability}, promptEvalTime=${trace.promptEvalTimeProbe.availability}, outputTokenCount=${trace.outputTokenProbe.availability}, estimatedTokenCount=${trace.estimatedTokenProbe.availability}",
    )
    Log.i(
        "ChatScreen",
        "LOCAL stats signatures: modelName=${trace.modelNameProbe.signature}, finishReason=${trace.finishReasonProbe.signature}, outputTokens=${trace.outputTokenProbe.signature}, loadTime=${trace.loadTimeProbe.signature}, promptEval=${trace.promptEvalTimeProbe.signature}, evalTime=${trace.evalTimeProbe.signature}, firstToken=${trace.firstTokenProbe.signature}, estimatedTokens=${trace.estimatedTokenProbe.signature}",
    )
}

@Composable
private fun AssistantStreamingIndicator() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("assistantStreamingIndicator")
    ) {
        Text(
            text = "生成中…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InferenceStatRow(
    label: String,
    value: String,
    emphasizeValue: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasizeValue) FontWeight.SemiBold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun InferenceStatsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

private fun sanitizeLocalAssistantResponse(raw: String): String {
    return raw
        .replace("<end_of_turn>", "")
        .replace("<eot>", "")
        .replace("<|eot_id|>", "")
        .replace("<|end_of_text|>", "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

internal fun createAssistantMessage(
    chatId: Int,
    response: String,
    latestInferenceStats: InferenceStats? = null,
    imageInputCount: Int? = null,
    generationTimeMs: Long? = null,
): Message {
    val outputTokens = latestInferenceStats?.outputTokens ?: latestInferenceStats?.completionTokens
    val inputTokens = latestInferenceStats?.inputTokens
    val persistedTotalTokens = latestInferenceStats?.totalTokens
        ?: if (inputTokens != null && outputTokens != null) inputTokens + outputTokens else null
    return Message(
        message = response,
        chatId = chatId,
        isSendbyMe = false,
        completionTokens = outputTokens,
        generationTimeMs = generationTimeMs
            ?: latestInferenceStats?.generationTimeMs
            ?: latestInferenceStats?.inferenceTimeSec?.times(1000.0)?.toLong(),
        evalDurationNs = latestInferenceStats?.evalDurationNs,
        loadDurationNs = latestInferenceStats?.modelLoadDurationNs,
        promptEvalDurationNs = latestInferenceStats?.promptEvalDurationNs,
        modelName = latestInferenceStats?.modelName ?: latestInferenceStats?.model,
        inputTokens = inputTokens,
        totalTokens = persistedTotalTokens,
        tokensPerSecond = latestInferenceStats?.tokensPerSecond,
        inferenceTimeSec = latestInferenceStats?.inferenceTimeSec,
        finishReason = latestInferenceStats?.finishReason,
        timeToFirstTokenMs = latestInferenceStats?.timeToFirstTokenMs,
        // 画像入力数は添付画像の枚数。入力トークンとは別メトリクスとして保存する。
        imageInputCount = imageInputCount ?: latestInferenceStats?.imageInputCount,
    )
}

@Composable
private fun InferenceStatsSheetContent(
    stats: InferenceStats,
    localTraceForDev: LocalInferenceTrace? = null,
) {
    var isDetailExpanded by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    val sheetContentPadding = 14.dp
    val sectionSpacing = 12.dp

    val sections = buildInferenceSummarySections(stats, localTraceForDev = localTraceForDev)
    val detailSections = buildInferenceDetailSections(stats)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            // BottomSheet 内の視認性を上げるため、周囲の余白を揃える。
            .padding(sheetContentPadding),
            // 下部コンテンツが IME / ナビゲーションバーに埋もれないようにする。
            // シート内でのみ insets を吸収し、既存レイアウトへの影響を最小化する。
        verticalArrangement = Arrangement.spacedBy(sectionSpacing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            Text(
                text = "推論統計",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            InferenceModelInfoRow(
                stats = stats,
                onCopyModelName = { modelName ->
                    clipboardManager.setText(AnnotatedString(modelName))
                },
            )

            sections.forEach { section ->
                InferenceStatsSection(title = section.title) {
                    section.items.forEach { item ->
                        InferenceStatRow(label = item.label, value = item.value, emphasizeValue = item.emphasizeValue)
                    }
                }
            }

            InferenceTimingBreakdownSection(stats)
            InferenceContextUsageSection(stats)

            if (shouldShowInferenceTimingNote(stats)) {
                Text(
                    text = inferenceTimingNoteText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            InferenceStatsCollapsibleSectionHeader(
                expanded = isDetailExpanded,
                onToggle = { isDetailExpanded = !isDetailExpanded },
            )

            AnimatedVisibility(visible = isDetailExpanded) {
                Column(
                    modifier = Modifier.testTag("inferenceStatsDetailContent"),
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                ) {
                    detailSections.forEach { section ->
                        InferenceStatsSection(title = section.title) {
                            section.items.forEach { item ->
                                InferenceStatRow(
                                    label = item.label,
                                    value = item.value,
                                    emphasizeValue = item.emphasizeValue,
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
private fun InferenceModelInfoRow(
    stats: InferenceStats,
    onCopyModelName: (String) -> Unit,
) {
    val modelName = formatModelName(stats)
    InferenceStatsSection(title = "モデル情報") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "使用モデル",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = modelName ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (!modelName.isNullOrBlank()) {
                IconButton(
                    onClick = { onCopyModelName(modelName) },
                    modifier = Modifier.semantics { contentDescription = "モデル名をコピー" },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "モデル名をコピー",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InferenceTimingBreakdownSection(stats: InferenceStats) {
    val breakdown = buildInferenceTimeBreakdown(stats) ?: return
    val barColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    InferenceStatsSection(title = "推論時間内訳") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            breakdown.segments.forEach { segment ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InferenceStatRow(
                        label = segment.label,
                        value = "${segment.durationText} / ${segment.percent}%",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(
                                color = trackColor,
                                shape = RoundedCornerShape(999.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(segment.ratio.toFloat().coerceIn(0f, 1f))
                                .height(8.dp)
                                .background(
                                    color = barColor,
                                    shape = RoundedCornerShape(999.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InferenceContextUsageSection(stats: InferenceStats) {
    val usage = buildContextUsageUi(stats) ?: return
    InferenceStatsSection(title = "コンテキスト使用量") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (usage) {
                is ContextUsageUi.WithMax -> {
                    if (usage.ratio in 0.0..1.0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(999.dp),
                                ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(usage.ratio.toFloat().coerceIn(0f, 1f))
                                    .height(8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(999.dp),
                                    ),
                            )
                        }
                    }
                    Text(
                        text = "${usage.used} / ${usage.max} tokens (${usage.percent}%)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                is ContextUsageUi.Loading -> {
                    Text(
                        text = "使用トークン ${usage.used}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "上限取得中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is ContextUsageUi.WithoutMax -> {
                    Text(
                        text = "使用トークン ${usage.used}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "上限未取得",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InferenceStatsCollapsibleSectionHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val actionLabel = inferenceStatsDetailToggleActionLabel(expanded)
    val accessibilityLabel = inferenceStatsDetailToggleAccessibilityLabel(expanded)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .semantics { contentDescription = accessibilityLabel }
            .testTag("inferenceStatsDetailToggle")
            // 見出し行全体のタップしやすさを維持するため、最小限の縦余白を確保する。
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "詳細",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = accessibilityLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun inferenceStatsDetailToggleActionLabel(expanded: Boolean): String = if (expanded) "閉じる" else "表示"

internal fun inferenceStatsDetailToggleAccessibilityLabel(expanded: Boolean): String = if (expanded) "詳細を閉じる" else "詳細を表示"

internal fun inferenceTimingNoteText(): String =
    "初回受信までは端末側の受信タイミング、全体完了までは推論統計の完了タイミングを示します。"

internal fun shouldShowInferenceTimingNote(stats: InferenceStats): Boolean =
    formatTimeToFirstToken(stats) != null || formatInferenceTime(stats) != null


internal fun buildInferenceSummarySections(
    stats: InferenceStats,
    localTraceForDev: LocalInferenceTrace? = null,
): List<InferenceStatsSectionUi> {
    val summarySection = InferenceStatsSectionUi(
        title = "概要",
        items = if (isLocalMinimalInferenceStats(stats)) {
            listOf(
                InferenceStatItemUi(label = "応答時間", value = formatInferenceTime(stats) ?: "—"),
                InferenceStatItemUi(label = "応答文字数", value = stats.responseCharCount?.toString() ?: "—"),
            )
        } else {
            listOf(
                InferenceStatItemUi(label = "初回受信まで（端末基準）", value = formatTimeToFirstToken(stats) ?: "—"),
                InferenceStatItemUi(label = "全体完了まで（統計基準）", value = formatInferenceTime(stats) ?: "—"),
                InferenceStatItemUi(
                    label = "生成速度",
                    value = formatTokenPerSec(stats)?.removePrefix("⚡")?.trim() ?: "—",
                    emphasizeValue = true,
                ),
                InferenceStatItemUi(label = "完了理由", value = formatFinishReason(stats) ?: "—"),
            )
        },
    )
    val localInventorySection = buildLocalInventorySectionForDev(
        isLocalMinimal = isLocalMinimalInferenceStats(stats),
        trace = localTraceForDev,
    )
    return listOfNotNull(summarySection, localInventorySection)
}

private fun isLocalMinimalInferenceStats(stats: InferenceStats): Boolean {
    return stats.generationTimeMs != null &&
        stats.evalDurationNs == null &&
        stats.outputTokens == null &&
        stats.completionTokens == null &&
        stats.finishReason == null
}

private fun buildLocalInventorySectionForDev(
    isLocalMinimal: Boolean,
    trace: LocalInferenceTrace?,
): InferenceStatsSectionUi? {
    if (!isLocalMinimal || trace == null) return null
    return InferenceStatsSectionUi(
        title = "LOCAL棚卸し（開発用）",
        items = listOf(
            InferenceStatItemUi(label = "modelName", value = trace.modelNameProbe.availability.name),
            InferenceStatItemUi(label = "finishReason", value = trace.finishReasonProbe.availability.name),
            InferenceStatItemUi(label = "outputTokens", value = trace.outputTokenProbe.availability.name),
            InferenceStatItemUi(label = "loadTime", value = trace.loadTimeProbe.availability.name),
            InferenceStatItemUi(label = "promptEvalTime", value = trace.promptEvalTimeProbe.availability.name),
            InferenceStatItemUi(label = "firstToken", value = trace.firstTokenProbe.availability.name),
            InferenceStatItemUi(
                label = "streamingCandidate",
                value = trace.streamingCandidateDetected?.toString() ?: "—",
            ),
            InferenceStatItemUi(label = "generateMethod", value = trace.generateMethodSignature ?: "—"),
            InferenceStatItemUi(label = "createPath", value = trace.createMethodSignature ?: "—"),
            InferenceStatItemUi(label = "optionsBuildPath", value = trace.optionsBuildPath ?: "—"),
        ),
    )
}

internal data class InferenceTimeSegmentUi(
    val label: String,
    val ratio: Double,
    val percent: Int,
    val durationText: String,
)

internal data class InferenceTimeBreakdownUi(
    val segments: List<InferenceTimeSegmentUi>,
)

internal fun buildInferenceTimeBreakdown(stats: InferenceStats): InferenceTimeBreakdownUi? {
    val load = stats.modelLoadDurationNs?.takeIf { it >= 0L } ?: 0L
    val prompt = stats.promptEvalDurationNs?.takeIf { it >= 0L } ?: 0L
    val generation = (stats.generationDurationNs ?: stats.evalDurationNs)?.takeIf { it >= 0L } ?: 0L
    val total = load + prompt + generation
    if (total <= 0L) return null

    fun ratio(value: Long): Double = value.toDouble() / total.toDouble()
    return InferenceTimeBreakdownUi(
        segments = listOf(
            InferenceTimeSegmentUi("ロード", ratio(load), (ratio(load) * 100).roundToInt(), formatDurationNsAsSecondsForSheet(load)),
            InferenceTimeSegmentUi("入力", ratio(prompt), (ratio(prompt) * 100).roundToInt(), formatDurationNsAsSecondsForSheet(prompt)),
            InferenceTimeSegmentUi("生成", ratio(generation), (ratio(generation) * 100).roundToInt(), formatDurationNsAsSecondsForSheet(generation)),
        ),
    )
}

private fun formatDurationNsAsSecondsForSheet(durationNs: Long): String {
    val seconds = durationNs / 1_000_000_000.0
    if (seconds > 0.0 && seconds < 0.1) return "<0.1 s"
    return String.format(Locale.US, "%.1f s", seconds)
}

internal sealed interface ContextUsageUi {
    data class WithMax(
        val used: Int,
        val max: Int,
        val ratio: Double,
        val percent: Int,
    ) : ContextUsageUi

    data class Loading(val used: Int) : ContextUsageUi

    data class WithoutMax(val used: Int) : ContextUsageUi
}

internal fun buildContextUsageUi(stats: InferenceStats): ContextUsageUi? {
    val used = stats.totalTokens?.takeIf { it >= 0 } ?: return null
    val max = stats.contextWindow?.takeIf { it > 0 }
    if (max != null) {
        val ratio = used.toDouble() / max.toDouble()
        return ContextUsageUi.WithMax(
            used = used,
            max = max,
            ratio = ratio,
            percent = (ratio * 100).roundToInt(),
        )
    }
    return when (stats.contextWindowFetchState) {
        ContextWindowFetchState.LOADING -> ContextUsageUi.Loading(used = used)
        ContextWindowFetchState.AVAILABLE,
        ContextWindowFetchState.UNAVAILABLE,
        -> ContextUsageUi.WithoutMax(used = used)
    }
}

internal fun buildInferenceDetailSections(stats: InferenceStats): List<InferenceStatsSectionUi> = listOf(
    InferenceStatsSectionUi(
        title = "トークン",
        items = listOf(
            InferenceStatItemUi(label = "入力トークン", value = stats.inputTokens?.toString() ?: "—"),
            InferenceStatItemUi(label = "生成トークン", value = formatOutputTokens(stats) ?: "—"),
            InferenceStatItemUi(label = "合計トークン", value = formatTotalTokens(stats) ?: "—"),
        ),
    ),
    InferenceStatsSectionUi(
        title = "バックエンド時間詳細",
        items = listOf(
            InferenceStatItemUi(label = "モデルロード時間", value = formatModelLoadDuration(stats) ?: "—"),
            InferenceStatItemUi(label = "入力評価時間", value = formatPromptEvalDuration(stats) ?: "—"),
            InferenceStatItemUi(label = "生成時間", value = formatGenerationDuration(stats) ?: "—"),
        ),
    ),
    InferenceStatsSectionUi(
        title = "補足",
        items = listOf(
            InferenceStatItemUi(label = "画像入力", value = formatImageInputCount(stats) ?: "—"),
        ),
    ),
)

internal data class InferenceStatsSectionUi(
    val title: String,
    val items: List<InferenceStatItemUi>,
)

internal data class InferenceStatItemUi(
    val label: String,
    val value: String,
    val emphasizeValue: Boolean = false,
)

@Composable
private fun DrawerSearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val height = 40.dp
    val shape = RoundedCornerShape(height / 2)
    val drawerSearchTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Default,
    )
    Box(
        modifier = modifier
            .height(height)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .padding(start = 16.dp, top = 1.dp, end = 0.dp, bottom = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = drawerSearchTextStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = "タイトル検索",
                                style = drawerSearchTextStyle.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "検索をクリア",
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewRow(
    uris: List<Uri>,
    onOpen: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    inComposer: Boolean = false,
) {
    val attachmentPreviewSize = 72.dp
    val edgeFadeWidth = 12.dp
    val epsilonPx = 2
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val showLeftFade by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val showRightFade by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: -1
            val lastVisibleItem = visibleItems.lastOrNull()

            totalItemsCount > 0 && (
                lastVisibleIndex < totalItemsCount - 1 ||
                    (
                        lastVisibleItem != null &&
                            (lastVisibleItem.offset + lastVisibleItem.size) >
                            (layoutInfo.viewportEndOffset + epsilonPx)
                        )
                )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 入力欄との視認分離に必要な最小限の余白
            .padding(
                horizontal = if (inComposer) 12.dp else 17.dp,
                // 入力欄内表示時の上側余白を +2dp 調整して縁との距離を確保
                vertical = if (inComposer) 3.5.dp else 8.dp,
            ),
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(uris) { index, uri ->
                Box(
                    modifier = Modifier
                        .size(attachmentPreviewSize),
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp),
                            ),
                    ) {
                        AndroidView(
                            factory = { context ->
                                ImageView(context).apply {
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                }
                            },
                            update = { imageView ->
                                imageView.setImageURI(uri)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onOpen(index) },
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 1.dp, end = 1.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onRemoveAt(index) }
                            .testTag("attachment_remove_$index"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove attachment",
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }

        if (showLeftFade) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .height(attachmentPreviewSize)
                    .width(edgeFadeWidth)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                surfaceColor.copy(alpha = 1f),
                                surfaceColor.copy(alpha = 0f),
                            ),
                        ),
                    ),
            )
        }

        if (showRightFade) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(attachmentPreviewSize)
                    .width(edgeFadeWidth)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                surfaceColor.copy(alpha = 0f),
                                surfaceColor.copy(alpha = 1f),
                            ),
                        ),
                    ),
            )
        }
    }
}

internal fun filterChatsByTitle(chats: List<Chat>, query: String): List<Chat> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) {
        return chats
    }
    return chats.filter { chat ->
        chat.title.contains(normalizedQuery, ignoreCase = true)
    }
}


internal fun formatChatPreview(message: String?): String {
    if (message.isNullOrBlank()) {
        return ""
    }
    val oneLineMessage = message
        .replace("\n", " ")
        .replace("\r", " ")
        .trim()
    if (oneLineMessage.isEmpty()) {
        return ""
    }
    val maxLength = 80
    return if (oneLineMessage.length > maxLength) {
        oneLineMessage.take(maxLength) + "…"
    } else {
        oneLineMessage
    }
}


internal fun resolveDefaultChatId(explicitChatId: Int?, chats: List<Chat>): Int? {
    return explicitChatId
}

internal suspend fun createAndNavigateToNewChat(
    createNewChat: suspend () -> Int,
    onChatResolved: (Int) -> Unit,
    navigateToChat: (Int) -> Unit,
    closeDrawer: suspend () -> Unit,
) {
    val newChatId = createNewChat()
    onChatResolved(newChatId)
    closeThenNavigate(
        closeDrawer = closeDrawer,
        navigate = {
            navigateToChat(newChatId)
        }
    )
}

internal suspend fun closeThenNavigate(
    closeDrawer: suspend () -> Unit,
    navigate: () -> Unit,
) {
    // close の失敗は吸収し、navigate は必ず実行する
    runCatching { closeDrawer() }
    navigate()
}

private suspend fun closeDrawerSafely(drawerState: DrawerState) {
    if (!drawerState.isOpen) {
        return
    }
    runCatching { drawerState.close() }
}

private suspend fun closeDrawerForNavigation(drawerState: DrawerState) {
    if (!drawerState.isOpen) return
    if (RuntimeFlags.isUiTestRuntime()) {
        runCatching { drawerState.snapTo(DrawerValue.Closed) }
    } else {
        runCatching { drawerState.close() }
    }
}

internal fun shouldAutoCreateNewChat(
    suppressAutoNewChat: Boolean,
    resolvedChatId: Int?,
    isCreatingChat: Boolean,
): Boolean {
    return !suppressAutoNewChat && resolvedChatId == null && !isCreatingChat
}

private fun computeLatestUserAnchor(messages: List<Message>): Int {
    if (messages.isEmpty()) {
        return 0
    }
    val lastUser = messages.indexOfLast { it.isSendbyMe }
    return if (lastUser >= 0) {
        lastUser
    } else {
        messages.lastIndex
    }
}


private fun List<String>.toAttachmentUriStringsJson(): String =
    JSONArray().apply { forEach { uri -> put(uri) } }.toString()
