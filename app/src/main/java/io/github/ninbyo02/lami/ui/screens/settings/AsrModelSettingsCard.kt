package io.github.ninbyo02.lami.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ninbyo02.lami.asr.platform.model.AndroidLocalModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AsrModelUiState {
    CHECKING,
    NOT_INSTALLED,
    IMPORTING,
    INSTALLED,
    ERROR,
}

@Composable
internal fun AsrModelSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context.applicationContext) {
        AndroidLocalModelStore(context.applicationContext)
    }
    var state by remember { mutableStateOf(AsrModelUiState.CHECKING) }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    suspend fun refresh() {
        state = AsrModelUiState.CHECKING
        val installed = withContext(Dispatchers.IO) {
            if (store.isInstalledAndVerified()) {
                true
            } else if (store.missingStagedFiles().isEmpty()) {
                runCatching { store.loadOrInstall() }.isSuccess
            } else {
                false
            }
        }
        state = if (installed) AsrModelUiState.INSTALLED else AsrModelUiState.NOT_INSTALLED
    }

    LaunchedEffect(store) {
        refresh()
    }

    val openDocuments = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            state = AsrModelUiState.IMPORTING
            errorDetail = null
            val result = withContext(Dispatchers.IO) {
                runCatching { store.importSelected(uris) }
            }
            if (result.isSuccess) {
                state = AsrModelUiState.INSTALLED
            } else {
                state = AsrModelUiState.ERROR
                errorDetail = "指定された4ファイルがReazonSpeechモデルと一致しません。"
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("音声認識モデルを解除しますか？") },
            text = { Text("端末内に取り込んだReazonSpeechモデルを削除します。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        scope.launch {
                            state = AsrModelUiState.IMPORTING
                            withContext(Dispatchers.IO) { store.removeInstalledModel() }
                            state = AsrModelUiState.NOT_INSTALLED
                        }
                    },
                ) { Text("解除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("キャンセル")
                }
            },
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalModelCardLayoutContract.minHeightDp.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "音声認識モデル",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "日本語の端末内音声認識に使用します",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = when (state) {
                            AsrModelUiState.CHECKING -> "確認中…"
                            AsrModelUiState.IMPORTING -> "検証して読み込み中…"
                            AsrModelUiState.INSTALLED -> "ReazonSpeech k2 v2"
                            AsrModelUiState.NOT_INSTALLED -> "モデルが選択されていません"
                            AsrModelUiState.ERROR -> "モデルを読み込めませんでした"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Button(
                        onClick = { openDocuments.launch(arrayOf("*/*")) },
                        enabled = state !in setOf(
                            AsrModelUiState.CHECKING,
                            AsrModelUiState.IMPORTING,
                        ),
                    ) {
                        Text(if (state == AsrModelUiState.INSTALLED) "変更" else "選択")
                    }
                    Box(modifier = Modifier.height(48.dp)) {
                        if (state == AsrModelUiState.INSTALLED) {
                            TextButton(onClick = { showClearConfirmation = true }) {
                                Text("解除")
                            }
                        }
                    }
                }
            }
            errorDetail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
