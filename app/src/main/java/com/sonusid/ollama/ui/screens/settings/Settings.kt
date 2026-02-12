package com.sonusid.ollama.ui.screens.settings
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.annotation.VisibleForTesting
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.navigation.SettingsRoute
import com.sonusid.ollama.R
import com.sonusid.ollama.api.BaseUrlInitializationState
import com.sonusid.ollama.api.RetrofitClient
import com.sonusid.ollama.db.AppDatabase
import com.sonusid.ollama.db.entity.BaseUrl
import com.sonusid.ollama.db.repository.BaseUrlRepository
import com.sonusid.ollama.db.repository.ModelPreferenceRepository
import com.sonusid.ollama.ui.common.LocalAppSnackbarHostState
import com.sonusid.ollama.ui.common.PROJECT_SNACKBAR_SHORT_MS
import com.sonusid.ollama.util.PORT_ERROR_MESSAGE
import com.sonusid.ollama.util.normalizeUrlInput
import com.sonusid.ollama.util.validateUrlFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

internal data class ServerInput(
    val localId: String = UUID.randomUUID().toString(),
    val id: Int? = null,
    val url: String,
    val isActive: Boolean = false
)

// サーバー接続検証中インジケータの視覚的中心補正
private val ServerValidationIndicatorYOffset = 3.dp

fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(navgationController: NavController, onSaved: () -> Unit = {}) {
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
            listOf(
                ServerInput(
                    url = "http://localhost:13511/",
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
        connectionStatuses = connectionStatuses.filterKeys { key -> key in serverInputIds }
        val normalizedInputs = getNormalizedInputs()
        duplicateUrls = detectDuplicateUrls(normalizedInputs)
    }

    val horizontalPadding = 16.dp
    val verticalPadding = 12.dp

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemBarInsets = WindowInsets.systemBars
    // 左右の安全領域は維持し、上は TopAppBar のデフォルト Insets に任せる
    val scaffoldInsets = WindowInsets(
        left = systemBarInsets.getLeft(density, layoutDirection),
        top = 0,
        right = systemBarInsets.getRight(density, layoutDirection),
        bottom = 0
    )

    Scaffold(
        modifier = Modifier.testTag("settingsScreenRoot"),
        // 左右の安全領域は維持し、上は TopAppBar 側で処理する
        contentWindowInsets = scaffoldInsets,
        topBar = {
            Box(
                modifier = Modifier
                    // [dp] 縦: TopAppBar 直下の余白を詰めるため高さを固定
                    .height(48.dp)
                    .fillMaxWidth()
            ) {
                TopAppBar(
                    // 上余白の原因切り分けのため、TopAppBar 側の Insets は明示的に 0 にする
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = {
                        IconButton(
                            onClick = { onBackRequested() },
                            modifier = Modifier
                                .fillMaxHeight()
                                .wrapContentHeight(Alignment.CenterVertically)
                        ) {
                            Icon(
                                painterResource(R.drawable.back),
                                "exit"
                            )
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
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // 上下左右の余白を反映するための padding
                .padding(paddingValues),
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
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Card {
                    Column {
                        ListItem(
                            headlineContent = {
                                Text("Sprite Settings", style = MaterialTheme.typography.titleMedium)
                            },
                            supportingContent = {
                                Text(
                                    "スプライト画像を表示します",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Filled.BugReport,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navgationController.navigate(SettingsRoute.SpriteSettings.route) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        ListItem(
                            headlineContent = {
                                Text("Sprite Editor", style = MaterialTheme.typography.titleMedium)
                            },
                            supportingContent = {
                                Text(
                                    "スプライト画像の編集・書き出しを行います",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Filled.BugReport,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navgationController.navigate(SettingsRoute.SpriteEditor.route) }
                                // [dp] 左右/上下: ListItem の余白(余白)に関係
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                }
            }
            item {
                CardSectionHeader(
                    title = "表示設定",
                    description = "テーマカラーなどの外観設定を変更できます",
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Card {
                    ListItem(
                        headlineContent = {
                            Text("ダイナミックカラー", style = MaterialTheme.typography.titleMedium)
                        },
                        supportingContent = {
                            Text(
                                "システムカラーに合わせて配色を自動調整します",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = settingsData.useDynamicColor,
                                onCheckedChange = { enabled ->
                                    scope.launch { settingsPreferences.updateDynamicColor(enabled) }
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            item {
                CardSectionHeader(
                    title = "サーバー設定",
                    description = "Ollama サーバーのURLと接続状態を管理します",
                    modifier = Modifier.padding(
                        // 下: サーバー設定の見出しとカードの間隔を最小限確保
                        bottom = 2.dp
                    )
                )
            }
            itemsIndexed(serverInputs, key = { _, item -> item.localId }) { index, serverInput ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                    placeholder = { Text("http://host:port") },
                                    label = { Text("Server ${index + 1}") },
                                    singleLine = true,
                                    isError = duplicateUrls[serverInput.localId] == true ||
                                        !validateUrlFormat(serverInput.url).isValid ||
                                        connectionStatuses[serverInput.localId]?.isReachable == false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
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
                                                        snackbarHostState.showSnackbar(
                                                            message = "追加できるサーバー数は最大${maxServers}件です",
                                                            duration = SnackbarDuration.Short
                                                        )
                                                    }
                                                } else {
                                                    serverInputs.add(
                                                        ServerInput(
                                                            url = "http://localhost:13511/",
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
                                                            snackbarHostState.showSnackbar(
                                                                message = "最低1件のサーバーを残してください",
                                                                duration = SnackbarDuration.Short
                                                            )
                                                        }
                                                        return@IconButton
                                                    }
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
                                                }) {
                                                    Icon(Icons.Filled.Delete, contentDescription = "Remove server")
                                                }
                                            }
                                        }
                                    }
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
                }
            }
            item {
                Card {
                    Column {
                        ListItem(
                            headlineContent = {
                                Text("設定を保存", style = MaterialTheme.typography.titleMedium)
                            },
                            supportingContent = {
                                Text(
                                    "入力したサーバー設定を検証して保存します",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                Button(onClick = {
                                    scope.launch {
                                        if (serverInputs.any { it.url.isBlank() }) {
                                            snackbarHostState.showSnackbar(
                                                message = "空のURLを保存できません",
                                                duration = SnackbarDuration.Short
                                            )
                                            return@launch
                                        }
                                        val normalizedInputs = getNormalizedInputs()
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
                                        if (serverInputs.none { it.isActive }) {
                                            serverInputs[0] = serverInputs[0].copy(isActive = true)
                                        }
                                        val inputsForValidation = getNormalizedInputs()
                                        isValidatingConnections = true
                                        val validationResults = try {
                                            withContext(Dispatchers.IO) {
                                                validateActiveConnections(inputsForValidation, ::isValidURL)
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
                                                ?: "有効なURLがないためデフォルトにフォールバックしました"
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
                                                normalizeUrlInput(initializationState.baseUrl).trimEnd('/')
                                            serverInputs.indices.forEach { i ->
                                                val current = serverInputs[i]
                                                val normalized = normalizeUrlInput(current.url).trimEnd('/')
                                                serverInputs[i] = current.copy(isActive = normalized == normalizedActiveBaseUrl)
                                            }
                                            showSuccessSnackbarShort("サーバー設定を保存しました")
                                        }
                                    }
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.save),
                                        contentDescription = "Save"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("保存")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.about),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            supportingContent = {
                                Text(
                                    "バージョン情報やオープンソースライセンスを表示します",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navgationController.navigate(Routes.ABOUT) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardSectionHeader(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal suspend fun isValidURL(urlString: String): ConnectionValidationResult {
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
        val requestUrl = URL("$baseUrl/api/tags")
        // LAN 内利用を想定し、体感ラグを抑えるためにタイムアウトを短めに設定する
        val connectTimeoutSeconds = 2L
        val readTimeoutSeconds = 3L
        val client = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val request = Request.Builder().url(requestUrl).get().build()

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
