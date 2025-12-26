package com.sonusid.ollama.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import com.sonusid.ollama.R
import com.sonusid.ollama.UiState
import com.sonusid.ollama.db.entity.Chat
import com.sonusid.ollama.db.entity.Message
import com.sonusid.ollama.ui.components.LamiSprite
import com.sonusid.ollama.ui.components.LamiAvatar
import com.sonusid.ollama.viewmodels.OllamaViewModel

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val selectedModel by viewModel.selectedModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val isLoadingModels by viewModel.isLoadingModels.collectAsState()
    val activeBaseUrl by viewModel.baseUrl.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val errorMessage = (uiState as? UiState.Error)?.errorMessage
    val mappedState by viewModel.lamiState.collectAsState()


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
            showSheet = false
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

    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = {
                LamiAvatar(
                    baseUrl = activeBaseUrl,
                    selectedModel = selectedModel,
                    lastError = errorMessage,
                    onNavigateSettings = { navHostController.navigate("setting") }
                )
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(onClick = { showSheet = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (selectedModel.isNullOrEmpty()) {
                                    "Select model"
                                } else {
                                    selectedModel.toString()
                                },
                                fontSize = 20.sp
                            )
                            if (isLoadingModels) {
                                Spacer(modifier = Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            },
            actions = {
                IconButton(onClick = {
                    navHostController.navigate("chats")
                }) {
                    Icon(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = "チャット一覧",
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = {
                    navHostController.navigate("setting")
                }) {
                    Icon(
                        painter = painterResource(R.drawable.settings),
                        contentDescription = "設定",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        )
    }, snackbarHost = { SnackbarHost(snackbarHostState) }, bottomBar = {
        OutlinedTextField(
            interactionSource = interactionSource,
            leadingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LamiSprite(state = mappedState, sizeDp = 32.dp)
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painterResource(R.drawable.logo),
                        contentDescription = "Logo",
                        modifier = Modifier.size(25.dp)
                    )
                }
            },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ask llama")
                }
            },
            value = userPrompt,
            onValueChange = { userPrompt = it },
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
                        if (selectedModel.isNullOrBlank()) {
                            showSheet = true
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("モデルを選択してください")
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

        LaunchedEffect(showSheet) {
            if (showSheet) {
                sheetState.show()
            } else {
                sheetState.hide()
                showSheet = false
            }
        }

        LaunchedEffect(errorMessage) {
            if (errorMessage != null) {
                snackbarHostState.showSnackbar(errorMessage)
            }
        }

        if (showSheet) {
            ModalBottomSheet(sheetState = sheetState, onDismissRequest = { showSheet = false }) {
                LazyColumn {
                    items(availableModels) { model ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateSelectedModel(model.name)
                                    showSheet = false
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedModel == model.name,
                                onClick = {
                                    viewModel.updateSelectedModel(model.name)
                                    showSheet = false
                                }
                            )
                            Text(model.name, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (effectiveChatId == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(if (isCreatingChat) "Creating new chat..." else "Preparing chat...")
                }
            } else if (allChats.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(painterResource(R.drawable.logo), "logo", modifier = Modifier.size(100.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    state = listState
                ) {
                    items(allChats.size) { index ->
                        ChatBubble(allChats[index].message, allChats[index].isSendbyMe)
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
                        .padding(16.dp),
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
