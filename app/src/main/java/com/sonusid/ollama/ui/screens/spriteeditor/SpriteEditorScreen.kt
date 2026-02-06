package com.sonusid.ollama.ui.screens.spriteeditor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
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
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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

private const val GRID_ON_SCALE = 8f
private const val GRID_OFF_SCALE = 7f
private const val GRID_MAJOR_STEP = 8
private const val GRID_ALPHA_MAX_SCALE = 16f
private const val CHECKER_LIGHT_ALPHA = 0.32f
private const val CHECKER_DARK_ALPHA = 0.55f

private val CHECKER_CELL_SIZE = 8.dp

private enum class SheetType {
    None,
    More,
    Tools,
}

private enum class ApplySource(val label: String) {
    Selection("Selection"),
    FullImage("Full Image"),
}

private sealed class LastToolOp {
    data object Grayscale : LastToolOp()
    data object Outline : LastToolOp()
    data object Binarize : LastToolOp()
    data object ClearBackground : LastToolOp()
    data object ClearRegion : LastToolOp()
    data object FillConnected : LastToolOp()
    data object CenterContentInBox : LastToolOp()
    data class ResizeToMax96(val anchor: ResizeAnchor) : LastToolOp()
}

private val LastToolOpSaver = Saver<LastToolOp?, List<String>?>(
    save = { op ->
        when (op) {
            null -> null
            LastToolOp.Grayscale -> listOf("Grayscale")
            LastToolOp.Outline -> listOf("Outline")
            LastToolOp.Binarize -> listOf("Binarize")
            LastToolOp.ClearBackground -> listOf("ClearBackground")
            LastToolOp.ClearRegion -> listOf("ClearRegion")
            LastToolOp.FillConnected -> listOf("FillConnected")
            LastToolOp.CenterContentInBox -> listOf("CenterContentInBox")
            is LastToolOp.ResizeToMax96 -> listOf("ResizeToMax96", op.anchor.name)
        }
    },
    restore = { data ->
        val type = data?.firstOrNull() ?: return@Saver null
        when (type) {
            "Grayscale" -> LastToolOp.Grayscale
            "Outline" -> LastToolOp.Outline
            "Binarize" -> LastToolOp.Binarize
            "ClearBackground" -> LastToolOp.ClearBackground
            "ClearRegion" -> LastToolOp.ClearRegion
            "FillConnected" -> LastToolOp.FillConnected
            "CenterContentInBox" -> LastToolOp.CenterContentInBox
            "ResizeToMax96" -> {
                val anchor = data.getOrNull(1)
                    ?.let { runCatching { ResizeAnchor.valueOf(it) }.getOrNull() }
                    ?: ResizeAnchor.TopLeft
                LastToolOp.ResizeToMax96(anchor)
            }

            else -> null
        }
    },
)

private fun lerpFloat(start: Float, end: Float, t: Float): Float {
    return start + (end - start) * t
}

private fun gridAlphaForScale(scale: Float, minAlpha: Float, maxAlpha: Float): Float {
    val t = ((scale - GRID_ON_SCALE) / (GRID_ALPHA_MAX_SCALE - GRID_ON_SCALE)).coerceIn(0f, 1f)
    return lerpFloat(minAlpha, maxAlpha, t)
}

private fun snapToPixelCenter(value: Float): Float {
    return value.roundToInt().toFloat() + 0.5f
}

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
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var editUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var isGridEnabled by remember { mutableStateOf(false) }
    // 追加UIの状態管理: BottomSheet と Apply ダイアログ用
    var activeSheet by rememberSaveable { mutableStateOf(SheetType.None) }
    var showApplyDialog by rememberSaveable { mutableStateOf(false) }
    var showResizeDialog by rememberSaveable { mutableStateOf(false) }
    var applySource by rememberSaveable { mutableStateOf(ApplySource.Selection) }
    var applyDestinationLabel by rememberSaveable { mutableStateOf("Sprite (TODO)") }
    var applyOverwrite by rememberSaveable { mutableStateOf(true) }
    var applyPreserveAlpha by rememberSaveable { mutableStateOf(true) }
    var resizeAnchor by rememberSaveable { mutableStateOf(ResizeAnchor.TopLeft) }
    var lastToolOp by rememberSaveable(stateSaver = LastToolOpSaver) { mutableStateOf<LastToolOp?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val undoStack = remember { ArrayDeque<EditorSnapshot>() }
    val redoStack = remember { ArrayDeque<EditorSnapshot>() }
    var fillStatusText by remember { mutableStateOf("Fill: mode=-") }

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

    fun runResizeSelection(
        current: SpriteEditorState,
        anchor: ResizeAnchor,
        repeated: Boolean,
    ) {
        val resizeResult = resizeSelectionToMax96(
            current.bitmap,
            current.selection,
            anchor = anchor,
        )
        if (!resizeResult.applied) {
            scope.launch { showSnackbarMessage("Resize skipped (already <= 96px)") }
            return
        }
        pushUndoSnapshot(current, undoStack, redoStack)
        editorState = current.withBitmap(resizeResult.bitmap).withSelection(resizeResult.selection)
        lastToolOp = LastToolOp.ResizeToMax96(anchor)
        val message = if (repeated) "Repeated: Resize" else "Resize applied"
        scope.launch { showSnackbarMessage(message) }
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
        // selection移動はbitmap履歴に含めない（Undo/Redo対象外）
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
                    var pxStepBase by rememberSaveable { mutableStateOf(4) }
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
                        val gridRenderScale = remember(state, previewSize, displayScale) {
                            if (state == null) {
                                0f
                            } else if (previewSize.width == 0 || previewSize.height == 0) {
                                0f
                            } else if (state.bitmap.width <= 0 || state.bitmap.height <= 0) {
                                0f
                            } else {
                                val scaleX = previewSize.width.toFloat() / state.bitmap.width
                                val scaleY = previewSize.height.toFloat() / state.bitmap.height
                                min(scaleX, scaleY) * displayScale
                            }
                        }
                        LaunchedEffect(gridRenderScale) {
                            if (gridRenderScale <= 0f) return@LaunchedEffect
                            if (!isGridEnabled && gridRenderScale >= GRID_ON_SCALE) {
                                isGridEnabled = true
                            } else if (isGridEnabled && gridRenderScale < GRID_OFF_SCALE) {
                                isGridEnabled = false
                            }
                        }
                        fun clampPanOffset(
                            currentPanOffset: Offset,
                            nextDisplayScale: Float,
                        ): Offset {
                            val current = editorState
                            if (current == null) return Offset.Zero
                            if (previewSize.width == 0 || previewSize.height == 0) return Offset.Zero
                            if (current.bitmap.width <= 0 || current.bitmap.height <= 0) return Offset.Zero
                            val scaleX = previewSize.width.toFloat() / current.bitmap.width
                            val scaleY = previewSize.height.toFloat() / current.bitmap.height
                            val fitScale = min(scaleX, scaleY)
                            val renderScale = fitScale * nextDisplayScale
                            val destinationWidth = current.bitmap.width * renderScale
                            val destinationHeight = current.bitmap.height * renderScale
                            val viewWidth = previewSize.width.toFloat()
                            val viewHeight = previewSize.height.toFloat()
                            val clampedX = if (destinationWidth <= viewWidth) {
                                0f
                            } else {
                                val maxOffsetX = (destinationWidth - viewWidth) / 2f
                                currentPanOffset.x.coerceIn(-maxOffsetX, maxOffsetX)
                            }
                            val clampedY = if (destinationHeight <= viewHeight) {
                                0f
                            } else {
                                val maxOffsetY = (destinationHeight - viewHeight) / 2f
                                currentPanOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                            }
                            return Offset(clampedX, clampedY)
                        }
                        val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                            val nextScale = (displayScale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
                            displayScale = nextScale
                            val nextPan = panOffset + panChange
                            panOffset = clampPanOffset(nextPan, nextScale)
                        }
                        LaunchedEffect(previewSize, editorState) {
                            panOffset = clampPanOffset(panOffset, displayScale)
                        }
                        Box(
                            modifier = Modifier
                                // [非dp] 横: プレビュー の fillMaxWidth(制約)に関係
                                .fillMaxWidth()
                                // [dp] 上: プレビュー の余白(余白)に関係
                                .padding(top = 4.dp)
                                // [非dp] 縦: プレビュー の正方形レイアウト(制約)に関係
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    clip = true
                                    shape = RectangleShape
                                }
                                .onSizeChanged { size ->
                                    previewSize = size
                                    panOffset = clampPanOffset(panOffset, displayScale)
                                }
                                .transformable(state = transformableState)
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
                                val checkerLightColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CHECKER_LIGHT_ALPHA)
                                val checkerDarkColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = CHECKER_DARK_ALPHA)
                                val density = LocalDensity.current
                                val checkerBrush = remember(checkerLightColor, checkerDarkColor, density) {
                                    val cellSizePx = with(density) { CHECKER_CELL_SIZE.toPx() }
                                        .roundToInt()
                                        .coerceAtLeast(1)
                                    val bitmapSizePx = cellSizePx * 2
                                    val bitmap = Bitmap.createBitmap(bitmapSizePx, bitmapSizePx, Bitmap.Config.ARGB_8888)
                                    val canvas = AndroidCanvas(bitmap)
                                    val lightPaint = Paint().apply { color = checkerLightColor.toArgb() }
                                    val darkPaint = Paint().apply { color = checkerDarkColor.toArgb() }
                                    val cellSize = cellSizePx.toFloat()
                                    canvas.drawRect(0f, 0f, cellSize, cellSize, lightPaint)
                                    canvas.drawRect(cellSize, 0f, cellSize * 2f, cellSize, darkPaint)
                                    canvas.drawRect(0f, cellSize, cellSize, cellSize * 2f, darkPaint)
                                    canvas.drawRect(cellSize, cellSize, cellSize * 2f, cellSize * 2f, lightPaint)
                                    ShaderBrush(
                                        ImageShader(
                                            bitmap.asImageBitmap(),
                                            TileMode.Repeated,
                                            TileMode.Repeated,
                                        ),
                                    )
                                }
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    if (state.bitmap.width > 0 && state.bitmap.height > 0) {
                                        val scaleX = size.width / state.bitmap.width
                                        val scaleY = size.height / state.bitmap.height
                                        val fitScale = min(scaleX, scaleY)
                                        val renderScale = fitScale * displayScale
                                        val destinationWidth = state.bitmap.width * renderScale
                                        val destinationHeight = state.bitmap.height * renderScale
                                        val offsetXPx = ((size.width - destinationWidth) / 2f).roundToInt()
                                        val offsetYPx = ((size.height - destinationHeight) / 2f).roundToInt()
                                        val renderOffsetXPx = offsetXPx + panOffset.x.roundToInt()
                                        val renderOffsetYPx = offsetYPx + panOffset.y.roundToInt()
                                        val renderLeft = renderOffsetXPx.toFloat()
                                        val renderTop = renderOffsetYPx.toFloat()
                                        val renderRight = renderLeft + destinationWidth
                                        val renderBottom = renderTop + destinationHeight
                                        clipRect(renderLeft, renderTop, renderRight, renderBottom) {
                                            drawRect(
                                                brush = checkerBrush,
                                                topLeft = Offset(renderLeft, renderTop),
                                                size = Size(renderRight - renderLeft, renderBottom - renderTop),
                                            )
                                        }
                                    }
                                }
                                androidx.compose.foundation.Image(
                                    bitmap = state.imageBitmap,
                                    contentDescription = "Sprite Editor Preview",
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer {
                                            scaleX = displayScale
                                            scaleY = displayScale
                                            translationX = panOffset.x
                                            translationY = panOffset.y
                                        },
                                    contentScale = ContentScale.Fit,
                                )
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    if (state.bitmap.width > 0 && state.bitmap.height > 0) {
                                        val scaleX = size.width / state.bitmap.width
                                        val scaleY = size.height / state.bitmap.height
                                        val fitScale = min(scaleX, scaleY)
                                        val renderScale = fitScale * displayScale
                                        val destinationWidth = state.bitmap.width * renderScale
                                        val destinationHeight = state.bitmap.height * renderScale
                                        val offsetXPx = ((size.width - destinationWidth) / 2f).roundToInt()
                                        val offsetYPx = ((size.height - destinationHeight) / 2f).roundToInt()
                                        val renderOffsetXPx = offsetXPx + panOffset.x.roundToInt()
                                        val renderOffsetYPx = offsetYPx + panOffset.y.roundToInt()
                                        val renderLeft = renderOffsetXPx.toFloat()
                                        val renderTop = renderOffsetYPx.toFloat()
                                        val renderRight = renderLeft + destinationWidth
                                        val renderBottom = renderTop + destinationHeight
                                        if (isGridEnabled) {
                                            clipRect(renderLeft, renderTop, renderRight, renderBottom) {
                                                val stepPx = renderScale
                                                val minorAlpha = gridAlphaForScale(renderScale, 0.18f, 0.42f)
                                                val majorAlpha = gridAlphaForScale(renderScale, 0.30f, 0.60f)
                                                val minorBlackColor = Color.Black.copy(alpha = minorAlpha * 0.35f)
                                                val minorWhiteColor = Color.White.copy(alpha = minorAlpha)
                                                val majorBlackColor = Color.Black.copy(alpha = majorAlpha * 0.35f)
                                                val majorWhiteColor = Color.White.copy(alpha = majorAlpha)
                                                val majorStroke = if (renderScale >= 12f) 2f else 1.5f
                                                var lineX = renderLeft
                                                while (lineX <= renderRight) {
                                                    val snappedX = snapToPixelCenter(lineX)
                                                    drawLine(
                                                        color = minorBlackColor,
                                                        start = Offset(snappedX, renderTop),
                                                        end = Offset(snappedX, renderBottom),
                                                        strokeWidth = 1f,
                                                    )
                                                    drawLine(
                                                        color = minorWhiteColor,
                                                        start = Offset(snappedX, renderTop),
                                                        end = Offset(snappedX, renderBottom),
                                                        strokeWidth = 1f,
                                                    )
                                                    lineX += stepPx
                                                }
                                                var lineY = renderTop
                                                while (lineY <= renderBottom) {
                                                    val snappedY = snapToPixelCenter(lineY)
                                                    drawLine(
                                                        color = minorBlackColor,
                                                        start = Offset(renderLeft, snappedY),
                                                        end = Offset(renderRight, snappedY),
                                                        strokeWidth = 1f,
                                                    )
                                                    drawLine(
                                                        color = minorWhiteColor,
                                                        start = Offset(renderLeft, snappedY),
                                                        end = Offset(renderRight, snappedY),
                                                        strokeWidth = 1f,
                                                    )
                                                    lineY += stepPx
                                                }
                                                val majorStepPx = stepPx * GRID_MAJOR_STEP
                                                var majorX = renderLeft
                                                while (majorX <= renderRight) {
                                                    val snappedX = snapToPixelCenter(majorX)
                                                    drawLine(
                                                        color = majorBlackColor,
                                                        start = Offset(snappedX, renderTop),
                                                        end = Offset(snappedX, renderBottom),
                                                        strokeWidth = majorStroke,
                                                    )
                                                    drawLine(
                                                        color = majorWhiteColor,
                                                        start = Offset(snappedX, renderTop),
                                                        end = Offset(snappedX, renderBottom),
                                                        strokeWidth = majorStroke,
                                                    )
                                                    majorX += majorStepPx
                                                }
                                                var majorY = renderTop
                                                while (majorY <= renderBottom) {
                                                    val snappedY = snapToPixelCenter(majorY)
                                                    drawLine(
                                                        color = majorBlackColor,
                                                        start = Offset(renderLeft, snappedY),
                                                        end = Offset(renderRight, snappedY),
                                                        strokeWidth = majorStroke,
                                                    )
                                                    drawLine(
                                                        color = majorWhiteColor,
                                                        start = Offset(renderLeft, snappedY),
                                                        end = Offset(renderRight, snappedY),
                                                        strokeWidth = majorStroke,
                                                    )
                                                    majorY += majorStepPx
                                                }
                                            }
                                        }
                                        val outlineTopLeft = Offset(
                                            x = renderLeft + 0.5f,
                                            y = renderTop + 0.5f,
                                        )
                                        val outlineSize = Size(
                                            width = max(0f, destinationWidth - 1f),
                                            height = max(0f, destinationHeight - 1f),
                                        )
                                        drawRect(
                                            color = Color.Black.copy(alpha = 0.35f),
                                            topLeft = outlineTopLeft,
                                            size = outlineSize,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                                        )
                                        drawRect(
                                            color = Color.White.copy(alpha = 0.55f),
                                            topLeft = Offset(
                                                x = outlineTopLeft.x + 1f,
                                                y = outlineTopLeft.y + 1f,
                                            ),
                                            size = Size(
                                                width = max(0f, outlineSize.width - 2f),
                                                height = max(0f, outlineSize.height - 2f),
                                            ),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                                        )
                                        val copied = copiedSelection
                                        if (copied != null) {
                                            val copiedXPx = (copied.x * renderScale).roundToInt()
                                            val copiedYPx = (copied.y * renderScale).roundToInt()
                                            val copiedWPx = (copied.w * renderScale).roundToInt()
                                            val copiedHPx = (copied.h * renderScale).roundToInt()
                                            val copiedStrokePx = max(1, 3.dp.toPx().roundToInt())
                                            val copiedColor = Color.Cyan
                                            drawRect(
                                                color = copiedColor.copy(alpha = 0.35f),
                                                topLeft = Offset(
                                                    x = (renderOffsetXPx + copiedXPx).toFloat(),
                                                    y = (renderOffsetYPx + copiedYPx).toFloat(),
                                                ),
                                                size = Size(
                                                    width = copiedWPx.toFloat(),
                                                    height = copiedHPx.toFloat(),
                                                ),
                                            )
                                            drawRect(
                                                color = copiedColor,
                                                topLeft = Offset(
                                                    x = (renderOffsetXPx + copiedXPx).toFloat(),
                                                    y = (renderOffsetYPx + copiedYPx).toFloat(),
                                                ),
                                                size = Size(
                                                    width = copiedWPx.toFloat(),
                                                    height = copiedHPx.toFloat(),
                                                ),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = copiedStrokePx.toFloat()),
                                            )
                                        }
                                        val selectionXPx = (state.selection.x * renderScale).roundToInt()
                                        val selectionYPx = (state.selection.y * renderScale).roundToInt()
                                        val selectionWPx = (state.selection.w * renderScale).roundToInt()
                                        val selectionHPx = (state.selection.h * renderScale).roundToInt()
                                        val clipboardImage = state.clipboard?.let { ensureArgb8888(it).asImageBitmap() }
                                        if (clipboardImage != null) {
                                            drawImage(
                                                image = clipboardImage,
                                                srcOffset = IntOffset(0, 0),
                                                srcSize = IntSize(clipboardImage.width, clipboardImage.height),
                                                dstOffset = IntOffset(
                                                    x = renderOffsetXPx + selectionXPx,
                                                    y = renderOffsetYPx + selectionYPx,
                                                ),
                                                dstSize = IntSize(
                                                    width = selectionWPx,
                                                    height = selectionHPx,
                                                ),
                                                alpha = 0.78f,
                                                colorFilter = ColorFilter.tint(
                                                    color = Color(0xFF7FD7FF),
                                                    blendMode = BlendMode.SrcIn,
                                                ),
                                            )
                                        }
                                        val strokePx = max(1, 2.dp.toPx().roundToInt())
                                        drawRect(
                                            color = Color.Red,
                                            topLeft = Offset(
                                                x = (renderOffsetXPx + selectionXPx).toFloat(),
                                                y = (renderOffsetYPx + selectionYPx).toFloat(),
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
                            val statusLine3 = if (state == null) {
                                "移動: -"
                            } else {
                                "移動: ${pxStepBase}px"
                            }
                            Text(
                                text = statusLine1,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = statusLine2,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = fillStatusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("spriteEditorFillStatus"),
                                )
                            }
                            Text(
                                text = statusLine3,
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
                        val adjustedRepeatStepPx = repeatStepPx?.let { repeatStep ->
                            if (pxStepBase == 4) {
                                repeatStep
                            } else {
                                (repeatStep / 4).coerceAtLeast(1)
                            }
                        }
                        val step = adjustedRepeatStepPx ?: pxStepBase
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
                                            onClick = {
                                                moveMode = MoveMode.Px
                                                pxStepBase = if (pxStepBase == 4) 1 else 4
                                            },
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
                                                displayScale = 1f
                                                panOffset = Offset.Zero
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
                                item(span = { GridItemSpan(2) }) {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        StandardButton(
                                            label = "Repeat",
                                            testTag = "spriteEditorRepeat",
                                            onClick = {
                                                val current = editorState
                                                if (current == null) {
                                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                                } else {
                                                    val op = lastToolOp
                                                    if (op == null) {
                                                        scope.launch { showSnackbarMessage("No previous operation") }
                                                    } else {
                                                        when (op) {
                                                            LastToolOp.Grayscale -> {
                                                                pushUndoSnapshot(current, undoStack, redoStack)
                                                                val grayBitmap = toGrayscale(current.bitmap)
                                                                editorState = current.withBitmap(grayBitmap)
                                                                scope.launch { showSnackbarMessage("Repeated: Grayscale") }
                                                            }

                                                            LastToolOp.Outline -> {
                                                                pushUndoSnapshot(current, undoStack, redoStack)
                                                                val outlinedBitmap = addOuterOutline(current.bitmap)
                                                                editorState = current.withBitmap(outlinedBitmap)
                                                                scope.launch { showSnackbarMessage("Repeated: Outline") }
                                                            }

                                                            LastToolOp.Binarize -> {
                                                                pushUndoSnapshot(current, undoStack, redoStack)
                                                                val binarizedBitmap = toBinarize(current.bitmap)
                                                                editorState = current.withBitmap(binarizedBitmap)
                                                                scope.launch { showSnackbarMessage("Repeated: Binarize") }
                                                            }

                                                            LastToolOp.ClearBackground -> {
                                                                pushUndoSnapshot(current, undoStack, redoStack)
                                                                val clearedBitmap = clearEdgeConnectedBackground(current.bitmap)
                                                                editorState = current.withBitmap(clearedBitmap)
                                                                scope.launch { showSnackbarMessage("Repeated: Clear Background") }
                                                            }

                                                            LastToolOp.ClearRegion -> {
                                                                pushUndoSnapshot(current, undoStack, redoStack)
                                                                val clearedBitmap = clearConnectedRegionFromSelection(
                                                                    current.bitmap,
                                                                    current.selection,
                                                                )
                                                                editorState = current.withBitmap(clearedBitmap)
                                                                scope.launch { showSnackbarMessage("Repeated: Clear Region") }
                                                            }

                                                            LastToolOp.FillConnected -> {
                                                                val fillResult = fillConnectedToWhite(
                                                                    current.bitmap,
                                                                    current.selection,
                                                                )
                                                                fillStatusText = fillResult.debugText
                                                                when {
                                                                    fillResult.aborted -> {
                                                                        scope.launch { showSnackbarMessage("Fill aborted (too large)") }
                                                                    }

                                                                    fillResult.filled <= 0 -> {
                                                                        scope.launch { showSnackbarMessage("No target pixels in selection") }
                                                                    }

                                                                    else -> {
                                                                        pushUndoSnapshot(current, undoStack, redoStack)
                                                                        editorState = current.withBitmap(fillResult.bitmap)
                                                                        scope.launch { showSnackbarMessage("Repeated: Fill Connected") }
                                                                    }
                                                                }
                                                            }

                                                            LastToolOp.CenterContentInBox -> {
                                                                val contentBounds = findContentBoundsInRect(
                                                                    current.bitmap,
                                                                    current.selection,
                                                                )
                                                                if (contentBounds == null) {
                                                                    scope.launch { showSnackbarMessage("Repeated: No content in selection") }
                                                                } else {
                                                                    pushUndoSnapshot(current, undoStack, redoStack)
                                                                    val centeredBitmap = centerContentInRect(
                                                                        current.bitmap,
                                                                        current.selection,
                                                                    )
                                                                    editorState = current.withBitmap(centeredBitmap)
                                                                    scope.launch {
                                                                        showSnackbarMessage("Repeated: Center Content in Box")
                                                                    }
                                                                }
                                                            }

                                                            is LastToolOp.ResizeToMax96 -> {
                                                                runResizeSelection(
                                                                    current,
                                                                    anchor = lastToolOp.anchor,
                                                                    repeated = true,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        StandardButton(
                                            label = "More...",
                                            testTag = "spriteEditorMore",
                                            onClick = { activeSheet = SheetType.More },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        StandardButton(
                                            label = "Tools",
                                            testTag = "spriteEditorTools",
                                            onClick = { activeSheet = SheetType.Tools },
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

    if (activeSheet != SheetType.None) {
        val sheetTitle = if (activeSheet == SheetType.More) "More" else "Tools"
        data class SheetItem(
            val label: String,
            val testTag: String,
            val opensApplyDialog: Boolean = false,
        )
        val sheetItems = if (activeSheet == SheetType.More) {
            listOf(
                SheetItem(label = "Flip Copy", testTag = "spriteEditorSheetItemFlipCopy"),
                SheetItem(label = "Resize...", testTag = "spriteEditorSheetItemResize"),
                SheetItem(
                    label = "Apply to Sprite...",
                    testTag = "spriteEditorSheetItemApply",
                    opensApplyDialog = true,
                ),
            )
        } else {
            listOf(
                SheetItem(label = "Grayscale", testTag = "spriteEditorSheetItemGrayscale"),
                SheetItem(label = "Outline", testTag = "spriteEditorSheetItemOutline"),
                SheetItem(label = "Binarize", testTag = "spriteEditorSheetItemBinarize"),
                SheetItem(label = "Clear Background", testTag = "spriteEditorSheetItemClearBackground"),
                SheetItem(label = "Clear Region", testTag = "spriteEditorSheetItemClearRegion"),
                SheetItem(label = "Fill Connected", testTag = "spriteEditorSheetItemFillConnected"),
                SheetItem(
                    label = "Center Content in Box",
                    testTag = "spriteEditorSheetItemCenterContentInBox",
                ),
            )
        }
        ModalBottomSheet(
            onDismissRequest = { activeSheet = SheetType.None },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    // [dp] 全体: ボトムシート内容の最小余白(余白)に関係
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = sheetTitle,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(
                    modifier = Modifier
                        // [dp] 上下: タイトルと項目の間隔(間隔)に関係
                        .height(8.dp)
                )
                sheetItems.forEach { item ->
                    Button(
                        onClick = {
                            if (item.opensApplyDialog) {
                                activeSheet = SheetType.None
                                showApplyDialog = true
                            } else if (item.testTag == "spriteEditorSheetItemGrayscale") {
                                val current = editorState
                                if (current == null) {
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                } else {
                                    pushUndoSnapshot(current, undoStack, redoStack)
                                    val grayBitmap = toGrayscale(current.bitmap)
                                    editorState = current.withBitmap(grayBitmap)
                                    lastToolOp = LastToolOp.Grayscale
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("Grayscale applied") }
                                }
                            } else if (item.testTag == "spriteEditorSheetItemOutline") {
                                val current = editorState
                                if (current == null) {
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                } else {
                                    pushUndoSnapshot(current, undoStack, redoStack)
                                    val outlinedBitmap = addOuterOutline(current.bitmap)
                                    editorState = current.withBitmap(outlinedBitmap)
                                    lastToolOp = LastToolOp.Outline
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("Outline applied") }
                                }
                            } else if (item.testTag == "spriteEditorSheetItemBinarize") {
                                val current = editorState
                                if (current == null) {
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                } else {
                                    pushUndoSnapshot(current, undoStack, redoStack)
                                    val binarizedBitmap = toBinarize(current.bitmap)
                                    editorState = current.withBitmap(binarizedBitmap)
                                    lastToolOp = LastToolOp.Binarize
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("Binarize applied") }
                                }
                            } else if (item.testTag == "spriteEditorSheetItemClearBackground") {
                                val current = editorState
                                if (current == null) {
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                } else {
                                    pushUndoSnapshot(current, undoStack, redoStack)
                                    val clearedBitmap = clearEdgeConnectedBackground(current.bitmap)
                                    editorState = current.withBitmap(clearedBitmap)
                                    lastToolOp = LastToolOp.ClearBackground
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("Background cleared") }
                                }
                            } else if (item.testTag == "spriteEditorSheetItemClearRegion") {
                                val current = editorState
                                if (current == null) {
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                } else {
                                    pushUndoSnapshot(current, undoStack, redoStack)
                                    val clearedBitmap = clearConnectedRegionFromSelection(
                                        current.bitmap,
                                        current.selection,
                                    )
                                    editorState = current.withBitmap(clearedBitmap)
                                    lastToolOp = LastToolOp.ClearRegion
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("Region cleared") }
                                }
                            } else if (item.testTag == "spriteEditorSheetItemFillConnected") {
                                val current = editorState
                                if (current == null) {
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                } else {
                                    val fillResult = fillConnectedToWhite(
                                        current.bitmap,
                                        current.selection,
                                    )
                                    fillStatusText = fillResult.debugText
                                    activeSheet = SheetType.None
                                    when {
                                        fillResult.aborted -> {
                                            scope.launch { showSnackbarMessage("Fill aborted (too large)") }
                                        }

                                        fillResult.filled <= 0 -> {
                                            scope.launch { showSnackbarMessage("No target pixels in selection") }
                                        }

                                        else -> {
                                            pushUndoSnapshot(current, undoStack, redoStack)
                                            editorState = current.withBitmap(fillResult.bitmap)
                                            lastToolOp = LastToolOp.FillConnected
                                            scope.launch { showSnackbarMessage("Fill Connected applied") }
                                        }
                                    }
                                }
                            } else if (item.testTag == "spriteEditorSheetItemCenterContentInBox") {
                                val current = editorState
                                if (current == null) {
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                } else {
                                    val contentBounds = findContentBoundsInRect(current.bitmap, current.selection)
                                    activeSheet = SheetType.None
                                    if (contentBounds == null) {
                                        scope.launch { showSnackbarMessage("No content in selection") }
                                    } else {
                                        pushUndoSnapshot(current, undoStack, redoStack)
                                        val centeredBitmap = centerContentInRect(current.bitmap, current.selection)
                                        editorState = current.withBitmap(centeredBitmap)
                                        lastToolOp = LastToolOp.CenterContentInBox
                                        scope.launch { showSnackbarMessage("Centered content in selection") }
                                    }
                                }
                            } else if (item.testTag == "spriteEditorSheetItemResize") {
                                activeSheet = SheetType.None
                                showResizeDialog = true
                            } else {
                                activeSheet = SheetType.None
                                scope.launch { showSnackbarMessage("TODO: ${item.label}") }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            // [dp] 縦: 見た目32dpを維持しつつタップ領域を確保
                            .height(32.dp)
                            .heightIn(min = 48.dp)
                            .testTag(item.testTag),
                        // [dp] 左右: ボトムシート内ボタンの余白(余白)に関係
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(item.label)
                    }
                    Spacer(
                        modifier = Modifier
                            // [dp] 上下: 項目間の間隔(間隔)に関係
                            .height(6.dp)
                    )
                }
            }
        }
    }

    if (showApplyDialog) {
        AlertDialog(
            onDismissRequest = { showApplyDialog = false },
            title = { Text("Apply to Sprite") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Applies the current image (or selection) to a sprite asset.")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Source")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = applySource == ApplySource.Selection,
                                    onClick = { applySource = ApplySource.Selection },
                                    role = Role.RadioButton,
                                )
                                .testTag("spriteEditorApplySourceSelection"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = applySource == ApplySource.Selection,
                                onClick = null,
                            )
                            Text("Selection")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = applySource == ApplySource.FullImage,
                                    onClick = { applySource = ApplySource.FullImage },
                                    role = Role.RadioButton,
                                )
                                .testTag("spriteEditorApplySourceFull"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = applySource == ApplySource.FullImage,
                                onClick = null,
                            )
                            Text("Full Image")
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Destination")
                        OutlinedTextField(
                            value = applyDestinationLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("spriteEditorApplyDestination")
                                .clickable {
                                    scope.launch {
                                        showSnackbarMessage("TODO: Choose destination sprite")
                                    }
                                },
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Options")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("spriteEditorApplyOverwrite"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = applyOverwrite,
                                onCheckedChange = { applyOverwrite = it },
                            )
                            Text("Overwrite existing")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("spriteEditorApplyPreserveAlpha"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = applyPreserveAlpha,
                                onCheckedChange = { applyPreserveAlpha = it },
                            )
                            Text("Preserve transparency")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showApplyDialog = false
                        scope.launch {
                            showSnackbarMessage(
                                "TODO: Apply to Sprite (Source=${applySource.label}, " +
                                    "Destination=$applyDestinationLabel, " +
                                    "Overwrite=$applyOverwrite, PreserveAlpha=$applyPreserveAlpha)"
                            )
                        }
                    },
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("spriteEditorApplyConfirm"),
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showApplyDialog = false },
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("spriteEditorApplyCancel"),
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("spriteEditorApplyDialog"),
        )
    }

    if (showResizeDialog) {
        AlertDialog(
            onDismissRequest = { showResizeDialog = false },
            title = { Text("Resize") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Shrink selection to max 96px (keeps aspect ratio).")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Anchor")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = resizeAnchor == ResizeAnchor.TopLeft,
                                    onClick = { resizeAnchor = ResizeAnchor.TopLeft },
                                    role = Role.RadioButton,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = resizeAnchor == ResizeAnchor.TopLeft,
                                onClick = null,
                            )
                            Text("TopLeft")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = resizeAnchor == ResizeAnchor.Center,
                                    onClick = { resizeAnchor = ResizeAnchor.Center },
                                    role = Role.RadioButton,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = resizeAnchor == ResizeAnchor.Center,
                                onClick = null,
                            )
                            Text("Center")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResizeDialog = false
                        val current = editorState
                        if (current == null) {
                            scope.launch { showSnackbarMessage("No sprite loaded") }
                        } else {
                            runResizeSelection(
                                current,
                                anchor = resizeAnchor,
                                repeated = false,
                            )
                        }
                    },
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showResizeDialog = false },
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("Cancel")
                }
            },
        )
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
private const val MIN_SCALE = 0.5f
private const val MAX_SCALE = 16f
