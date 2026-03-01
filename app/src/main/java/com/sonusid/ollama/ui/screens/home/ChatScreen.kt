package com.sonusid.ollama.ui.screens.home

import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.sonusid.ollama.R
import com.sonusid.ollama.UiState
import com.sonusid.ollama.db.entity.Chat
import com.sonusid.ollama.db.entity.Message
import com.sonusid.ollama.db.entity.TitleSource
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.ui.common.LocalAppSnackbarHostState
import com.sonusid.ollama.ui.components.HeaderAvatar
import com.sonusid.ollama.ui.components.LamiHeaderStatus
import com.sonusid.ollama.util.RuntimeFlags
import com.sonusid.ollama.viewmodels.OllamaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlinx.coroutines.yield
import kotlin.math.min
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
// メッセージ間の縦余白は初回ペアも含めて常に同値で統一する
private val ChatMessageVerticalGap = 8.dp
private const val MaxComposerAttachments = 10

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
    val baseUrl by viewModel.baseUrl.collectAsState()
    val snackbarHostState = LocalAppSnackbarHostState.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedImageUriStrings by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    // composer fullscreen viewer は回転（構成変更）で閉じないよう Saveable で保持する。
    // Uri は Saveable ではないため String で保持し、表示時に Uri.parse で復元する。
    var composerViewerUriStrings by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var composerViewerInitialIndex by rememberSaveable { mutableStateOf(0) }
    val selectedImageUris = selectedImageUriStrings.map(Uri::parse)
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
    val lamiUiState by viewModel.lamiUiState.collectAsState()
    val debugOverlayEnabled = false
    val topGradientBottomDp = TopGradientOverlayTopOffset + TopGradientOverlayYOffset + TopGradientOverlayHeight
    val chatListTopPaddingDp = topGradientBottomDp + ChatListTopGapFromGradientBottom
    var measuredTopGradientBottomPx by remember { mutableStateOf<Float?>(null) }
    val measuredTopGradientBottomDp = with(LocalDensity.current) { (measuredTopGradientBottomPx ?: 0f).toDp() }
    val effectiveTopGradientBottomDp = if (measuredTopGradientBottomPx != null) measuredTopGradientBottomDp else topGradientBottomDp
    val topPaddingModeMap = remember {
        mutableStateMapOf<Int, TopPaddingMode>()
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
                            Message(message = response, chatId = currentChatId, isSendbyMe = false)
                        )
                    }
                    placeholder = "Enter your prompt..."
                    viewModel.resetUiState()
                }

                is UiState.Error -> {
                    if (currentChatId != null) {
                        viewModel.insert(
                            Message(
                                message = (uiState as UiState.Error).errorMessage,
                                chatId = currentChatId,
                                isSendbyMe = false
                            )
                        )
                    }
                    placeholder = "Enter your prompt..."
                    viewModel.resetUiState()
                }

                else -> {
                }
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
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(text = "履歴", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = chatSearchQuery,
                        onValueChange = { chatSearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        singleLine = true,
                        label = { Text("タイトル検索") },
                        trailingIcon = {
                            if (chatSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { chatSearchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "検索をクリア"
                                    )
                                }
                            }
                        }
                    )
                    ElevatedButton(
                        onClick = createNewChatAndNavigate,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text("New chat")
                    }
                }
                if (filteredChats.isEmpty()) {
                    Text(
                        text = "該当なし",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 12.dp)
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
                    HeaderAvatar(
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
                        debugOverlayEnabled = false,
                        syncEpochMs = animationEpochMs,
                    )
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
                        debugOverlayEnabled = false,
                        syncEpochMs = animationEpochMs,
                        // title 内で HeaderAvatar を表示しているため二重表示を防ぐ
                        showAvatar = false,
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
                            color = MaterialTheme.colorScheme.primaryContainer
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
                                                        fontSize = 15.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        enabled = !selectedModel.isNullOrBlank() && (userPrompt.isNotEmpty() || selectedImageUriStrings.isNotEmpty()),
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

                                            val currentChatId = effectiveChatId
                                            if (currentChatId != null) {
                                                val requestPrompt = userPrompt
                                                val requestAttachmentUris = selectedImageUris
                                                if (requestPrompt.isNotEmpty() || requestAttachmentUris.isNotEmpty()) {
                                                    placeholder = "I'm thinking ... "
                                                    toggle = true
                                                }
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
                val messagesForList: List<Message> = allChatsOrNull
                val isListForCurrentChatForUi =
                    currentChatId != null &&
                        (messagesForList.isEmpty() || messagesForList.all { it.chatId == currentChatId })

                if (!isListForCurrentChatForUi) {
                    Box(modifier = contentModifier)
                } else {
                    key(effectiveChatId) {
                        val anchor = computeLatestUserAnchor(messagesForList)
                        // 仕上げチェック: 初回のみ anchor を使い、それ以降は Saveable な復元位置を優先する
                        val listState = rememberSaveable(effectiveChatId, saver = LazyListState.Saver) {
                            LazyListState(firstVisibleItemIndex = anchor)
                        }
                        val isNearBottom by remember(listState) {
                            derivedStateOf {
                                val layoutInfo = listState.layoutInfo
                                val totalItems = layoutInfo.totalItemsCount
                                if (totalItems == 0) {
                                    true
                                } else {
                                    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                                    lastVisibleIndex >= totalItems - 2
                                }
                            }
                        }
                        var isNearBottomSnapshot by remember(effectiveChatId) { mutableStateOf(true) }
                        var previousMessageCount by remember(effectiveChatId) { mutableStateOf(-1) }
                        var lastAppliedAnchor by remember(effectiveChatId) { mutableStateOf(anchor) }
                        var suppressFollowOnce by remember(effectiveChatId) { mutableStateOf(false) }

                        LaunchedEffect(effectiveChatId) {
                            previousMessageCount = messagesForList.size
                            lastAppliedAnchor = computeLatestUserAnchor(messagesForList)
                            suppressFollowOnce = true
                        }

                        LaunchedEffect(listState) {
                            snapshotFlow { isNearBottom }
                                .collect { nearBottom ->
                                    isNearBottomSnapshot = nearBottom
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
                                    if (appended && isNearBottomSnapshot && !suppressFollowOnce && lastIndex >= 0) {
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
                        val messageListTopPaddingDp = if (messagesForList.isEmpty()) {
                            effectiveTopGradientBottomDp
                        } else {
                            when (mode) {
                                TopPaddingMode.NewConversation -> effectiveTopGradientBottomDp
                                TopPaddingMode.ExistingConversation -> chatListTopPaddingDp
                            }
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
                                    ) { _, message ->
                                        if (message.isSendbyMe) {
                                            ChatBubble(
                                                message = message.message,
                                                isSentByMe = message.isSendbyMe,
                                                attachmentUriString = message.attachmentUriString,
                                                attachmentUriStringsJson = message.attachmentUriStringsJson,
                                            )
                                        } else {
                                            PlainAssistantMessage(message.message)
                                        }
                                    }
                                }
                                item(key = "composer_spacer") {
                                    // IME 表示中でも末尾メッセージへ到達できるよう、既存の IME 分だけ末尾余白へ加算する
                                    Spacer(modifier = Modifier.height(ComposerMinHeight + ComposerBottomGapHeight + bottomDp))
                                }
                            }

                            if (!isNearBottom && messagesForList.isNotEmpty()) {
                                SmallFloatingActionButton(
                                    onClick = {
                                        val lastIndex = messagesForList.lastIndex
                                        if (lastIndex >= 0) {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(lastIndex)
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
                // 上部グラデーションの開始位置をステータスバーぶん下げる
                .statusBarsPadding()
                // 上部グラデーション全体を既存位置へ配置
                .padding(top = TopGradientOverlayTopOffset)
                // 上部グラデーションの開始位置を 4dp 上へ戻す
                .offset(y = TopGradientOverlayYOffset)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // IME の表示有無に関係なく上部グラデの高さを固定する
                    .height(TopGradientOverlayHeight)
                    .onGloballyPositioned { coordinates ->
                        measuredTopGradientBottomPx = coordinates.positionInParent().y + coordinates.size.height
                    }
                    .clipToBounds()
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to topColor.copy(alpha = 1.0f),
                                0.5f to topColor.copy(alpha = 0.6f),
                                1.0f to topColor.copy(alpha = 0.0f)
                            )
                        )
                    )
            )
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
