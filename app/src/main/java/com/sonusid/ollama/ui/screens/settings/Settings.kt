package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.annotation.VisibleForTesting
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.sonusid.ollama.R
import com.sonusid.ollama.api.BaseUrlInitializationState
import com.sonusid.ollama.api.RetrofitClient
import com.sonusid.ollama.db.AppDatabase
import com.sonusid.ollama.db.entity.BaseUrl
import com.sonusid.ollama.db.repository.BaseUrlRepository
import com.sonusid.ollama.db.repository.ModelPreferenceRepository
import com.sonusid.ollama.util.PORT_ERROR_MESSAGE
import com.sonusid.ollama.util.UrlValidationResult
import com.sonusid.ollama.util.normalizeUrlInput
import com.sonusid.ollama.util.validateUrlFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.UUID

internal data class ServerInput(
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
    val modelPreferenceRepository = remember { ModelPreferenceRepository(db.modelPreferenceDao()) }
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    val serverInputs = remember { mutableStateListOf<ServerInput>() }
    var invalidConnections by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var duplicateUrls by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val maxServers = 5
    val serverInputIds = serverInputs.map { it.localId }

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
                    url = "http://localhost:11434",
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
        invalidConnections = invalidConnections.filterKeys { key -> key in serverInputIds }
        val normalizedInputs = getNormalizedInputs()
        duplicateUrls = detectDuplicateUrls(normalizedInputs)
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
        snackbarHost = {
            Box(modifier = Modifier.fillMaxSize()) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 72.dp, start = 16.dp, end = 16.dp),
                    snackbar = { snackbarData ->
                        val message = snackbarData.visuals.message
                        val isConnectionError = message.contains("接続できません")
                        Snackbar(
                            containerColor = if (isConnectionError) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.inverseSurface
                            }
                        ) {
                            Text(
                                text = message,
                                color = if (isConnectionError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    Color.White
                                }
                            )
                        }
                    }
                )
            }
        },
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
                            val normalized = normalizeUrlInput(newValue)
                            serverInputs[index] = serverInput.copy(url = normalized)
                            val normalizedInputs = getNormalizedInputs()
                            duplicateUrls = detectDuplicateUrls(normalizedInputs)
                        },
                        placeholder = { Text("http://host:port") },
                        label = { Text("Server ${index + 1}") },
                        singleLine = true,
                        isError = duplicateUrls[serverInput.localId] == true ||
                            !validateUrlFormat(serverInput.url).isValid ||
                            invalidConnections[serverInput.localId] == true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        supportingText = {
                            when {
                                duplicateUrls[serverInput.localId] == true -> {
                                    Text(
                                        text = "このURLは既に追加されています",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                invalidConnections[serverInput.localId] == true -> {
                                    Text("接続できません")
                                }
                            }
                        },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            errorBorderColor = MaterialTheme.colorScheme.error,
                            errorCursorColor = MaterialTheme.colorScheme.error,
                            errorLabelColor = MaterialTheme.colorScheme.error,
                            errorLeadingIconColor = MaterialTheme.colorScheme.error,
                            errorTrailingIconColor = MaterialTheme.colorScheme.error
                        ),
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
                                                url = "http://localhost:11434",
                                                isActive = false
                                            )
                                        )
                                        val normalizedInputs = getNormalizedInputs()
                                        duplicateUrls = detectDuplicateUrls(normalizedInputs)
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
                                        val updatedInvalidConnections =
                                            invalidConnections.toMutableMap().apply {
                                                remove(serverInput.localId)
                                            }
                                        serverInputs.removeAt(index)
                                        invalidConnections = updatedInvalidConnections
                                        val normalizedInputs = getNormalizedInputs()
                                        duplicateUrls = detectDuplicateUrls(normalizedInputs)
                                        if (wasActive && serverInputs.isNotEmpty()) {
                                            serverInputs[0] = serverInputs[0].copy(isActive = true)
                                        }
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Remove server")
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
                                val normalizedInputs = getNormalizedInputs()
                                val duplicates = detectDuplicateUrls(normalizedInputs)
                                duplicateUrls = duplicates
                                if (duplicates.isNotEmpty()) {
                                    invalidConnections = emptyMap()
                                    snackbarHostState.showSnackbar("同じURLは複数登録できません")
                                    return@launch
                                }
                                if (normalizedInputs.any { !validateUrlFormat(it.url).isValid }) {
                                    snackbarHostState.showSnackbar(PORT_ERROR_MESSAGE)
                                    return@launch
                                }
                                if (serverInputs.none { it.isActive }) {
                                    serverInputs[0] = serverInputs[0].copy(isActive = true)
                                }
                                val inputsForValidation = getNormalizedInputs()
                                val validationResults = withContext(Dispatchers.IO) {
                                    validateActiveConnections(inputsForValidation, ::isValidURL)
                                }
                                invalidConnections = validationResults
                                if (validationResults.values.any { it }) {
                                    snackbarHostState.showSnackbar("選択中のサーバーに接続できません。入力内容を確認してください")
                                    return@launch
                                }
                                invalidConnections = emptyMap()
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
                                    snackbarHostState.showSnackbar(fallbackMessage)
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
                                    invalidConnections = emptyMap()
                                    val normalizedInputs = getNormalizedInputs()
                                    duplicateUrls = detectDuplicateUrls(normalizedInputs)
                                } else {
                                    val normalizedActiveBaseUrl =
                                        normalizeUrlInput(initializationState.baseUrl).trimEnd('/')
                                    serverInputs.indices.forEach { i ->
                                        val current = serverInputs[i]
                                        val normalized = normalizeUrlInput(current.url).trimEnd('/')
                                        serverInputs[i] = current.copy(isActive = normalized == normalizedActiveBaseUrl)
                                    }
                                    snackbarHostState.showSnackbar("サーバー設定を保存しました")
                                }
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

suspend fun isValidURL(urlString: String): UrlValidationResult {
    val formatResult = validateUrlFormat(urlString)
    if (!formatResult.isValid) return formatResult
    return try {
        val url = URL(formatResult.normalizedUrl)
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            formatResult.copy(isValid = response.isSuccessful)
        }
    } catch (e: MalformedURLException) {
        formatResult.copy(isValid = false, errorMessage = PORT_ERROR_MESSAGE)
    } catch (e: IOException) {
        formatResult.copy(isValid = false)
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal suspend fun validateActiveConnections(
    inputs: List<ServerInput>,
    validateConnection: suspend (String) -> UrlValidationResult
): Map<String, Boolean> {
    val activeInputs = inputs.filter { it.isActive }
    return activeInputs.associate { input ->
        val validation = validateConnection(input.url)
        input.localId to !validation.isValid
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
        baseUrlRepository.replaceAll(inputsToSave)
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
