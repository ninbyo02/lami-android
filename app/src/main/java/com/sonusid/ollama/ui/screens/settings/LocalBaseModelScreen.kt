package com.sonusid.ollama.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sonusid.ollama.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LocalModelImportState {
    Unset,
    Importing,
    Imported,
}

@Composable
fun LocalBaseModelScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importState by remember { mutableStateOf(LocalModelImportState.Unset) }
    var importedFileName by remember { mutableStateOf<String?>(null) }

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val previousState = importState
        val previousFileName = importedFileName

        scope.launch {
            importState = LocalModelImportState.Importing
            val copiedFileName = importLocalModelToAppStorage(context, uri)
            if (copiedFileName != null) {
                importedFileName = copiedFileName
                importState = LocalModelImportState.Imported
            } else {
                importedFileName = previousFileName
                importState = previousState
            }
        }
    }

    Scaffold(
        // Settings 系では Scaffold 自体は Insets を受けず、topBar/content の座標だけを返す
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SettingsTopAppBar(
                titleResId = R.string.local_base_model_title,
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
                    if (importState == LocalModelImportState.Imported && importedFileName != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = importedFileName.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                        enabled = importState != LocalModelImportState.Importing,
                    ) {
                        Text(text = stringResource(R.string.local_base_model_select_button))
                    }
                }
            }
        }
    }
}

private suspend fun importLocalModelToAppStorage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
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

        val oldModelFiles = modelsDir.listFiles().orEmpty().filter { it.isFile && it != targetFile }
        val failedDeletions = oldModelFiles.filterNot { file -> !file.exists() || file.delete() }
        if (failedDeletions.isNotEmpty()) {
            targetFile.delete()
            return@runCatching null
        }

        targetFile.name
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

private fun sanitizeFileName(name: String): String {
    val sanitized = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return sanitized.ifBlank { "local_model" }
}
