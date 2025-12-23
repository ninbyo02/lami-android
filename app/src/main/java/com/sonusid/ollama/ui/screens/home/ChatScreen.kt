package com.sonusid.ollama.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.sonusid.ollama.db.entity.Message
import com.sonusid.ollama.viewmodels.OllamaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    navHostController: NavHostController,
    viewModel: OllamaViewModel,
    chatId: Int = viewModel.chats.value.last().chatId + 1,
) {

    val uiState by viewModel.uiState.collectAsState()
    val interactionSource = remember { MutableInteractionSource() }
    var userPrompt: String by remember { mutableStateOf("") }
    remember { mutableStateListOf<String>() }
    var prompt: String by remember { mutableStateOf("") }
    val allChats = viewModel.allMessages(chatId).collectAsState(initial = emptyList())
    var toggle by remember { mutableStateOf(false) }
    var placeholder by remember { mutableStateOf("Enter your prompt ...") }
    var showModelSelectionDialog by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf<String?>(null) }
    val availableModels by viewModel.availableModels.collectAsState()
    val listState = rememberLazyListState()


    LaunchedEffect(allChats.value.size) {
        if (allChats.value.isNotEmpty()) {
            listState.animateScrollToItem(allChats.value.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadAvailableModels()
    }

    LaunchedEffect(uiState) {
        if (toggle) {
            when (uiState) {
                is UiState.Success -> {
                    val response = (uiState as UiState.Success).outputText
                    viewModel.insert(Message(message = response, chatId = chatId, isSendbyMe = false))
                    placeholder = "Enter your prompt..."
                }

                is UiState.Error -> {
                    viewModel.insert(
                        Message(
                            message = (uiState as UiState.Error).errorMessage, chatId = chatId, isSendbyMe = false
                        )
                    )
                    placeholder = "Enter your prompt..."
                }

                else -> {
                }
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    navHostController.navigate("chats")
                }) {
                    Icon(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = "logo",
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Model selection button:
                TextButton(onClick = {
                    showModelSelectionDialog = true
                }) {
                    Text(
                        if (selectedModel.isNullOrEmpty()) {
                            "Ollama"
                        } else {
                            selectedModel.toString()
                        }, fontSize = 20.sp
                    ) // Display selected model
                }

                IconButton(onClick = {
                    navHostController.navigate("setting")
                }) {
                    Icon(
                        painter = painterResource(R.drawable.settings),
                        contentDescription = "settings",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        })
    }, bottomBar = {
        OutlinedTextField(
            interactionSource = interactionSource,
            label = {
                Row {
                    Icon(
                        painterResource(R.drawable.logo),
                        contentDescription = "Logo",
                        Modifier.size(25.dp)
                    )
                    Spacer(Modifier.width(5.dp))
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
                    onClick = {
                        if (selectedModel != null) {
                            if (userPrompt.isNotEmpty()) {
                                placeholder = "I'm thinking ... "
                                viewModel.insert(Message(chatId = chatId, message = userPrompt, isSendbyMe = true))
                                toggle = true
                                prompt = userPrompt
                                userPrompt = ""
                                viewModel.sendPrompt(prompt, selectedModel)
                                prompt = ""
                            }
                        } else {
                            viewModel.insert(Message(chatId = chatId, message = userPrompt, isSendbyMe = true))
                            userPrompt = ""
                            viewModel.insert(
                                Message(
                                    chatId = chatId, message = "Please Choose a model", isSendbyMe = false
                                )
                            )
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

        // Model Selection Dialog
        if (showModelSelectionDialog) {
            AlertDialog(onDismissRequest = { showModelSelectionDialog = false },
                title = { Text("Select Model") },
                text = {
                    val (tempModel, setTempModel) = rememberSaveable { mutableStateOf(selectedModel) }

                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        availableModels.forEach { model ->
                            item {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { setTempModel(model.name) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = tempModel == model.name, onClick = { setTempModel(model.name) })
                                    Text(
                                        model.name,
                                        Modifier.padding(start = 8.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        selectedModel = tempModel
                        showModelSelectionDialog = false
                    }) {
                        Text("OK")
                    }
                })
        }

        if (allChats.value.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(painterResource(R.drawable.logo), "logo", modifier = Modifier.size(100.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .padding(16.dp),
                state = listState
            ) {
                items(allChats.value.size) { index ->
                    ChatBubble(allChats.value[index].message, allChats.value[index].isSendbyMe)
                }
            }
        }
    }
}
