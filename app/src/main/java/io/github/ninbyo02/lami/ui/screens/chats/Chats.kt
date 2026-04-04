package io.github.ninbyo02.lami.ui.screens.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import io.github.ninbyo02.lami.R
import io.github.ninbyo02.lami.UiState
import io.github.ninbyo02.lami.db.entity.Chat
import io.github.ninbyo02.lami.db.entity.TitleSource
import io.github.ninbyo02.lami.navigation.Routes
import io.github.ninbyo02.lami.ui.components.HeaderAvatar
import io.github.ninbyo02.lami.ui.components.LamiHeaderStatus
import io.github.ninbyo02.lami.ui.components.LamiSprite
import io.github.ninbyo02.lami.ui.components.rememberLamiCharacterBackdropColor
import io.github.ninbyo02.lami.viewmodels.OllamaViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Chats(navController: NavController, viewModel: OllamaViewModel) {
    val allChats = viewModel.chats.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val lamiStatusState = viewModel.lamiAnimationStatus.collectAsState()
    val lamiUiState by viewModel.lamiUiState.collectAsState()
    val lamiState by viewModel.lamiState.collectAsState()
    val animationEpochMs by viewModel.animationEpochMs.collectAsState()

    val lastError = (uiState as? UiState.Error)?.errorMessage
    var showDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var chatTitle by remember { mutableStateOf("") }
    var openLamiControlRequestKey by remember { mutableStateOf(0) }
    println(allChats.value)

    val chatsContentWindowInsets = WindowInsets.systemBars.only(
        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
    )

    Scaffold(
        // Chats は TopAppBar で上端、Scaffold で左右・下端の安全領域を受け持つ
        contentWindowInsets = chatsContentWindowInsets,
        topBar = {
            Box(
                modifier = Modifier
                    // 上端の安全領域は TopAppBar コンテナでのみ処理する
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .zIndex(1f)
            ) {
                TopAppBar(
                    // 上端の安全領域は親 Box で処理するため、TopAppBar の Insets は無効化する
                    windowInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            // アバター下端がTopAppBarに接して見えないよう下余白を統一
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            HeaderAvatar(
                                baseUrl = baseUrl,
                                selectedModel = selectedModel,
                                lastError = lastError,
                                lamiStatus = lamiStatusState.value,
                                lamiState = lamiUiState.state,
                                availableModels = availableModels,
                                onSelectModel = { modelName ->
                                    viewModel.onUserInteraction()
                                    viewModel.updateSelectedModel(modelName)
                                },
                                onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                                debugOverlayEnabled = false,
                                syncEpochMs = animationEpochMs,
                                openControlRequestKey = openLamiControlRequestKey,
                            )
                            // ヘッダー内の最小間隔だけ確保して左余白を増やさない
                            Spacer(modifier = Modifier.size(2.dp))
                            LamiHeaderStatus(
                                baseUrl = baseUrl,
                                selectedModel = selectedModel,
                                lastError = lastError,
                                lamiStatus = lamiStatusState.value,
                                lamiState = lamiUiState.state,
                                availableModels = availableModels,
                                onSelectModel = { modelName ->
                                    viewModel.onUserInteraction()
                                    viewModel.updateSelectedModel(modelName)
                                },
                                onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                                debugOverlayEnabled = false,
                                syncEpochMs = animationEpochMs,
                                showAvatar = false,
                                onOpenControl = {
                                    viewModel.onUserInteraction()
                                    openLamiControlRequestKey += 1
                                },
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                            Icon(
                                painter = painterResource(R.drawable.settings),
                                contentDescription = "settings",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier
                    .padding(20.dp)
                    .size(60.dp),
                onClick = { showDialog = true }) {
                Icon(
                    painterResource(R.drawable.add),
                    "add",
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // TopAppBar 下の描画領域に合わせて、左右・下端の安全領域をここで一元適用する
                .padding(innerPadding)
                // Scaffold 由来の Insets はこの階層で消費し、子で二重適用しない
                .consumeWindowInsets(innerPadding)
        ) {
            if (allChats.value.isEmpty()) {
                Column(
                    modifier = Modifier
                        // 先頭コンテンツがヘッダーに詰まり過ぎないよう上余白を確保
                        .padding(top = 24.dp)
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LamiSprite(
                        state = lamiState,
                        lamiStatus = lamiStatusState.value,
                        sizeDp = 96.dp,
                        contentPadding = 0.dp,
                        tightContainer = true,
                        backgroundColor = rememberLamiCharacterBackdropColor(),
                        debugOverlayEnabled = false,
                        animationsEnabled = true,
                        replacementEnabled = true,
                        blinkEffectEnabled = true,
                        syncEpochMs = animationEpochMs,
                    )
                    Text("Click on + to start a new chat")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .padding(10.dp),
                    // 48.dp は一覧先頭の見た目余白であり、安全領域の回避には使わない
                    contentPadding = PaddingValues(start = 0.dp, top = 48.dp, end = 0.dp, bottom = 0.dp)
                ) {
                    items(allChats.value.size) { index ->
                        ElevatedButton(
                            elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 10.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .padding(10.dp)
                                .size(40.dp),
                            onClick = {
                                navController.navigate(Routes.chat(allChats.value[index].chatId))
                            }) {
                            Row(Modifier.padding(10.dp)) { Text("${allChats.value[index].title}.") }
                        }
                    }
                }
            }
        }
    }

    // Chat Name Input Dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        val normalizedTitle = chatTitle.trim()
                        val newChatId = if (normalizedTitle.isBlank()) {
                            viewModel.insertChatAndReturnId(chat = Chat(title = "New chat", titleSource = TitleSource.TEMP))
                        } else {
                            viewModel.insertChatAndReturnId(chat = Chat(title = normalizedTitle, titleSource = TitleSource.MANUAL))
                        }
                        navController.navigate(Routes.chat(newChatId)) {
                            launchSingleTop = true
                        }
                        chatTitle = ""
                        showDialog = false
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("New Chat") },
            text = {
                OutlinedTextField(
                    value = chatTitle,
                    onValueChange = { chatTitle = it },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LamiSprite(
                                state = lamiState,
                                lamiStatus = lamiStatusState.value,
                                sizeDp = 32.dp,
                                contentPadding = 0.dp,
                                tightContainer = true,
                                backgroundColor = rememberLamiCharacterBackdropColor(),
                                debugOverlayEnabled = false,
                                animationsEnabled = true,
                                replacementEnabled = true,
                                blinkEffectEnabled = true,
                                syncEpochMs = animationEpochMs,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Chat Title")
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { showDialog = false })
                )
            }
        )
    }
}
