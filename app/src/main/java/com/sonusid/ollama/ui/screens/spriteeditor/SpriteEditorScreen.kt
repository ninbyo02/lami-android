package com.sonusid.ollama.ui.screens.spriteeditor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavController
import com.sonusid.ollama.R
import com.sonusid.ollama.ui.components.rememberLamiEditorSpriteBackdropColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpriteEditorScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val editorBackdropColor = rememberLamiEditorSpriteBackdropColor()
    var editorState by remember { mutableStateOf<SpriteEditorState?>(null) }
    var copiedSelection by remember { mutableStateOf<RectPx?>(null) }
    var displayScale by remember { mutableStateOf(1f) }
    var editUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val undoStack = remember { ArrayDeque<EditorSnapshot>() }
    val redoStack = remember { ArrayDeque<EditorSnapshot>() }

    suspend fun showSnackbarMessage(
        message: String,
        duration: SnackbarDuration = SnackbarDuration.Short,
    ) {
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(
            message = message,
            duration = duration,
        )
    }

    LaunchedEffect(context) {
        val autosaveFile = internalAutosaveFile(context)
        val autosaveBitmap = loadInternalAutosave(context)
        if (autosaveFile.exists() && autosaveBitmap == null) {
            showSnackbarMessage("スプライト画像の読み込みに失敗しました")
        }
        val bitmap = autosaveBitmap ?: withContext(Dispatchers.IO) {
            BitmapFactory.decodeResource(context.resources, R.drawable.lami_sprite_3x3_288)
        }
        if (bitmap == null) {
            showSnackbarMessage("スプライト画像の読み込みに失敗しました")
        } else {
            val safeBitmap = ensureArgb8888(bitmap)
            editorState = createInitialEditorState(safeBitmap)
            editUriString = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val persistResult = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            if (persistResult.isFailure) {
                showSnackbarMessage("権限の永続化に失敗しました(必要なら再選択してください)")
            }
            val bitmap = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
            val current = editorState
            if (bitmap == null || current == null) {
                showSnackbarMessage("PNGの読み込みに失敗しました")
                return@launch
            }
            pushUndoSnapshot(current, undoStack, redoStack)
            val safeBitmap = ensureArgb8888(bitmap)
            val nextSelection = rectNormalizeClamp(current.selection, safeBitmap.width, safeBitmap.height)
            editorState = current.copy(
                bitmap = safeBitmap,
                imageBitmap = safeBitmap.asImageBitmap(),
                selection = nextSelection,
                widthInput = nextSelection.w.toString(),
                heightInput = nextSelection.h.toString(),
                savedSnapshot = null,
                initialBitmap = safeBitmap,
            )
            editUriString = uri.toString()
            showSnackbarMessage("PNGを読み込みました")
        }
    }

    suspend fun writeBitmapToUri(targetUri: Uri, bitmap: Bitmap): Boolean {
        return withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            } ?: false
        }
    }

    suspend fun saveInternalAutosave(state: SpriteEditorState): Boolean {
        val safeBitmap = ensureArgb8888(state.bitmap)
        val success = saveInternalAutosave(context, safeBitmap)
        if (!success) {
            return false
        }
        val snapshot = safeBitmap.copy(Bitmap.Config.ARGB_8888, false)
        editorState = state.copy(
            bitmap = safeBitmap,
            imageBitmap = safeBitmap.asImageBitmap(),
            savedSnapshot = snapshot,
            initialBitmap = snapshot,
        )
        return true
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = editorState?.bitmap
            if (bitmap == null) {
                showSnackbarMessage("書き出す画像がありません")
                return@launch
            }
            val success = writeBitmapToUri(uri, bitmap)
            if (success) {
                showSnackbarMessage("PNGを書き出しました")
            } else {
                showSnackbarMessage("PNG書き出しに失敗しました")
            }
        }
    }

    fun updateState(block: (SpriteEditorState) -> SpriteEditorState) {
        val current = editorState ?: return
        editorState = block(current)
    }

    fun moveSelection(dx: Int, dy: Int) {
        val current = editorState ?: return
        pushUndoSnapshot(current, undoStack, redoStack)
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
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
            )
        },
        snackbarHost = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        // 上: ステータスバー回避のため最小限の top padding
                        .statusBarsPadding()
                        // 上: TopAppBar と重ならないように最小限の top padding
                        .padding(top = 56.dp + 8.dp)
                )
            }
        },
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
                    .padding(horizontal = 8.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        // [非dp] 横: レイアウト全体 の fillMaxWidth(制約)に関係
                        .fillMaxWidth()
                ) {
                    val isNarrow = maxWidth < 420.dp
                    val buttonHeight = 32.dp
                    val buttonMinHeight = 48.dp
                    // [dp] 左右: ボタン内側の余白(余白)に関係
                    val buttonPadding = PaddingValues(horizontal = 8.dp)
                    val pillShape = RoundedCornerShape(999.dp)
                    var moveMode by remember { mutableStateOf(MoveMode.Box) }
                    var widthText by remember(state?.widthInput) {
                        mutableStateOf(state?.widthInput.orEmpty())
                    }
                    var heightText by remember(state?.heightInput) {
                        mutableStateOf(state?.heightInput.orEmpty())
                    }
                    val inputContent: @Composable (Modifier) -> Unit = { modifier ->
                        Row(
                            modifier = modifier,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedTextField(
                                value = widthText,
                                onValueChange = { input: String ->
                                    val sanitized = digitsOnly(input).take(4)
                                    widthText = sanitized
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
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(54.dp)
                                    // Material3の最小高さ制約で54.dpに収まらない場合があるため保険として残す
                                    .heightIn(min = 54.dp)
                                    .testTag("spriteEditorWidthPx"),
                            )
                            OutlinedTextField(
                                value = heightText,
                                onValueChange = { input: String ->
                                    val sanitized = digitsOnly(input).take(4)
                                    heightText = sanitized
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
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(54.dp)
                                    // Material3の最小高さ制約で54.dpに収まらない場合があるため保険として残す
                                    .heightIn(min = 54.dp)
                                    .testTag("spriteEditorHeightPx"),
                            )
                        }
                    }
                    val previewContent: @Composable () -> Unit = {
                        Box(
                            modifier = Modifier
                                // [非dp] 横: プレビュー の fillMaxWidth(制約)に関係
                                .fillMaxWidth()
                                // [dp] 上: プレビュー の余白(余白)に関係
                                .padding(top = 4.dp)
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
                                        val offsetXPx = ((size.width - destinationWidth) / 2f).roundToInt()
                                        val offsetYPx = ((size.height - destinationHeight) / 2f).roundToInt()
                                        val copied = copiedSelection
                                        if (copied != null) {
                                            val copiedXPx = (copied.x * scale).roundToInt()
                                            val copiedYPx = (copied.y * scale).roundToInt()
                                            val copiedWPx = (copied.w * scale).roundToInt()
                                            val copiedHPx = (copied.h * scale).roundToInt()
                                            val copiedStrokePx = max(1, 3.dp.toPx().roundToInt())
                                            val copiedColor = Color.Cyan
                                            drawRect(
                                                color = copiedColor.copy(alpha = 0.25f),
                                                topLeft = Offset(
                                                    x = (offsetXPx + copiedXPx).toFloat(),
                                                    y = (offsetYPx + copiedYPx).toFloat(),
                                                ),
                                                size = Size(
                                                    width = copiedWPx.toFloat(),
                                                    height = copiedHPx.toFloat(),
                                                ),
                                            )
                                            drawRect(
                                                color = copiedColor,
                                                topLeft = Offset(
                                                    x = (offsetXPx + copiedXPx).toFloat(),
                                                    y = (offsetYPx + copiedYPx).toFloat(),
                                                ),
                                                size = Size(
                                                    width = copiedWPx.toFloat(),
                                                    height = copiedHPx.toFloat(),
                                                ),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = copiedStrokePx.toFloat()),
                                            )
                                        }
                                        val selectionXPx = (state.selection.x * scale).roundToInt()
                                        val selectionYPx = (state.selection.y * scale).roundToInt()
                                        val selectionWPx = (state.selection.w * scale).roundToInt()
                                        val selectionHPx = (state.selection.h * scale).roundToInt()
                                        val clipboardImage = state.clipboard?.let { ensureArgb8888(it).asImageBitmap() }
                                        if (clipboardImage != null) {
                                            drawImage(
                                                image = clipboardImage,
                                                srcOffset = IntOffset(0, 0),
                                                srcSize = IntSize(clipboardImage.width, clipboardImage.height),
                                                dstOffset = IntOffset(
                                                    x = offsetXPx + selectionXPx,
                                                    y = offsetYPx + selectionYPx,
                                                ),
                                                dstSize = IntSize(
                                                    width = selectionWPx,
                                                    height = selectionHPx,
                                                ),
                                                alpha = 0.45f,
                                                colorFilter = ColorFilter.tint(
                                                    color = Color(0xFF7FD7FF),
                                                    blendMode = BlendMode.SrcAtop,
                                                ),
                                            )
                                        }
                                        val strokePx = max(1, 2.dp.toPx().roundToInt())
                                        drawRect(
                                            color = Color.Red,
                                            topLeft = Offset(
                                                x = (offsetXPx + selectionXPx).toFloat(),
                                                y = (offsetYPx + selectionYPx).toFloat(),
                                            ),
                                            size = Size(
                                                width = selectionWPx.toFloat(),
                                                height = selectionHPx.toFloat(),
                                            ),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx.toFloat()),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    val statusContent: @Composable (Modifier) -> Unit = { modifier ->
                        Column(
                            modifier = modifier
                                // [dp] 上下: ステータス の余白(余白)に関係
                                .padding(vertical = 4.dp)
                                .testTag("spriteEditorStatus"),
                            // [dp] 縦: ステータス の間隔(間隔)に関係
                            verticalArrangement = Arrangement.spacedBy(0.dp)
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
                    }
                    fun moveSelectionByMode(dxSign: Int, dySign: Int, repeatStepPx: Int? = null) {
                        val currentMode = moveMode
                        if (currentMode == MoveMode.Box) {
                            val current = editorState ?: return
                            val step = if (dxSign != 0) {
                                current.selection.w.coerceAtLeast(1)
                            } else {
                                current.selection.h.coerceAtLeast(1)
                            }
                            moveSelection(dxSign * step, dySign * step)
                            return
                        }
                        val step = repeatStepPx ?: 4
                        moveSelection(dxSign * step, dySign * step)
                    }
                    val controlsContent: @Composable (Modifier) -> Unit = { modifier ->
                        Column(modifier = modifier) {
                            @Composable
                            fun StandardButton(
                                label: String,
                                testTag: String,
                                onClick: () -> Unit,
                            ) {
                                Button(
                                    onClick = onClick,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        // [dp] 縦: 見た目32dpを維持しつつタップ領域を確保
                                        .height(buttonHeight)
                                        .heightIn(min = buttonMinHeight)
                                        .testTag(testTag),
                                    contentPadding = buttonPadding,
                                    shape = pillShape,
                                ) {
                                    Text(label)
                                }
                            }
                            // 操作ボタン領域: 4x4グリッドで均等配置
                            LazyVerticalGrid(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("spriteEditorControls"),
                                columns = GridCells.Fixed(4),
                                // [dp] 横: 操作エリアの間隔(間隔)に関係
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                // [dp] 縦: 操作エリアの間隔(間隔)に関係
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        MoveButton(
                                            label = "←",
                                            testTag = "spriteEditorMoveLeft",
                                            onTap = { moveSelectionByMode(-1, 0) },
                                            onRepeat = { step -> moveSelectionByMode(-1, 0, step) },
                                            buttonHeight = buttonHeight,
                                            buttonMinHeight = buttonMinHeight,
                                            padding = buttonPadding,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = pillShape,
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        MoveButton(
                                            label = "→",
                                            testTag = "spriteEditorMoveRight",
                                            onTap = { moveSelectionByMode(1, 0) },
                                            onRepeat = { step -> moveSelectionByMode(1, 0, step) },
                                            buttonHeight = buttonHeight,
                                            buttonMinHeight = buttonMinHeight,
                                            padding = buttonPadding,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = pillShape,
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        val isPx = moveMode == MoveMode.Px
                                        Button(
                                            onClick = { moveMode = MoveMode.Px },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                // [dp] 縦: 見た目32dpを維持しつつタップ領域を確保
                                                .height(buttonHeight)
                                                .heightIn(min = buttonMinHeight),
                                            contentPadding = buttonPadding,
                                            shape = pillShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isPx) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                },
                                                contentColor = if (isPx) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            ),
                                            border = if (isPx) null else ButtonDefaults.outlinedButtonBorder
                                        ) {
                                            Text("PX")
                                        }
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        val isBox = moveMode == MoveMode.Box
                                        Button(
                                            onClick = { moveMode = MoveMode.Box },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                // [dp] 縦: 見た目32dpを維持しつつタップ領域を確保
                                                .height(buttonHeight)
                                                .heightIn(min = buttonMinHeight),
                                            contentPadding = buttonPadding,
                                            shape = pillShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isBox) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                },
                                                contentColor = if (isBox) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            ),
                                            border = if (isBox) null else ButtonDefaults.outlinedButtonBorder
                                        ) {
                                            Text("BOX")
                                        }
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        MoveButton(
                                            label = "↓",
                                            testTag = "spriteEditorMoveDown",
                                            onTap = { moveSelectionByMode(0, 1) },
                                            onRepeat = { step -> moveSelectionByMode(0, 1, step) },
                                            buttonHeight = buttonHeight,
                                            buttonMinHeight = buttonMinHeight,
                                            padding = buttonPadding,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = pillShape,
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        MoveButton(
                                            label = "↑",
                                            testTag = "spriteEditorMoveUp",
                                            onTap = { moveSelectionByMode(0, -1) },
                                            onRepeat = { step -> moveSelectionByMode(0, -1, step) },
                                            buttonHeight = buttonHeight,
                                            buttonMinHeight = buttonMinHeight,
                                            padding = buttonPadding,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = pillShape,
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        StandardButton(
                                            label = "Save",
                                            testTag = "spriteEditorSave",
                                            onClick = {
                                                scope.launch {
                                                    val current = editorState ?: return@launch
                                                    val result = runCatching { saveInternalAutosave(current) }
                                                    if (result.getOrDefault(false)) {
                                                        showSnackbarMessage("保存しました")
                                                    } else {
                                                        showSnackbarMessage("保存に失敗しました")
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        StandardButton(
                                            label = "Reset",
                                            testTag = "spriteEditorReset",
                                            onClick = {
                                                updateState { current ->
                                                    pushUndoSnapshot(current, undoStack, redoStack)
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
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        StandardButton(
                                            label = "Copy",
                                            testTag = "spriteEditorCopy",
                                            onClick = {
                                                updateState { current ->
                                                    val clip = copyRect(current.bitmap, current.selection)
                                                    copiedSelection = current.selection
                                                    current.withClipboard(clip)
                                                }
                                            },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        StandardButton(
                                            label = "Paste",
                                            testTag = "spriteEditorPaste",
                                            onClick = {
                                                updateState { current ->
                                                    val clip = current.clipboard
                                                    if (clip == null) {
                                                        current
                                                    } else {
                                                        pushUndoSnapshot(current, undoStack, redoStack)
                                                        val pasted = paste(
                                                            current.bitmap,
                                                            clip,
                                                            current.selection.x,
                                                            current.selection.y
                                                        )
                                                        copiedSelection = null
                                                        current.withBitmap(pasted).withClipboard(null)
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        StandardButton(
                                            label = "Undo",
                                            testTag = "spriteEditorUndo",
                                            onClick = {
                                                val current = editorState
                                                val snapshot = undoStack.removeLastOrNull()
                                                if (current != null && snapshot != null) {
                                                    redoStack.addLast(
                                                        EditorSnapshot(ensureArgb8888(current.bitmap), current.selection)
                                                    )
                                                    if (redoStack.size > MAX_HISTORY) {
                                                        redoStack.removeFirst()
                                                    }
                                                    editorState = current.applySnapshot(snapshot)
                                                }
                                            },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        StandardButton(
                                            label = "Redo",
                                            testTag = "spriteEditorRedo",
                                            onClick = {
                                                val current = editorState
                                                val snapshot = redoStack.removeLastOrNull()
                                                if (current != null && snapshot != null) {
                                                    undoStack.addLast(
                                                        EditorSnapshot(ensureArgb8888(current.bitmap), current.selection)
                                                    )
                                                    if (undoStack.size > MAX_HISTORY) {
                                                        undoStack.removeFirst()
                                                    }
                                                    editorState = current.applySnapshot(snapshot)
                                                }
                                            },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        StandardButton(
                                            label = "Delete",
                                            testTag = "spriteEditorDelete",
                                            onClick = {
                                                updateState { current ->
                                                    pushUndoSnapshot(current, undoStack, redoStack)
                                                    val cleared = clearTransparent(current.bitmap, current.selection)
                                                    current.withBitmap(cleared)
                                                }
                                            },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        StandardButton(
                                            label = "Fill Black",
                                            testTag = "spriteEditorFillBlack",
                                            onClick = {
                                                updateState { current ->
                                                    pushUndoSnapshot(current, undoStack, redoStack)
                                                    val filled = fillBlack(current.bitmap, current.selection)
                                                    current.withBitmap(filled)
                                                }
                                            },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        StandardButton(
                                            label = "Import",
                                            testTag = "spriteEditorImport",
                                            onClick = { importLauncher.launch(arrayOf("image/png")) },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        StandardButton(
                                            label = "Export",
                                            testTag = "spriteEditorExport",
                                            onClick = { exportLauncher.launch("sprite.png") },
                                        )
                                    }
                                }
                            }
                            Spacer(
                                modifier = Modifier
                                    // [dp] 下: 操作ボタン群の追加余白(余白)に関係
                                    .height(32.dp)
                            )
                        }
                    }
                    if (isNarrow) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            // [dp] 縦: 画面縦積み時の間隔(間隔)に関係
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            previewContent()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                statusContent(Modifier.weight(1f))
                                inputContent(Modifier)
                            }
                            controlsContent(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                // [dp] 縦: 右カラムの間隔(間隔)に関係
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                previewContent()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    statusContent(Modifier.weight(1f))
                                    inputContent(Modifier)
                                }
                                controlsContent(Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun digitsOnly(input: String): String = input.filter { ch -> ch.isDigit() }

@Composable
private fun OperationCell(
    minHeight: androidx.compose.ui.unit.Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        contentAlignment = Alignment.BottomCenter,
        content = content,
    )
}

@Composable
private fun MoveButton(
    label: String,
    testTag: String,
    onTap: () -> Unit,
    onRepeat: (stepPx: Int) -> Unit,
    buttonHeight: androidx.compose.ui.unit.Dp,
    buttonMinHeight: androidx.compose.ui.unit.Dp,
    padding: PaddingValues,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            // [dp] 縦: 見た目32dpを維持しつつタップ領域を確保
            .height(buttonHeight)
            .heightIn(min = buttonMinHeight)
            .fillMaxWidth()
            .testTag(testTag)
            // 簡易確認: 枠が震えない/長押し停止が即/単押し1回/8px↔4px切替
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    onTap()
                    val initialDelayMs = 300L
                    val startIntervalMs = 140L
                    val minIntervalMs = 90L
                    val accelDeltaMs = 10L
                    val accelCountThreshold = 12
                    val accelTimeMs = 800L
                    val startedAtMs = System.currentTimeMillis()
                    val releasedBeforeRepeat = withTimeoutOrNull(initialDelayMs) {
                        waitForUpOrCancellation()
                    }
                    if (releasedBeforeRepeat != null) {
                        return@awaitEachGesture
                    }
                    var intervalMs = startIntervalMs
                    var count = 0
                    while (true) {
                        val elapsedMs = System.currentTimeMillis() - startedAtMs
                        val stepPx = if (count < accelCountThreshold && elapsedMs < accelTimeMs) 4 else 8
                        onRepeat(stepPx)
                        count += 1
                        val released = withTimeoutOrNull(intervalMs) {
                            waitForUpOrCancellation()
                        }
                        if (released != null) {
                            return@awaitEachGesture
                        }
                        intervalMs = (intervalMs - accelDeltaMs).coerceAtLeast(minIntervalMs)
                    }
                }
            },
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = shape,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // [dp] 上下左右: 移動ボタン内側の余白(余白)に関係
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text(label)
        }
    }
}

private enum class MoveMode {
    Px,
    Box,
}

private data class EditorSnapshot(
    val bitmap: Bitmap,
    val selection: RectPx,
)

private fun pushUndoSnapshot(
    current: SpriteEditorState,
    undoStack: ArrayDeque<EditorSnapshot>,
    redoStack: ArrayDeque<EditorSnapshot>,
) {
    undoStack.addLast(EditorSnapshot(ensureArgb8888(current.bitmap), current.selection))
    if (undoStack.size > MAX_HISTORY) {
        undoStack.removeFirst()
    }
    redoStack.clear()
}

private fun SpriteEditorState.applySnapshot(snapshot: EditorSnapshot): SpriteEditorState {
    val normalized = rectNormalizeClamp(snapshot.selection, snapshot.bitmap.width, snapshot.bitmap.height)
    val restoredBitmap = ensureArgb8888(snapshot.bitmap)
    return copy(
        bitmap = restoredBitmap,
        imageBitmap = restoredBitmap.asImageBitmap(),
        selection = normalized,
        widthInput = normalized.w.toString(),
        heightInput = normalized.h.toString(),
    )
}

private fun internalAutosaveFile(context: android.content.Context): File {
    return File(context.filesDir, "sprite_editor/sprite_editor_autosave.png")
}

private suspend fun loadInternalAutosave(context: android.content.Context): Bitmap? {
    return withContext(Dispatchers.IO) {
        val file = internalAutosaveFile(context)
        if (!file.exists()) {
            return@withContext null
        }
        runCatching {
            FileInputStream(file).use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.getOrNull()?.let { bitmap -> ensureArgb8888(bitmap) }
    }
}

private suspend fun saveInternalAutosave(context: android.content.Context, bitmap: Bitmap): Boolean {
    return withContext(Dispatchers.IO) {
        val file = internalAutosaveFile(context)
        file.parentFile?.mkdirs()
        runCatching {
            val safeBitmap = ensureArgb8888(bitmap)
            FileOutputStream(file).use { output ->
                safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }.getOrDefault(false)
    }
}

private const val MAX_HISTORY = 10
