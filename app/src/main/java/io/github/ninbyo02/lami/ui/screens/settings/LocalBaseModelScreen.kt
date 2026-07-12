package io.github.ninbyo02.lami.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.ninbyo02.lami.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LocalModelImportState {
    Unset,
    Importing,
    Imported,
}

private data class LocalModelImportResult(
    val displayName: String,
    val filePath: String,
)

@Composable
fun LocalBaseModelScreen(navController: NavController) {
    LocalModelScreen(
        navController = navController,
        slot = LocalModelSlot.NpuPreview,
    )
}

@Composable
fun LocalGenericFallbackModelScreen(navController: NavController) {
    LocalModelScreen(
        navController = navController,
        slot = LocalModelSlot.GenericFallback,
    )
}

@Composable
internal fun LocalModelSlotCard(
    slot: LocalModelSlot,
    highlighted: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsPreferences = remember(context) { SettingsPreferences(context) }
    var isImporting by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val savedDisplayName by slot.displayNameFlow(settingsPreferences).collectAsState(initial = null)
    val savedFilePath by slot.filePathFlow(settingsPreferences).collectAsState(initial = null)
    val hasModel = isValidSavedLocalModelInfo(savedDisplayName, savedFilePath)
    val warning = localModelCompatibilityWarning(slot, savedDisplayName)

    LaunchedEffect(savedDisplayName, savedFilePath, isImporting) {
        if (!isImporting && !hasModel && (!savedDisplayName.isNullOrBlank() || !savedFilePath.isNullOrBlank())) {
            slot.clearModelInfo(settingsPreferences)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val displayName = resolveDisplayName(context, uri)
        if (!isLitertlmDisplayName(displayName)) return@rememberLauncherForActivityResult
        scope.launch {
            isImporting = true
            val importedResult = importLocalModelToAppStorage(context, uri, savedFilePath)
            if (importedResult != null) {
                slot.saveModelInfo(settingsPreferences, importedResult.displayName, importedResult.filePath)
            }
            isImporting = false
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("モデル設定を解除しますか？") },
            text = { Text("選択中の${slot.title}を解除し、端末内に取り込んだファイルを削除します。") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showClearConfirmation = false
                        scope.launch {
                            isImporting = true
                            clearImportedLocalModel(savedFilePath, settingsPreferences, slot)
                            isImporting = false
                        }
                    },
                ) { Text("解除") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showClearConfirmation = false },
                ) { Text("キャンセル") }
            },
        )
    }

    val borderColor by animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(durationMillis = 180),
        label = "localModelCardBorderColorPulse",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (highlighted) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = 180),
        label = "localModelCardBorderWidthPulse",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalModelCardLayoutContract.minHeightDp.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(borderWidth, borderColor),
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
                        text = slot.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = slot.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (isImporting) "読み込み中…" else localModelSlotStatusLabel(savedDisplayName),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Button(
                        onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                        enabled = !isImporting,
                    ) {
                        Text(localModelSlotActionLabel(hasModel))
                    }
                    Box(modifier = Modifier.height(48.dp)) {
                        if (hasModel) {
                            androidx.compose.material3.TextButton(
                                onClick = { showClearConfirmation = true },
                                enabled = !isImporting,
                            ) {
                                Text("解除")
                            }
                        }
                    }
                }
            }
            warning?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LocalModelScreen(
    navController: NavController,
    slot: LocalModelSlot,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsPreferences = remember(context) { SettingsPreferences(context) }
    var importState by remember { mutableStateOf(LocalModelImportState.Unset) }
    var importedFileDisplayName by remember { mutableStateOf<String?>(null) }
    val savedDisplayName by slot.displayNameFlow(settingsPreferences).collectAsState(initial = null)
    val savedFilePath by slot.filePathFlow(settingsPreferences).collectAsState(initial = null)

    LaunchedEffect(savedDisplayName, savedFilePath, importState) {
        if (importState == LocalModelImportState.Importing) return@LaunchedEffect

        val displayName = savedDisplayName
        val filePath = savedFilePath
        if (isValidSavedLocalModelInfo(displayName = displayName, filePath = filePath)) {
            importedFileDisplayName = displayName
            importState = LocalModelImportState.Imported
        } else {
            if (!displayName.isNullOrBlank() || !filePath.isNullOrBlank()) {
                slot.clearModelInfo(settingsPreferences)
            }
            importedFileDisplayName = null
            importState = LocalModelImportState.Unset
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val previousState = importState
        val previousFileDisplayName = importedFileDisplayName
        val displayName = resolveDisplayName(context, uri)
        if (!isLitertlmDisplayName(displayName)) return@rememberLauncherForActivityResult

        scope.launch {
            importState = LocalModelImportState.Importing
            val importedResult = importLocalModelToAppStorage(
                context = context, uri = uri, previousSlotFilePath = savedFilePath,
            )
            if (importedResult != null) {
                slot.saveModelInfo(
                    settingsPreferences = settingsPreferences,
                    displayName = importedResult.displayName,
                    filePath = importedResult.filePath,
                )
                importedFileDisplayName = importedResult.displayName
                importState = LocalModelImportState.Imported
            } else {
                importedFileDisplayName = previousFileDisplayName
                importState = previousState
            }
        }
    }

    Scaffold(
        // Settings 系では Scaffold 自体は Insets を受けず、topBar/content の座標だけを返す
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SettingsTopAppBar(
                title = slot.title,
                onBack = { navController.popBackStack() },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 上下左右: Scaffold と TopBar が決めた描画領域に本文を揃える
                .padding(innerPadding)
                // Scaffold の Insets はこの階層で消費し、本文側へ二重適用しない
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    // 画面端との接触を避けるための最小限の余白
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // カード内テキストの可読性を保つための最小限の余白
                        .padding(16.dp),
                ) {
                    Text(
                        text = slot.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.local_base_model_status_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (importState) {
                            LocalModelImportState.Unset -> stringResource(R.string.local_base_model_status_unset)
                            LocalModelImportState.Importing -> stringResource(R.string.local_base_model_status_importing)
                            LocalModelImportState.Imported -> stringResource(R.string.local_base_model_status_imported)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (importState == LocalModelImportState.Imported) {
                            importedFileDisplayName.orEmpty()
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        minLines = 1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                            enabled = importState != LocalModelImportState.Importing,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(R.string.local_base_model_select_button))
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    importState = LocalModelImportState.Importing
                                    clearImportedLocalModel(
                                        filePath = savedFilePath,
                                        settingsPreferences = settingsPreferences,
                                        slot = slot,
                                    )
                                    importedFileDisplayName = null
                                    importState = LocalModelImportState.Unset
                                }
                            },
                            enabled = importState == LocalModelImportState.Imported,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(R.string.local_base_model_clear_button))
                        }
                    }
                }
            }
        }
    }
}

private suspend fun importLocalModelToAppStorage(
    context: Context,
    uri: Uri,
    previousSlotFilePath: String?,
): LocalModelImportResult? = withContext(Dispatchers.IO) {
    runCatching {
        val modelsDir = File(context.filesDir, "local_models")
        if (!modelsDir.exists() && !modelsDir.mkdirs()) {
            return@runCatching null
        }

        val displayName = resolveDisplayName(context, uri) ?: "local_model"
        val safeName = sanitizeFileName(displayName)
        val targetFile = File(modelsDir, "${System.currentTimeMillis()}_$safeName")
        val tempFile = File(modelsDir, ".${targetFile.name}.tmp")

        if (tempFile.exists()) {
            tempFile.delete()
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        } ?: return@runCatching null

        if (!tempFile.renameTo(targetFile)) {
            tempFile.delete()
            return@runCatching null
        }

        val previousSlotFile = previousSlotFilePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
        val oldModelFiles = listOfNotNull(previousSlotFile).filter { file ->
            file.isFile && file != targetFile &&
                runCatching { file.parentFile?.canonicalPath == modelsDir.canonicalPath }.getOrDefault(false)
        }
        val failedDeletions = oldModelFiles.filterNot { file -> !file.exists() || file.delete() }
        if (failedDeletions.isNotEmpty()) {
            targetFile.delete()
            return@runCatching null
        }

        LocalModelImportResult(
            displayName = displayName,
            filePath = targetFile.absolutePath,
        )
    }.getOrNull()
}

private fun resolveDisplayName(context: Context, uri: Uri): String? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex == -1 || !cursor.moveToFirst()) return@use null
        cursor.getString(nameIndex)
    }
}

private fun isLitertlmDisplayName(displayName: String?): Boolean {
    if (displayName.isNullOrBlank()) return false
    return displayName.endsWith(".litertlm", ignoreCase = true)
}

internal fun isValidSavedLocalModelInfo(displayName: String?, filePath: String?): Boolean {
    if (displayName.isNullOrBlank() || filePath.isNullOrBlank()) return false
    if (!displayName.endsWith(".litertlm", ignoreCase = true)) return false

    return runCatching {
        val modelFile = File(filePath)
        modelFile.isFile &&
            modelFile.length() > 0L &&
            modelFile.name.endsWith(".litertlm", ignoreCase = true)
    }.getOrDefault(false)
}

private fun sanitizeFileName(name: String): String {
    val sanitized = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return sanitized.ifBlank { "local_model" }
}

private suspend fun clearImportedLocalModel(
    filePath: String?,
    settingsPreferences: SettingsPreferences,
    slot: LocalModelSlot,
) = withContext(Dispatchers.IO) {
    if (!filePath.isNullOrBlank()) {
        runCatching {
            val modelFile = File(filePath)
            if (modelFile.exists()) {
                modelFile.delete()
            }
        }
    }
    slot.clearModelInfo(settingsPreferences)
}
