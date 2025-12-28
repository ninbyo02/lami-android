package com.sonusid.ollama.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sonusid.ollama.data.SpriteSheetConfig
import com.sonusid.ollama.ui.screens.settings.copyJsonToClipboard
import com.sonusid.ollama.ui.screens.settings.pasteJsonFromClipboard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpriteDebugScreen(
    navController: NavController,
    clipboardManager: ClipboardManager = LocalClipboardManager.current,
) {
    val context = LocalContext.current
    val settingsPreferences = remember { SettingsPreferences(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val config by settingsPreferences.spriteSheetConfig.collectAsState(
        initial = SpriteSheetConfig.default3x3()
    )
    var jsonText by remember(config) { mutableStateOf(config.toJson()) }

    fun showError(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun handleApply(json: String) {
        val parsed = SpriteSheetConfig.fromJson(json)
        if (parsed == null) {
            showError("JSON の解析に失敗しました")
            return
        }
        val validationError = parsed.validate()
        if (validationError != null) {
            showError(validationError)
            return
        }
        val normalized = SpriteSheetConfig.default3x3()
        jsonText = normalized.toJson()
        scope.launch {
            settingsPreferences.saveSpriteSheetConfig(normalized)
            snackbarHostState.showSnackbar("スプライト設定を保存しました（3x3 に固定）")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sprite Debug") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "スプライト設定を JSON で確認・編集できます。貼り付け時は検証を行い、失敗した場合はスナックバーで通知します。",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = jsonText,
                onValueChange = { jsonText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                label = { Text("SpriteSheetConfig JSON") },
                minLines = 6,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        jsonText = config.toJson()
                        copyJsonToClipboard(
                            clipboardManager = clipboardManager,
                            scope = scope,
                            snackbarHostState = snackbarHostState,
                            json = jsonText,
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "コピー")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("コピー")
                }
                Button(
                    onClick = {
                        pasteJsonFromClipboard(
                            clipboardManager = clipboardManager,
                            onPaste = { clipText -> handleApply(clipText) },
                            onEmpty = { showError("クリップボードが空です") },
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = "貼り付け")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("貼り付け")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { handleApply(jsonText) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
                Button(
                    onClick = {
                        scope.launch {
                            settingsPreferences.resetSpriteSheetConfig()
                            snackbarHostState.showSnackbar("初期3x3にリセットしました")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "リセット")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("リセット")
                }
            }
        }
    }
}
