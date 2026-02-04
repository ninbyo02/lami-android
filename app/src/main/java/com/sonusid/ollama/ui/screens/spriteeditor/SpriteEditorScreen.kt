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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
    var editMode by rememberSaveable { mutableStateOf(EditMode.SPRITE_96) }
    // 追加UIの状態管理: BottomSheet と Apply ダイアログ用
    var activeSheet by rememberSaveable { mutableStateOf(SheetType.None) }
    var showApplyDialog by rememberSaveable { mutableStateOf(false) }
    var applySource by rememberSaveable { mutableStateOf(ApplySource.Selection) }
    var applyDestinationLabel by rememberSaveable { mutableStateOf("Sprite (TODO)") }
    var applyOverwrite by rememberSaveable { mutableStateOf(true) }
    var applyPreserveAlpha by rememberSaveable { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState()
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
            editorState = createInitialEditorState(safeBitmap, editMode)
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
            val applyRect = computeApplyRect(nextSelection, editMode, safeBitmap.width, safeBitmap.height)
            val workingBitmap = copyRect(safeBitmap, applyRect)
            editorState = current.copy(
                bitmap = safeBitmap,
                imageBitmap = safeBitmap.asImageBitmap(),
                selection = nextSelection,
                workingBitmap = workingBitmap,
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

    fun computeApplyRectForState(state: SpriteEditorState): RectPx {
        return computeApplyRect(state.selection, editMode, state.bitmap.width, state.bitmap.height)
    }

    fun rebuildWorkingBitmap(
        state: SpriteEditorState,
        applyRect: RectPx = computeApplyRectForState(state),
    ): SpriteEditorState {
        val workingBitmap = copyRect(state.bitmap, applyRect)
        return state.copy(workingBitmap = workingBitmap)
    }

    fun updateSelectionWithMode(state: SpriteEditorState, nextSelection: RectPx): SpriteEditorState {
        val normalized = rectNormalizeClamp(nextSelection, state.bitmap.width, state.bitmap.height)
        val applyRect = computeApplyRect(normalized, editMode, state.bitmap.width, state.bitmap.height)
        val workingBitmap = copyRect(state.bitmap, applyRect)
        return state.copy(
            selection = normalized,
            widthInput = normalized.w.toString(),
            heightInput = normalized.h.toString(),
            workingBitmap = workingBitmap,
        )
    }

    fun ensureWorkingBitmap(
        state: SpriteEditorState,
        applyRect: RectPx,
    ): Bitmap {
        val working = state.workingBitmap
        return if (working.width == applyRect.w && working.height == applyRect.h) {
            working
        } else {
            copyRect(state.bitmap, applyRect)
        }
    }

    fun moveSelection(dx: Int, dy: Int) {
        val current = editorState ?: return
        // selection移動はbitmap履歴に含めない（Undo/Redo対象外）
        updateState { state ->
            val moved = state.selection.moveBy(dx, dy)
            updateSelectionWithMode(state, moved)
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
                                        val applyRect = computeApplyRect(
                                            state.selection,
                                            editMode,
                                            state.bitmap.width,
                                            state.bitmap.height,
                                        )
                                        val applyXPx = (applyRect.x * renderScale).roundToInt()
                                        val applyYPx = (applyRect.y * renderScale).roundToInt()
                                        val applyWPx = (applyRect.w * renderScale).roundToInt()
                                        val applyHPx = (applyRect.h * renderScale).roundToInt()
                                        val strokePx = max(1, 2.dp.toPx().roundToInt())
                                        drawRect(
                                            color = Color.Red,
                                            topLeft = Offset(
                                                x = (renderOffsetXPx + applyXPx).toFloat(),
                                                y = (renderOffsetYPx + applyYPx).toFloat(),
                                            ),
                                            size = Size(
                                                width = applyWPx.toFloat(),
                                                height = applyHPx.toFloat(),
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
                            val modeLine = if (state == null) {
                                "Mode: -"
                            } else {
                                "Mode: ${editMode.label}"
                            }
                            val applyRect = state?.let { computeApplyRectForState(it) }
                            val applyLine = if (applyRect == null) {
                                "Apply rect: (-, -, -, -)"
                            } else {
                                "Apply rect: (${applyRect.x}, ${applyRect.y}, ${applyRect.w}, ${applyRect.h})"
                            }
                            Text(
                                text = statusLine1,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = modeLine,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("spriteEditorModeLabel"),
                            )
                            Text(
                                text = applyLine,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("spriteEditorApplyRectText"),
                            )
                            Spacer(
                                modifier = Modifier
                                    // [dp] 上: モードボタン群の最小余白(余白)に関係
                                    .height(4.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                @Composable
                                fun modeButtonColors(selected: Boolean) =
                                    ButtonDefaults.buttonColors(
                                        containerColor = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        contentColor = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                val modeButtonModifier = Modifier
                                    .height(32.dp)
                                    .heightIn(min = 48.dp)
                                Button(
                                    onClick = {
                                        editMode = EditMode.BOX_32
                                        updateState { state -> rebuildWorkingBitmap(state) }
                                    },
                                    modifier = modeButtonModifier
                                        .weight(1f)
                                        .testTag("spriteEditorMode32"),
                                    // [dp] 左右: モードボタン内側の余白(余白)に関係
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    shape = RoundedCornerShape(999.dp),
                                    colors = modeButtonColors(editMode == EditMode.BOX_32),
                                    border = if (editMode == EditMode.BOX_32) null else ButtonDefaults.outlinedButtonBorder,
                                ) {
                                    Text("32x32", maxLines = 1)
                                }
                                Button(
                                    onClick = {
                                        editMode = EditMode.SPRITE_96
                                        updateState { state -> rebuildWorkingBitmap(state) }
                                    },
                                    modifier = modeButtonModifier
                                        .weight(1f)
                                        .testTag("spriteEditorMode96"),
                                    // [dp] 左右: モードボタン内側の余白(余白)に関係
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    shape = RoundedCornerShape(999.dp),
                                    colors = modeButtonColors(editMode == EditMode.SPRITE_96),
                                    border = if (editMode == EditMode.SPRITE_96) null else ButtonDefaults.outlinedButtonBorder,
                                ) {
                                    Text("96x96", maxLines = 1)
                                }
                                Button(
                                    onClick = {
                                        editMode = EditMode.BLOCK_288
                                        updateState { state -> rebuildWorkingBitmap(state) }
                                    },
                                    modifier = modeButtonModifier
                                        .weight(1f)
                                        .testTag("spriteEditorMode288"),
                                    // [dp] 左右: モードボタン内側の余白(余白)に関係
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    shape = RoundedCornerShape(999.dp),
                                    colors = modeButtonColors(editMode == EditMode.BLOCK_288),
                                    border = if (editMode == EditMode.BLOCK_288) null else ButtonDefaults.outlinedButtonBorder,
                                ) {
                                    Text("288x288", maxLines = 1)
                                }
                            }
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
                                                    val applyRect = computeApplyRect(
                                                        nextSelection,
                                                        editMode,
                                                        normalized.width,
                                                        normalized.height,
                                                    )
                                                    val workingBitmap = copyRect(normalized, applyRect)
                                                    current.copy(
                                                        bitmap = normalized,
                                                        imageBitmap = normalized.asImageBitmap(),
                                                        selection = nextSelection,
                                                        workingBitmap = workingBitmap,
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
                                                        val applyRect = computeApplyRectForState(current)
                                                        val workingBitmap = copyRect(pasted, applyRect)
                                                        current.copy(
                                                            bitmap = pasted,
                                                            imageBitmap = pasted.asImageBitmap(),
                                                            workingBitmap = workingBitmap,
                                                            clipboard = null,
                                                        )
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
                                                    editorState = current.applySnapshot(snapshot, editMode)
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
                                                    editorState = current.applySnapshot(snapshot, editMode)
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
                                                    val applyRect = computeApplyRectForState(current)
                                                    val clearedBitmap = clearTransparent(current.bitmap, applyRect)
                                                    val clearedWorking = copyRect(clearedBitmap, applyRect)
                                                    current.copy(
                                                        bitmap = clearedBitmap,
                                                        imageBitmap = clearedBitmap.asImageBitmap(),
                                                        workingBitmap = clearedWorking,
                                                    )
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
                                                    val applyRect = computeApplyRectForState(current)
                                                    val workingBitmap = ensureWorkingBitmap(current, applyRect)
                                                    val filledWorking = fillBlack(
                                                        workingBitmap,
                                                        RectPx.of(0, 0, workingBitmap.width, workingBitmap.height),
                                                    )
                                                    val applied = paste(
                                                        current.bitmap,
                                                        filledWorking,
                                                        applyRect.x,
                                                        applyRect.y,
                                                    )
                                                    current.copy(
                                                        bitmap = applied,
                                                        imageBitmap = applied.asImageBitmap(),
                                                        workingBitmap = filledWorking,
                                                    )
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
                                item(span = { GridItemSpan(2) }) {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        StandardButton(
                                            label = "Apply to Sprite",
                                            testTag = "spriteEditorApply",
                                            onClick = { showApplyDialog = true },
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
                            statusContent(Modifier.fillMaxWidth())
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
                                statusContent(Modifier.fillMaxWidth())
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
                            activeSheet = SheetType.None
                            if (item.opensApplyDialog) {
                                showApplyDialog = true
                            } else {
                                when (item.label) {
                                    "Grayscale" -> {
                                        val current = editorState
                                        if (current == null) {
                                            scope.launch { showSnackbarMessage("No image loaded") }
                                        } else {
                                            scope.launch {
                                                val applyRect = computeApplyRectForState(current)
                                                val workingBitmap = ensureWorkingBitmap(current, applyRect)
                                                pushUndoSnapshot(current, undoStack, redoStack)
                                                val grayscaleBitmap = runCatching {
                                                    withContext(Dispatchers.Default) {
                                                        toGrayscale(workingBitmap)
                                                    }
                                                }.getOrElse {
                                                    showSnackbarMessage("Failed to apply Grayscale")
                                                    return@launch
                                                }
                                                val applied = paste(current.bitmap, grayscaleBitmap, applyRect.x, applyRect.y)
                                                updateState { state ->
                                                    state.copy(
                                                        bitmap = applied,
                                                        imageBitmap = applied.asImageBitmap(),
                                                        workingBitmap = grayscaleBitmap,
                                                    )
                                                }
                                                showSnackbarMessage("Applied: Grayscale")
                                            }
                                        }
                                    }
                                    else -> scope.launch { showSnackbarMessage("TODO: ${item.label}") }
                                }
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
        val dialogState = editorState
        val dialogApplyRect = dialogState?.let { computeApplyRectForState(it) }
        AlertDialog(
            onDismissRequest = { showApplyDialog = false },
            title = { Text("Apply to Sprite") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Applies the current image (or selection) to a sprite asset.")
                    Text(
                        text = "Mode: ${editMode.label}",
                        modifier = Modifier.testTag("spriteEditorApplyModeText"),
                    )
                    Text(
                        text = if (dialogApplyRect == null) {
                            "Apply rect: (-, -, -, -)"
                        } else {
                            "Apply rect: (${dialogApplyRect.x}, ${dialogApplyRect.y}, " +
                                "${dialogApplyRect.w}, ${dialogApplyRect.h})"
                        },
                        modifier = Modifier.testTag("spriteEditorApplyRectDialogText"),
                    )
                    Text("This will overwrite the target rectangle.")
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
                            val current = editorState
                            if (current == null) {
                                showSnackbarMessage("No image loaded")
                                return@launch
                            }
                            val applyRect = computeApplyRectForState(current)
                            val workingBitmap = ensureWorkingBitmap(current, applyRect)
                            pushUndoSnapshot(current, undoStack, redoStack)
                            val applied = paste(current.bitmap, workingBitmap, applyRect.x, applyRect.y)
                            editorState = current.copy(
                                bitmap = applied,
                                imageBitmap = applied.asImageBitmap(),
                                workingBitmap = workingBitmap,
                            )
                            showSnackbarMessage(
                                "Applied (${editMode.label}) to (${applyRect.x}, ${applyRect.y}, " +
                                    "${applyRect.w}, ${applyRect.h})"
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
}

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

private fun SpriteEditorState.applySnapshot(snapshot: EditorSnapshot, editMode: EditMode): SpriteEditorState {
    val normalized = rectNormalizeClamp(snapshot.selection, snapshot.bitmap.width, snapshot.bitmap.height)
    val restoredBitmap = ensureArgb8888(snapshot.bitmap)
    val applyRect = computeApplyRect(normalized, editMode, restoredBitmap.width, restoredBitmap.height)
    val workingBitmap = copyRect(restoredBitmap, applyRect)
    return copy(
        bitmap = restoredBitmap,
        imageBitmap = restoredBitmap.asImageBitmap(),
        selection = normalized,
        workingBitmap = workingBitmap,
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
