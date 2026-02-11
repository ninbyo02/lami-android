package com.sonusid.ollama.ui.screens.home

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.sonusid.ollama.R
import com.sonusid.ollama.UiState
import com.sonusid.ollama.db.entity.Chat
import com.sonusid.ollama.db.entity.Message
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.ui.common.LocalAppSnackbarHostState
import com.sonusid.ollama.ui.components.HeaderAvatar
import com.sonusid.ollama.ui.components.LamiHeaderStatus
import com.sonusid.ollama.ui.components.LamiSprite
import com.sonusid.ollama.ui.components.rememberLamiCharacterBackdropColor
import com.sonusid.ollama.viewmodels.OllamaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val interactionSource = remember { MutableInteractionSource() }
    var userPrompt: String by remember { mutableStateOf("") }
    var prompt: String by remember { mutableStateOf("") }
    val allChatsState = effectiveChatId?.let { viewModel.allMessages(it).collectAsState(initial = emptyList()) }
    val allChats = allChatsState?.value.orEmpty()
    var toggle by remember { mutableStateOf(false) }
    var placeholder by remember { mutableStateOf("Enter your prompt ...") }
    val selectedModel by viewModel.selectedModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val lamiAnimationStatus by viewModel.lamiAnimationStatus.collectAsState()
    val animationEpochMs by viewModel.animationEpochMs.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = LocalAppSnackbarHostState.current
    val coroutineScope = rememberCoroutineScope()
    val errorMessage = (uiState as? UiState.Error)?.errorMessage
    val lamiUiState by viewModel.lamiUiState.collectAsState()

    LaunchedEffect(chatId, chats) {
        val resolvedChatId = chatId ?: chats.lastOrNull()?.chatId
        effectiveChatId = resolvedChatId

        if (resolvedChatId == null && !isCreatingChat) {
            isCreatingChat = true
            viewModel.insertChat(Chat(title = "New Chat"))
        }

        if (resolvedChatId != null) {
            isCreatingChat = false
        }
    }

    LaunchedEffect(allChats.size) {
        if (allChats.isNotEmpty()) {
            listState.animateScrollToItem(allChats.size - 1)
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

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            viewModel.onUserInteraction()
        }
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

    Scaffold(
        // 上部空白を 0dp に固定するため、Scaffold の Insets を無効化
        contentWindowInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
        topBar = {
        TopAppBar(
            // 上部空白を追加しないため、TopAppBar 側の Insets は明示的に 0 に固定
            windowInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
            navigationIcon = {
                Box(
                    // 左端でアバターが見切れないよう最小限の start 余白を確保
                    modifier = Modifier.padding(start = 8.dp)
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
                }
            },
            title = {
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
                    showAvatar = false,
                )
            },
            actions = {
                IconButton(onClick = {
                    viewModel.onUserInteraction()
                    navHostController.navigate(Routes.CHATS)
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
    }, bottomBar = {
        OutlinedTextField(
            interactionSource = interactionSource,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "音声入力",
                    modifier = Modifier.size(22.dp)
                )
            },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ask llama")
                }
            },
            value = userPrompt,
            onValueChange = {
                userPrompt = it
                viewModel.onUserInteraction()
            },
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 5.dp)
                .imePadding(),
            singleLine = true,
            suffix = {
                ElevatedButton(
                    contentPadding = PaddingValues(0.dp),
                    enabled = !selectedModel.isNullOrBlank(),
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
                            return@ElevatedButton
                        }

                        val currentChatId = effectiveChatId
                        if (currentChatId != null) {
                            if (userPrompt.isNotEmpty()) {
                                placeholder = "I'm thinking ... "
                                viewModel.insert(
                                    Message(chatId = currentChatId, message = userPrompt, isSendbyMe = true)
                                )
                                toggle = true
                                prompt = userPrompt
                                userPrompt = ""
                                viewModel.sendPrompt(prompt, selectedModel)
                                prompt = ""
                            }
                        } else {
                            placeholder = "Setting up a new chat ..."
                        }
                    }) {
                    Icon(
                        painterResource(R.drawable.send), contentDescription = "Send Button"
                    )
                }
            },
            placeholder = { Text(placeholder, fontSize = 15.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.primaryContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // TopAppBar 配下への潜り込みを防ぐため、Scaffold の上下 inset をここで一元適用
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding()
                )
                // LazyColumn 側で Insets を二重適用しないよう、この階層で消費する
                .consumeWindowInsets(paddingValues)
        ) {
            val contentModifier = Modifier
                .fillMaxSize()

            if (effectiveChatId == null) {
                Column(
                    modifier = contentModifier,
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(if (isCreatingChat) "Creating new chat..." else "Preparing chat...")
                }
            } else if (allChats.isEmpty()) {
                Column(
                    modifier = contentModifier,
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BoxWithConstraints {
                        val baseSpriteSize = 100.dp
                        val targetSize = baseSpriteSize * 2f
                        val maxSizeByWidth = maxWidth * 0.92f
                        val maxSizeByHeight = maxHeight * 0.45f
                        val finalSize = minOf(targetSize, maxSizeByWidth, maxSizeByHeight)
                        LamiSprite(
                            state = lamiUiState.state,
                            lamiStatus = lamiAnimationStatus,
                            sizeDp = finalSize,
                            shape = CircleShape,
                            backgroundColor = rememberLamiCharacterBackdropColor(),
                            contentPadding = 0.dp,
                            animationsEnabled = true,
                            replacementEnabled = true,
                            blinkEffectEnabled = true,
                            contentOffsetYDp = 2.dp,
                            tightContainer = true,
                            maxStatusSpriteSizeDp = finalSize,
                            debugOverlayEnabled = false,
                            syncEpochMs = animationEpochMs,
                        )
                    }
                    Text(
                        text = "最初のメッセージを送信して会話を始めましょう",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = contentModifier,
                    // 上は TopAppBar 直下に詰め、下は最小余白のみを維持
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 0.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    state = listState,
                ) {
                    itemsIndexed(
                        items = allChats,
                        key = { _, message -> message.messageID.takeIf { it != 0 } ?: "${message.chatId}-${message.message}" }
                    ) { index, message ->
                        val topPadding = if (index == 0) 0.dp else 8.dp
                        Box(modifier = Modifier.padding(top = topPadding)) {
                            ChatBubble(message.message, message.isSendbyMe)
                        }
                    }
                }
            }

            if (uiState is UiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                )
            }
            if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // エラーバナーの上端だけは詰めて、他方向の余白を維持
                        .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp),
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
    }
}
