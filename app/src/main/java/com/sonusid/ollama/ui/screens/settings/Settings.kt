package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.sonusid.ollama.R
import com.sonusid.ollama.api.RetrofitClient
import com.sonusid.ollama.db.AppDatabase
import com.sonusid.ollama.db.entity.BaseUrl
import com.sonusid.ollama.db.repository.BaseUrlRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.UUID

private data class ServerInput(
    val localId: String = UUID.randomUUID().toString(),
    val id: Int? = null,
    val url: String,
    val isActive: Boolean = false
)

fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(navgationController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    val baseUrlRepository = remember { BaseUrlRepository(db.baseUrlDao()) }
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    val serverInputs = remember { mutableStateListOf<ServerInput>() }
    val maxServers = 5

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
                    url = "localhost:11434",
                    isActive = true
                )
            )
        }
        serverInputs.clear()
        serverInputs.addAll(initialList)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navgationController.popBackStack() }) {
                        Icon(
                            painterResource(R.drawable.back),
                            "exit"
                        )
                    }
                },
                title = { Text("Settings") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.Center,
            ) { Text("Ollama v1.0.0") }
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            itemsIndexed(serverInputs, key = { _, item -> item.localId }) { index, serverInput ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = serverInput.isActive,
                        onClick = {
                            serverInputs.indices.forEach { i ->
                                val current = serverInputs[i]
                                serverInputs[i] = current.copy(isActive = i == index)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = serverInput.url,
                        onValueChange = { newValue ->
                            serverInputs[index] = serverInput.copy(url = newValue)
                        },
                        placeholder = { Text("localhost:11434") },
                        label = { Text("Server ${index + 1}") },
                        singleLine = true,
                        isError = serverInput.url.isBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    if (serverInputs.size >= maxServers) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("追加できるサーバー数は最大${maxServers}件です")
                                        }
                                    } else {
                                        serverInputs.add(
                                            ServerInput(
                                                url = "",
                                                isActive = false
                                            )
                                        )
                                    }
                                }) {
                                    Icon(Icons.Filled.Add, contentDescription = "Add server")
                                }
                                if (serverInputs.size > 1) {
                                    IconButton(onClick = {
                                        if (serverInputs.size <= 1) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("最低1件のサーバーを残してください")
                                            }
                                            return@IconButton
                                        }
                                        val wasActive = serverInputs[index].isActive
                                        serverInputs.removeAt(index)
                                        if (wasActive && serverInputs.isNotEmpty()) {
                                            serverInputs[0] = serverInputs[0].copy(isActive = true)
                                        }
                                    }) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Remove server")
                                    }
                                }
                            }
                        }
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                if (serverInputs.any { it.url.isBlank() }) {
                                    snackbarHostState.showSnackbar("空のURLを保存できません")
                                    return@launch
                                }
                                if (serverInputs.none { it.isActive }) {
                                    serverInputs[0] = serverInputs[0].copy(isActive = true)
                                }
                                val inputsToSave = serverInputs.mapIndexed { _, input ->
                                    BaseUrl(
                                        id = input.id ?: 0,
                                        url = input.url.trim(),
                                        isActive = input.isActive
                                    )
                                }
                                withContext(Dispatchers.IO) {
                                    baseUrlRepository.replaceAll(inputsToSave)
                                    RetrofitClient.refreshBaseUrl(baseUrlRepository)
                                }
                                snackbarHostState.showSnackbar("サーバー設定を保存しました")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.save),
                            contentDescription = "Save"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存")
                    }
                    ElevatedButton(
                        onClick = {
                            navgationController.navigate("about")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            Icon(
                                painterResource(R.drawable.about),
                                contentDescription = "About",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(20.dp))
                            Text("About")
                        }
                    }
                }
            }
        }
    }
}

suspend fun isValidURL(urlString: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val url = URL(urlString) // Check URL format
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        val response = client.newCall(request).execute()  // Make a request (empty)
        response.close() // Important: Close the response
        response.isSuccessful // Check if the response indicates success (2xx or 3xx status codes)

    } catch (e: MalformedURLException) {
        false // Invalid URL format
    } catch (e: IOException) {
        false // Network error or other IO issues
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    val dummyNav = rememberNavController()
    MaterialTheme(colorScheme = darkColorScheme()) {
        Settings(dummyNav)
    }
}
