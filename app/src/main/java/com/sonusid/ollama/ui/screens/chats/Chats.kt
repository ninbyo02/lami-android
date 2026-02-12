package com.sonusid.ollama.ui.screens.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.navigation.NavController
import com.sonusid.ollama.R
import com.sonusid.ollama.UiState
import com.sonusid.ollama.db.entity.Chat
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.ui.components.HeaderAvatar
import com.sonusid.ollama.ui.components.LamiHeaderStatus
import com.sonusid.ollama.ui.components.LamiSprite
import com.sonusid.ollama.ui.components.rememberLamiCharacterBackdropColor
import com.sonusid.ollama.viewmodels.OllamaViewModel

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
    var chatTitle by remember { mutableStateOf("") }
    println(allChats.value)

    Scaffold(
        // 上部空白を 0dp に固定するため、Scaffold の Insets を無効化
        contentWindowInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
        topBar = {
        TopAppBar(
            // 上部空白を 0dp に固定するため、TopAppBar の Insets を無効化
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
                // TopAppBar 直下からスレッドを開始するため、Scaffold の下側 Insets のみ適用
                .padding(bottom = innerPadding.calculateBottomPadding())
                .fillMaxSize()
        ) {
            if (allChats.value.isEmpty()) {
                Column(
                    modifier = Modifier
                        // 先頭コンテンツがヘッダーに詰まり過ぎないよう最小限の上余白を付与
                        .padding(top = 8.dp)
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
                    // 先頭スレッドだけヘッダー直下の窮屈さを解消するため最小限の上余白を付与
                    contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp)
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
                    if (chatTitle.isNotBlank()) {
                        viewModel.insertChat(chat = Chat(title = chatTitle))
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
