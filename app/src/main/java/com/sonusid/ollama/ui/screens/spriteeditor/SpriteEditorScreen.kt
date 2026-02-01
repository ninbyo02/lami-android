package com.sonusid.ollama.ui.screens.spriteeditor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.waitForUpOrCancellation
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sonusid.ollama.R
import com.sonusid.ollama.ui.components.rememberLamiEditorSpriteBackdropColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpriteEditorScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val editorBackdropColor = rememberLamiEditorSpriteBackdropColor()
    var editorState by remember { mutableStateOf<SpriteEditorState?>(null) }
    var displayScale by remember { mutableStateOf(1f) }

    LaunchedEffect(context) {
        val bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeResource(context.resources, R.drawable.lami_sprite_3x3_288)
        }
        if (bitmap == null) {
            snackbarHostState.showSnackbar("スプライト画像の読み込みに失敗しました")
        } else {
            editorState = createInitialEditorState(bitmap)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
            val current = editorState
            if (bitmap == null || current == null) {
                snackbarHostState.showSnackbar("PNGの読み込みに失敗しました")
                return@launch
            }
            val safeBitmap = ensureArgb8888(bitmap)
            val nextSelection = rectNormalizeClamp(current.selection, safeBitmap.width, safeBitmap.height)
            editorState = current.copy(
                bitmap = safeBitmap,
                imageBitmap = safeBitmap.asImageBitmap(),
                selection = nextSelection,
                widthInput = nextSelection.w.toString(),
                heightInput = nextSelection.h.toString(),
                savedSnapshot = null,
            )
            snackbarHostState.showSnackbar("PNGを読み込みました")
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = editorState?.bitmap
            if (bitmap == null) {
                snackbarHostState.showSnackbar("書き出す画像がありません")
                return@launch
            }
            val success = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                } ?: false
            }
            if (success) {
                snackbarHostState.showSnackbar("PNGを書き出しました")
            } else {
                snackbarHostState.showSnackbar("PNG書き出しに失敗しました")
            }
        }
    }

    fun updateState(block: (SpriteEditorState) -> SpriteEditorState) {
        val current = editorState ?: return
        editorState = block(current)
    }

    fun moveSelection(dx: Int, dy: Int) {
        updateState { state ->
            val moved = state.selection.moveBy(dx, dy)
            state.withSelection(rectNormalizeClamp(moved, state.bitmap.width, state.bitmap.height))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sprite Editor") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                // 上下: Scaffold の内側余白を適用
                .padding(innerPadding)
                // [非dp] 縦横: 画面全体 の fillMaxSize(制約)に関係
                .fillMaxSize(),
        ) {
            val state = editorState
            Column(
                modifier = Modifier
                    // [非dp] 横: プレビュー/操作領域 の fillMaxWidth(制約)に関係
                    .fillMaxWidth()
                    // [dp] 左右: 画面全体 の余白(余白)に関係
                    .padding(horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        // [非dp] 横: プレビュー の fillMaxWidth(制約)に関係
                        .fillMaxWidth()
                        // [dp] 上: プレビュー の余白(余白)に関係
                        .padding(top = 8.dp)
                        // [非dp] 縦: プレビュー の正方形レイアウト(制約)に関係
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .testTag("spriteEditorPreview"),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(editorBackdropColor)
                    )
                    if (state == null) {
                        Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                            Text("画像読み込み中", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        androidx.compose.foundation.Image(
                            bitmap = state.imageBitmap,
                            contentDescription = "Sprite Editor Preview",
                            modifier = Modifier
                                .matchParentSize()
                                .onSizeChanged { newSize ->
                                    displayScale = if (state.bitmap.width > 0) {
                                        newSize.width / state.bitmap.width.toFloat()
                                    } else {
                                        1f
                                    }
                                },
                            contentScale = ContentScale.Fit,
                        )
                        Canvas(modifier = Modifier.matchParentSize()) {
                            if (state.bitmap.width > 0 && state.bitmap.height > 0) {
                                val scaleX = size.width / state.bitmap.width
                                val scaleY = size.height / state.bitmap.height
                                val scale = min(scaleX, scaleY)
                                val destinationWidth = state.bitmap.width * scale
                                val destinationHeight = state.bitmap.height * scale
                                val offsetX = (size.width - destinationWidth) / 2f
                                val offsetY = (size.height - destinationHeight) / 2f
                                drawRect(
                                    color = Color.Red,
                                    topLeft = Offset(
                                        x = offsetX + state.selection.x * scale,
                                        y = offsetY + state.selection.y * scale,
                                    ),
                                    size = Size(
                                        width = state.selection.w * scale,
                                        height = state.selection.h * scale,
                                    ),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                                )
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        // [非dp] 横: ステータス の fillMaxWidth(制約)に関係
                        .fillMaxWidth()
                        // [dp] 上下: ステータス の余白(余白)に関係
                        .padding(vertical = 8.dp)
                        .testTag("spriteEditorStatus"),
                    // [dp] 縦: ステータス の間隔(間隔)に関係
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val statusLine1 = if (state == null) {
                        "画像読み込み中"
                    } else {
                        "画像: ${state.bitmap.width}×${state.bitmap.height} / ${"%.2f".format(displayScale)}x"
                    }
                    val statusLine2 = if (state == null) {
                        "選択: -, -, -, -"
                    } else {
                        "選択: ${state.selection.x},${state.selection.y},${state.selection.w},${state.selection.h}"
                    }
                    Text(
                        text = statusLine1,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = statusLine2,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(
                    modifier = Modifier
                        // [非dp] 横: 操作エリア の fillMaxWidth(制約)に関係
                        .fillMaxWidth()
                        .testTag("spriteEditorControls"),
                    // [dp] 縦: 操作エリア の間隔(間隔)に関係
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val buttonHeight = 32.dp
                    // [dp] 左右: ボタン内側の余白(余白)に関係
                    val buttonPadding = PaddingValues(horizontal = 8.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state?.widthInput.orEmpty(),
                            onValueChange = { input ->
                                val sanitized = input.filter { it.isDigit() }
                                updateState { current ->
                                    val updated = current.copy(widthInput = sanitized)
                                    val width = sanitized.toIntOrNull()
                                    if (width != null && width > 0) {
                                        val resized = current.selection.resize(width, current.selection.h)
                                        val normalized = rectNormalizeClamp(
                                            resized,
                                            current.bitmap.width,
                                            current.bitmap.height,
                                        )
                                        updated.copy(
                                            selection = normalized,
                                            widthInput = normalized.w.toString(),
                                            heightInput = normalized.h.toString(),
                                        )
                                    } else {
                                        updated
                                    }
                                }
                            },
                            label = { Text("W(px)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("spriteEditorWidthPx")
                        )
                        OutlinedTextField(
                            value = state?.heightInput.orEmpty(),
                            onValueChange = { input ->
                                val sanitized = input.filter { it.isDigit() }
                                updateState { current ->
                                    val updated = current.copy(heightInput = sanitized)
                                    val height = sanitized.toIntOrNull()
                                    if (height != null && height > 0) {
                                        val resized = current.selection.resize(current.selection.w, height)
                                        val normalized = rectNormalizeClamp(
                                            resized,
                                            current.bitmap.width,
                                            current.bitmap.height,
                                        )
                                        updated.copy(
                                            selection = normalized,
                                            widthInput = normalized.w.toString(),
                                            heightInput = normalized.h.toString(),
                                        )
                                    } else {
                                        updated
                                    }
                                }
                            },
                            label = { Text("H(px)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("spriteEditorHeightPx")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // [非dp] 横: 移動ボタン の左右余白(配置)に関係
                        Spacer(modifier = Modifier.weight(1f))
                        MoveButton(
                            label = "↑",
                            testTag = "spriteEditorMoveUp",
                            onMove = { moveSelection(0, -1) },
                            buttonHeight = buttonHeight,
                            padding = buttonPadding
                        )
                        // [非dp] 横: 移動ボタン の左右余白(配置)に関係
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MoveButton(
                            label = "←",
                            testTag = "spriteEditorMoveLeft",
                            onMove = { moveSelection(-1, 0) },
                            buttonHeight = buttonHeight,
                            padding = buttonPadding
                        )
                        // [非dp] 横: 移動ボタン の左右余白(配置)に関係
                        Spacer(modifier = Modifier.weight(1f))
                        MoveButton(
                            label = "→",
                            testTag = "spriteEditorMoveRight",
                            onMove = { moveSelection(1, 0) },
                            buttonHeight = buttonHeight,
                            padding = buttonPadding
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // [非dp] 横: 移動ボタン の左右余白(配置)に関係
                        Spacer(modifier = Modifier.weight(1f))
                        MoveButton(
                            label = "↓",
                            testTag = "spriteEditorMoveDown",
                            onMove = { moveSelection(0, 1) },
                            buttonHeight = buttonHeight,
                            padding = buttonPadding
                        )
                        // [非dp] 横: 移動ボタン の左右余白(配置)に関係
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                updateState { current ->
                                    val clip = copyRect(current.bitmap, current.selection)
                                    current.withClipboard(clip)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(buttonHeight)
                                .testTag("spriteEditorCopy"),
                            contentPadding = buttonPadding
                        ) {
                            Text("Copy")
                        }
                        Button(
                            onClick = {
                                updateState { current ->
                                    val clip = copyRect(current.bitmap, current.selection)
                                    val cleared = clearTransparent(current.bitmap, current.selection)
                                    current.withClipboard(clip).withBitmap(cleared)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(buttonHeight)
                                .testTag("spriteEditorCut"),
                            contentPadding = buttonPadding
                        ) {
                            Text("Cut")
                        }
                        Button(
                            onClick = {
                                updateState { current ->
                                    val clip = current.clipboard
                                    if (clip == null) {
                                        current
                                    } else {
                                        val pasted = paste(current.bitmap, clip, current.selection.x, current.selection.y)
                                        current.withBitmap(pasted)
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(buttonHeight)
                                .testTag("spriteEditorPaste"),
                            contentPadding = buttonPadding
                        ) {
                            Text("Paste")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                updateState { current ->
                                    val cleared = clearTransparent(current.bitmap, current.selection)
                                    current.withBitmap(cleared)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(buttonHeight)
                                .testTag("spriteEditorDelete"),
                            contentPadding = buttonPadding
                        ) {
                            Text("Delete")
                        }
                        Button(
                            onClick = {
                                updateState { current ->
                                    val filled = fillBlack(current.bitmap, current.selection)
                                    current.withBitmap(filled)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(buttonHeight)
                                .testTag("spriteEditorFillBlack"),
                            contentPadding = buttonPadding
                        ) {
                            Text("Fill Black")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                updateState { current ->
                                    val snapshot = current.bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                    current.withSavedSnapshot(snapshot)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(buttonHeight)
                                .testTag("spriteEditorSave"),
                            contentPadding = buttonPadding
                        ) {
                            Text("Save")
                        }
                        Button(
                            onClick = {
                                updateState { current ->
                                    val resetBitmap = current.savedSnapshot ?: current.initialBitmap
                                    val normalized = ensureArgb8888(resetBitmap)
                                    val nextSelection = rectNormalizeClamp(
                                        current.selection,
                                        normalized.width,
                                        normalized.height,
                                    )
                                    current.copy(
                                        bitmap = normalized,
                                        imageBitmap = normalized.asImageBitmap(),
                                        selection = nextSelection,
                                        widthInput = nextSelection.w.toString(),
                                        heightInput = nextSelection.h.toString(),
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(buttonHeight)
                                .testTag("spriteEditorReset"),
                            contentPadding = buttonPadding
                        ) {
                            Text("Reset")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { importLauncher.launch(arrayOf("image/png")) },
                            modifier = Modifier
                                .weight(1f)
                                .height(buttonHeight)
                                .testTag("spriteEditorImport"),
                            contentPadding = buttonPadding
                        ) {
                            Text("Import")
                        }
                        Button(
                            onClick = { exportLauncher.launch("sprite.png") },
                            modifier = Modifier
                                .weight(1f)
                                .height(buttonHeight)
                                .testTag("spriteEditorExport"),
                            contentPadding = buttonPadding
                        ) {
                            Text("Export")
                        }
                    }
                }
                // [dp] 下: 操作エリア下部の余白(余白)に関係
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun MoveButton(
    label: String,
    testTag: String,
    onMove: () -> Unit,
    buttonHeight: androidx.compose.ui.unit.Dp,
    padding: PaddingValues,
) {
    Button(
        onClick = onMove,
        modifier = Modifier
            .height(buttonHeight)
            .width(64.dp)
            .repeatOnPress(onMove)
            .testTag(testTag),
        contentPadding = padding,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(label)
    }
}

private fun Modifier.repeatOnPress(onRepeat: () -> Unit): Modifier = composed {
    pointerInput(onRepeat) {
        awaitEachGesture {
            awaitFirstDown()
            val job = launch {
                delay(300)
                while (true) {
                    onRepeat()
                    delay(50)
                }
            }
            waitForUpOrCancellation()
            job.cancel()
        }
    }
}
