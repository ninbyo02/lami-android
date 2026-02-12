package com.sonusid.ollama.ui.screens.spriteeditor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavController
import com.sonusid.ollama.R
import com.sonusid.ollama.ui.common.LocalAppSnackbarHostState
import com.sonusid.ollama.ui.screens.settings.SettingsPreferences
import com.sonusid.ollama.ui.common.PROJECT_SNACKBAR_SHORT_MS
import com.sonusid.ollama.sprite.compositePreserveTransparency
import com.sonusid.ollama.ui.screens.settings.SpriteSettingsSessionSpriteOverride
import com.sonusid.ollama.ui.components.rememberLamiEditorSpriteBackdropColor
import com.sonusid.ollama.ui.screens.spriteeditor.FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
private val APPLY_DIALOG_COMMENT_MIN_HEIGHT = 64.dp
private val APPLY_DIALOG_COMMENT_SLOT_SPACING = 4.dp
private val MOVE_STATUS_FIXED_WIDTH = 72.dp

private enum class SheetType {
    None,
    More,
    Tools,
}

private enum class ApplySource(val label: String) {
    Selection("Selection"),
    FullImage("Full Image"),
}

private enum class ApplyDialogCommentKind {
    None,
    Info,
    Warn,
    Error,
}

private sealed class LastToolOp {
    data object Grayscale : LastToolOp()
    data object Outline : LastToolOp()
    data object Binarize : LastToolOp()
    data object ClearBackground : LastToolOp()
    data object ClearRegion : LastToolOp()
    data object FillConnected : LastToolOp()
    data object CenterContentInBox : LastToolOp()
    data class ResizeToMax96(
        val anchor: ResizeAnchor,
        val stepFactor: Float,
        val downscaleMode: ResizeDownscaleMode,
        val pixelArtMethod: PixelArtStableMethod,
    ) : LastToolOp()
}

private val LastToolOpSaver = Saver<LastToolOp?, List<String>>(
    save = { op ->
        when (op) {
            null -> listOf("None")
            LastToolOp.Grayscale -> listOf("Grayscale")
            LastToolOp.Outline -> listOf("Outline")
            LastToolOp.Binarize -> listOf("Binarize")
            LastToolOp.ClearBackground -> listOf("ClearBackground")
            LastToolOp.ClearRegion -> listOf("ClearRegion")
            LastToolOp.FillConnected -> listOf("FillConnected")
            LastToolOp.CenterContentInBox -> listOf("CenterContentInBox")
            is LastToolOp.ResizeToMax96 -> listOf(
                "ResizeToMax96",
                op.anchor.name,
                op.stepFactor.toString(),
                op.downscaleMode.name,
                op.pixelArtMethod.name,
            )
        }
    },
    restore = { data ->
        val type = data.firstOrNull() ?: "None"
        when (type) {
            "None" -> null
            "Grayscale" -> LastToolOp.Grayscale
            "Outline" -> LastToolOp.Outline
            "Binarize" -> LastToolOp.Binarize
            "ClearBackground" -> LastToolOp.ClearBackground
            "ClearRegion" -> LastToolOp.ClearRegion
            "FillConnected" -> LastToolOp.FillConnected
            "CenterContentInBox" -> LastToolOp.CenterContentInBox
            "ResizeToMax96" -> {
                val anchorName = data.getOrNull(1) ?: ResizeAnchor.TopLeft.name
                val anchor = try {
                    ResizeAnchor.valueOf(anchorName)
                } catch (_: IllegalArgumentException) {
                    ResizeAnchor.TopLeft
                }
                val stepFactor = data.getOrNull(2)?.toFloatOrNull() ?: 0.5f
                val modeName = data.getOrNull(3) ?: ResizeDownscaleMode.PixelArtStable.name
                val downscaleMode = try {
                    ResizeDownscaleMode.valueOf(modeName)
                } catch (_: IllegalArgumentException) {
                    ResizeDownscaleMode.PixelArtStable
                }
                val methodName = data.getOrNull(4) ?: PixelArtStableMethod.CenterSample.name
                val pixelArtMethod = try {
                    PixelArtStableMethod.valueOf(methodName)
                } catch (_: IllegalArgumentException) {
                    PixelArtStableMethod.CenterSample
                }
                LastToolOp.ResizeToMax96(anchor, stepFactor, downscaleMode, pixelArtMethod)
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
    val snackbarHostState = LocalAppSnackbarHostState.current
    val settingsPreferences = remember(context.applicationContext) {
        SettingsPreferences(context.applicationContext)
    }
    val editorBackdropColor = rememberLamiEditorSpriteBackdropColor()
    var editorState by remember { mutableStateOf<SpriteEditorState?>(null) }
    var copiedSelection by remember { mutableStateOf<RectPx?>(null) }
    var copiedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var displayScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var editUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var isGridEnabled by remember { mutableStateOf(false) }
    var isDirty by rememberSaveable { mutableStateOf(false) }
    var showExitConfirmDialog by rememberSaveable { mutableStateOf(false) }
    // 追加UIの状態管理: BottomSheet と Apply ダイアログ用
    var activeSheet by rememberSaveable { mutableStateOf(SheetType.None) }
    var showApplyDialog by rememberSaveable { mutableStateOf(false) }
    var showResizeDialog by rememberSaveable { mutableStateOf(false) }
    var showCanvasSizeDialog by rememberSaveable { mutableStateOf(false) }
    var applySource by rememberSaveable { mutableStateOf(ApplySource.FullImage) }
    var applyOverwrite by rememberSaveable { mutableStateOf(true) }
    var applyPreserveAlpha by rememberSaveable { mutableStateOf(false) }
    var applyDialogComment by rememberSaveable { mutableStateOf("") }
    var applyDialogCommentKind by rememberSaveable { mutableStateOf(ApplyDialogCommentKind.None) }
    var resizeAnchor by rememberSaveable { mutableStateOf(ResizeAnchor.TopLeft) }
    var resizeStepFactor by rememberSaveable { mutableStateOf(0.5f) }
    var resizeDownscaleMode by rememberSaveable { mutableStateOf(ResizeDownscaleMode.PixelArtStable) }
    var resizePixelArtMethod by rememberSaveable { mutableStateOf(PixelArtStableMethod.CenterSample) }
    var canvasWidthInput by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var canvasHeightInput by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var canvasAnchor by rememberSaveable { mutableStateOf(ResizeAnchor.TopLeft) }
    var lastToolOp by rememberSaveable(stateSaver = LastToolOpSaver) { mutableStateOf<LastToolOp?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val undoStack = remember { ArrayDeque<EditorSnapshot>() }
    val redoStack = remember { ArrayDeque<EditorSnapshot>() }
    var fillStatusText by remember { mutableStateOf("Fill: mode=-") }
    var lastFillConnectedSeedType by remember { mutableStateOf<FillConnectedSeedType?>(null) }

    suspend fun showSnackbarMessage(
        message: String,
        duration: SnackbarDuration = SnackbarDuration.Short,
    ) {
        snackbarHostState.currentSnackbarData?.dismiss()
        if (duration == SnackbarDuration.Short) {
            coroutineScope {
                val dismissJob = launch {
                    delay(PROJECT_SNACKBAR_SHORT_MS)
                    snackbarHostState.currentSnackbarData?.dismiss()
                }
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Indefinite,
                )
                dismissJob.cancel()
            }
            return
        }
        snackbarHostState.showSnackbar(
            message = message,
            duration = duration,
        )
    }

    fun runResizeSelection(
        current: SpriteEditorState,
        anchor: ResizeAnchor,
        stepFactor: Float,
        downscaleMode: ResizeDownscaleMode,
        pixelArtMethod: PixelArtStableMethod,
        repeated: Boolean,
    ) {
        val resizeResult = resizeSelectionToMax96(
            current.bitmap,
            current.selection,
            anchor = anchor,
            stepFactor = stepFactor,
            downscaleMode = downscaleMode,
            pixelArtMethod = pixelArtMethod,
        )
        if (!resizeResult.applied) {
            scope.launch { showSnackbarMessage("Resize skipped (already <= 96px)") }
            return
        }
        pushUndoSnapshot(current, undoStack, redoStack)
        editorState = current.withBitmap(resizeResult.bitmap).withSelection(resizeResult.selection)
        isDirty = true
        lastToolOp = LastToolOp.ResizeToMax96(anchor, stepFactor, downscaleMode, pixelArtMethod)
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

    suspend fun runSave(): Boolean {
        val current = editorState ?: return false
        val result = runCatching { saveInternalAutosave(current) }
        return if (result.getOrDefault(false)) {
            showSnackbarMessage("保存しました")
            isDirty = false
            true
        } else {
            showSnackbarMessage("保存に失敗しました")
            false
        }
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

    fun closeEditor() {
        navController.popBackStack()
    }

    fun requestCloseEditor() {
        if (isDirty) {
            showExitConfirmDialog = true
        } else {
            closeEditor()
        }
    }

    BackHandler(enabled = isDirty) {
        requestCloseEditor()
    }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemBarInsets = WindowInsets.systemBars
    val scaffoldInsets = with(density) {
        WindowInsets(
            left = systemBarInsets.getLeft(this, layoutDirection),
            top = 0,
            right = systemBarInsets.getRight(this, layoutDirection),
            bottom = 0,
        )
    }

    Scaffold(
        contentWindowInsets = scaffoldInsets,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .fillMaxHeight()
                            .wrapContentHeight(Alignment.CenterVertically),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        IconButton(onClick = { requestCloseEditor() }) {
                            Icon(
                                painter = painterResource(R.drawable.back),
                                contentDescription = "exit",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .wrapContentHeight(Alignment.CenterVertically)
                    ) {
                        Text("Sprite Editor")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
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
                    val buttonHeight = SpriteEditorButtonHeight
                    val buttonMinHeight = SpriteEditorButtonMinHeight
                    val buttonPadding = SpriteEditorButtonPadding
                    val pillShape = SpriteEditorPillShape
                    var moveMode by remember { mutableStateOf(MoveMode.Box) }
                    var pxStepBase by rememberSaveable { mutableStateOf(4) }
                    var widthText by rememberSaveable(state?.widthInput, stateSaver = TextFieldValue.Saver) {
                        val initial = state?.widthInput.orEmpty()
                        mutableStateOf(
                            TextFieldValue(
                                text = initial,
                                selection = TextRange(initial.length),
                            ),
                        )
                    }
                    var heightText by rememberSaveable(state?.heightInput, stateSaver = TextFieldValue.Saver) {
                        val initial = state?.heightInput.orEmpty()
                        mutableStateOf(
                            TextFieldValue(
                                text = initial,
                                selection = TextRange(initial.length),
                            ),
                        )
                    }
                    val inputContent: @Composable (Modifier) -> Unit = { modifier ->
                        Row(
                            modifier = modifier,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedTextField(
                                value = widthText,
                                onValueChange = { input: TextFieldValue ->
                                    val maxWidth = state?.bitmap?.width ?: 4096
                                    val clamped = clampPxFieldValue(widthText, input, maxWidth)
                                    widthText = clamped
                                    val sanitizedText = clamped.text
                                    updateState { current ->
                                        val updated = current.copy(widthInput = sanitizedText)
                                        val width = sanitizedText.toIntOrNull()
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
                                onValueChange = { input: TextFieldValue ->
                                    val maxHeight = state?.bitmap?.height ?: 4096
                                    val clamped = clampPxFieldValue(heightText, input, maxHeight)
                                    heightText = clamped
                                    val sanitizedText = clamped.text
                                    updateState { current ->
                                        val updated = current.copy(heightInput = sanitizedText)
                                        val height = sanitizedText.toIntOrNull()
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
                    val realtimeSeedTypeChar by remember(editorState, state?.selection) {
                        derivedStateOf {
                            val current = editorState ?: return@derivedStateOf '-'
                            val selection = current.selection
                            val seedX = selection.x
                            val seedY = selection.y
                            val bitmap = current.bitmap
                            if (seedX !in 0 until bitmap.width || seedY !in 0 until bitmap.height) {
                                return@derivedStateOf '-'
                            }
                            val seedPixel = bitmap.getPixel(seedX, seedY)
                            val alpha = (seedPixel ushr 24) and 0xFF
                            val red = (seedPixel ushr 16) and 0xFF
                            val green = (seedPixel ushr 8) and 0xFF
                            val blue = seedPixel and 0xFF
                            when {
                                alpha < FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD -> 'T'
                                red <= 16 && green <= 16 && blue <= 16 -> 'B'
                                red >= 239 && green >= 239 && blue >= 239 -> 'W'
                                else -> 'O'
                            }
                        }
                    }
                    LaunchedEffect(state?.selection) {
                        lastFillConnectedSeedType = null
                    }
                    fun seedWordFromTypeChar(seedTypeChar: Char): String = when (seedTypeChar) {
                        'T' -> "Transparent"
                        'B' -> "Black"
                        'W' -> "White"
                        'O' -> "Other"
                        else -> "-"
                    }
                    fun seedWordFromFillConnectedSeedType(seedType: FillConnectedSeedType): String = when (seedType) {
                        FillConnectedSeedType.Transparent -> "Transparent"
                        FillConnectedSeedType.Black -> "Black"
                        FillConnectedSeedType.White -> "White"
                        FillConnectedSeedType.Other -> "Other"
                        FillConnectedSeedType.None -> "None"
                    }
                    val seedWord = lastFillConnectedSeedType?.let { seedWordFromFillConnectedSeedType(it) }
                        ?: seedWordFromTypeChar(realtimeSeedTypeChar)
                    val statusContent: @Composable (Modifier) -> Unit = { modifier ->
                        Column(
                            modifier = modifier
                                // [dp] 上下: ステータス の余白(余白)に関係
                                .padding(vertical = 2.dp)
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
                            val moveStatusText = if (state == null) {
                                "移動: -"
                            } else if (moveMode == MoveMode.Box) {
                                "移動: 1box"
                            } else {
                                "移動: ${pxStepBase}px"
                            }
                            val statusTextStyle = MaterialTheme.typography.labelMedium
                            Text(
                                text = statusLine1,
                                style = statusTextStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = statusLine2,
                                style = statusTextStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    // [dp] 横: 「移動: 1box」が収まる固定幅で Seed 表示の開始位置を安定化
                                    modifier = Modifier.width(MOVE_STATUS_FIXED_WIDTH)
                                ) {
                                    Text(
                                        text = moveStatusText,
                                        style = statusTextStyle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                // [dp] 横: 移動ステータスと Seed 表示の最小間隔
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Seed: $seedWord",
                                    style = statusTextStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.testTag("spriteEditorSeedStatus"),
                                )
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
                                        SpriteEditorStandardButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("spriteEditorSave"),
                                            label = "Save",
                                            onClick = {
                                                scope.launch {
                                                    runSave()
                                                }
                                            },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        SpriteEditorStandardButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("spriteEditorReset"),
                                            label = "Reset",
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
                                        SpriteEditorStandardButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("spriteEditorCopy"),
                                            label = "Copy",
                                            onClick = {
                                                updateState { current ->
                                                    val safeSelection = rectNormalizeClamp(
                                                        current.selection,
                                                        current.bitmap.width,
                                                        current.bitmap.height,
                                                    )
                                                    val clip = ensureArgb8888(copyRect(current.bitmap, safeSelection))
                                                    copiedSelection = current.selection
                                                    copiedBitmap = clip
                                                    current.withClipboard(clip)
                                                }
                                            },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        SpriteEditorStandardButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("spriteEditorPaste"),
                                            label = "Paste",
                                            onClick = {
                                                updateState { current ->
                                                    val clip = copiedBitmap ?: current.clipboard ?: return@updateState current
                                                    pushUndoSnapshot(current, undoStack, redoStack)
                                                    val pasted = paste(
                                                        current.bitmap,
                                                        clip,
                                                        current.selection.x,
                                                        current.selection.y
                                                    )
                                                    copiedSelection = null
                                                    copiedBitmap = null
                                                    current.withBitmap(pasted).withClipboard(null)
                                                }
                                                isDirty = true
                                            },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        SpriteEditorStandardButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("spriteEditorUndo"),
                                            label = "Undo",
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
                                                    isDirty = true
                                                }
                                            },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        SpriteEditorStandardButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("spriteEditorRedo"),
                                            label = "Redo",
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
                                                    isDirty = true
                                                }
                                            },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        SpriteEditorStandardButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("spriteEditorDelete"),
                                            label = "Delete",
                                            onClick = {
                                                updateState { current ->
                                                    pushUndoSnapshot(current, undoStack, redoStack)
                                                    val cleared = clearTransparent(current.bitmap, current.selection)
                                                    current.withBitmap(cleared)
                                                }
                                                isDirty = true
                                            },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        SpriteEditorStandardButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("spriteEditorFillBlack"),
                                            label = "Fill Black",
                                            onClick = {
                                                updateState { current ->
                                                    pushUndoSnapshot(current, undoStack, redoStack)
                                                    val filled = fillBlack(current.bitmap, current.selection)
                                                    current.withBitmap(filled)
                                                }
                                                isDirty = true
                                            },
                                        )
                                    }
                                }
                                item(span = { GridItemSpan(2) }) {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        SpriteEditorStandardButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("spriteEditorRepeat"),
                                            label = "Repeat",
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
                                                                isDirty = true
                                                                scope.launch { showSnackbarMessage("Repeated: Grayscale") }
                                                            }

                                                            LastToolOp.Outline -> {
                                                                pushUndoSnapshot(current, undoStack, redoStack)
                                                                val outlinedBitmap = addOuterOutline(current.bitmap)
                                                                editorState = current.withBitmap(outlinedBitmap)
                                                                isDirty = true
                                                                scope.launch { showSnackbarMessage("Repeated: Outline") }
                                                            }

                                                            LastToolOp.Binarize -> {
                                                                pushUndoSnapshot(current, undoStack, redoStack)
                                                                val binarizedBitmap = toBinarize(current.bitmap)
                                                                editorState = current.withBitmap(binarizedBitmap)
                                                                isDirty = true
                                                                scope.launch { showSnackbarMessage("Repeated: Binarize") }
                                                            }

                                                            LastToolOp.ClearBackground -> {
                                                                pushUndoSnapshot(current, undoStack, redoStack)
                                                                val clearedBitmap = clearEdgeConnectedBackground(current.bitmap)
                                                                editorState = current.withBitmap(clearedBitmap)
                                                                isDirty = true
                                                                scope.launch { showSnackbarMessage("Repeated: Clear Background") }
                                                            }

                                                            LastToolOp.ClearRegion -> {
                                                                pushUndoSnapshot(current, undoStack, redoStack)
                                                                val clearedBitmap = clearConnectedRegionFromSelection(
                                                                    current.bitmap,
                                                                    current.selection,
                                                                )
                                                                editorState = current.withBitmap(clearedBitmap)
                                                                isDirty = true
                                                                scope.launch { showSnackbarMessage("Repeated: Clear Region") }
                                                            }

                                                            LastToolOp.FillConnected -> {
                                                                val fillResult = fillConnectedToWhite(
                                                                    current.bitmap,
                                                                    current.selection,
                                                                )
                                                                fillStatusText = fillResult.debugText
                                                                if (fillResult.seedType != FillConnectedSeedType.None) {
                                                                    lastFillConnectedSeedType = fillResult.seedType
                                                                }
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
                                                                        isDirty = true
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
                                                                    isDirty = true
                                                                    scope.launch {
                                                                        showSnackbarMessage("Repeated: Center Content in Box")
                                                                    }
                                                                }
                                                            }

                                                            is LastToolOp.ResizeToMax96 -> {
                                                                runResizeSelection(
                                                                    current,
                                                                    anchor = op.anchor,
                                                                    stepFactor = op.stepFactor,
                                                                    downscaleMode = op.downscaleMode,
                                                                    pixelArtMethod = op.pixelArtMethod,
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
                                        SpriteEditorStandardButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("spriteEditorMore"),
                                            label = "More...",
                                            onClick = { activeSheet = SheetType.More },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        SpriteEditorStandardButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("spriteEditorTools"),
                                            label = "Tools",
                                            onClick = { activeSheet = SheetType.Tools },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        SpriteEditorStandardButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("spriteEditorImport"),
                                            label = "Import",
                                            onClick = { importLauncher.launch(arrayOf("image/png")) },
                                        )
                                    }
                                }
                                item {
                                    OperationCell(minHeight = buttonMinHeight) {
                                        SpriteEditorStandardButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("spriteEditorExport"),
                                            label = "Export",
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
                SheetItem(label = "Resize...", testTag = "spriteEditorSheetItemResize"),
                SheetItem(label = "Canvas Size...", testTag = "spriteEditorSheetItemCanvasSize"),
                SheetItem(
                    label = "Apply to Sprite...",
                    testTag = "spriteEditorSheetItemApply",
                    opensApplyDialog = true,
                ),
            )
        } else {
            listOf(
                SheetItem(label = "Flip Copy", testTag = "spriteEditorSheetItemFlipCopy"),
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
                                applyDialogComment = ""
                                applyDialogCommentKind = ApplyDialogCommentKind.None
                                showApplyDialog = true
                            } else if (item.testTag == "spriteEditorSheetItemFlipCopy") {
                                val current = editorState
                                if (current == null) {
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                } else {
                                    updateState { state ->
                                        val safeSelection = rectNormalizeClamp(
                                            state.selection,
                                            state.bitmap.width,
                                            state.bitmap.height,
                                        )
                                        val clip = ensureArgb8888(copyRect(state.bitmap, safeSelection))
                                        val flipped = flipHorizontal(clip)
                                        copiedSelection = state.selection
                                        copiedBitmap = flipped
                                        state.withClipboard(flipped)
                                    }
                                    isDirty = true
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("Flip copied") }
                                }
                            } else if (item.testTag == "spriteEditorSheetItemGrayscale") {
                                val current = editorState
                                if (current == null) {
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                } else {
                                    pushUndoSnapshot(current, undoStack, redoStack)
                                    val grayBitmap = toGrayscale(current.bitmap)
                                    editorState = current.withBitmap(grayBitmap)
                                    isDirty = true
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
                                    isDirty = true
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
                                    isDirty = true
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
                                    isDirty = true
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
                                    isDirty = true
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
                                    if (fillResult.seedType != FillConnectedSeedType.None) {
                                        lastFillConnectedSeedType = fillResult.seedType
                                    }
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
                                            isDirty = true
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
                                        isDirty = true
                                        lastToolOp = LastToolOp.CenterContentInBox
                                        scope.launch { showSnackbarMessage("Centered content in selection") }
                                    }
                                }
                            } else if (item.testTag == "spriteEditorSheetItemResize") {
                                activeSheet = SheetType.None
                                showResizeDialog = true
                            } else if (item.testTag == "spriteEditorSheetItemCanvasSize") {
                                val current = editorState
                                if (current == null) {
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                } else {
                                    canvasWidthInput = TextFieldValue(current.bitmap.width.toString())
                                    canvasHeightInput = TextFieldValue(current.bitmap.height.toString())
                                    canvasAnchor = ResizeAnchor.TopLeft
                                    activeSheet = SheetType.None
                                    showCanvasSizeDialog = true
                                }
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

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("Unsaved changes") },
            text = { Text("You have unsaved changes. What would you like to do?") },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // [dp] 左右・下: ダイアログボタンの余白(余白)に関係
                        .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .fillMaxWidth(),
                    ) {
                        SpriteEditorStandardOutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                // [dp] 左右: 1段目ボタンの横幅を詰めるための最小余白(余白)に関係
                                .padding(horizontal = 4.dp)
                                // [dp] 左右: 1段目ボタンの見た目幅を少しだけ詰める最小余白(余白)に関係
                                .padding(horizontal = 2.dp)
                                .testTag("spriteEditorExitDiscard"),
                            label = "Don’t Save",
                            onClick = {
                                showExitConfirmDialog = false
                                closeEditor()
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(
                            modifier = Modifier
                                // [dp] 上下: 2段ボタン間の間隔(間隔)に関係
                                .height(12.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // [dp] 左右: 2段目ボタン全体の横幅を詰めるための最小余白(余白)に関係
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SpriteEditorStandardOutlinedButton(
                                modifier = Modifier
                                    .weight(1f)
                                    // [dp] 左右: 2段目左ボタンの見た目幅を少しだけ詰める最小余白(余白)に関係
                                    .padding(horizontal = 2.dp)
                                    .testTag("spriteEditorExitCancel"),
                                label = "Cancel",
                                onClick = { showExitConfirmDialog = false },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            SpriteEditorStandardButton(
                                modifier = Modifier
                                    .weight(1f)
                                    // [dp] 左右: 2段目右ボタンの見た目幅を少しだけ詰める最小余白(余白)に関係
                                    .padding(horizontal = 2.dp)
                                    .testTag("spriteEditorExitSave"),
                                label = "Save",
                                onClick = {
                                    scope.launch {
                                        val saved = runSave()
                                        if (saved) {
                                            showExitConfirmDialog = false
                                            closeEditor()
                                        }
                                    }
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            modifier = Modifier.testTag("spriteEditorExitDialog"),
        )
    }

    if (showApplyDialog) {
        val applyTargetLabel = "Sprite Settings (Current)"
        val currentEditorBitmap = editorState?.bitmap
        val currentEditorSelection = editorState?.selection
        val existingOverrideBitmap = SpriteSettingsSessionSpriteOverride.bitmap
        val beforeBitmap = remember(existingOverrideBitmap) {
            // Sprite Settings 側の既存セッション上書きがあればそれを優先表示する
            existingOverrideBitmap
        }
        val afterBitmap = remember(applySource, currentEditorBitmap, currentEditorSelection) {
            val bitmap = currentEditorBitmap ?: return@remember null
            when (applySource) {
                ApplySource.FullImage -> ensureArgb8888(bitmap)
                ApplySource.Selection -> {
                    val selection = currentEditorSelection ?: return@remember null
                    val normalizedSelection = rectNormalizeClamp(
                        selection,
                        bitmap.width,
                        bitmap.height,
                    )
                    if (normalizedSelection.w < 1 || normalizedSelection.h < 1) {
                        null
                    } else {
                        ensureArgb8888(copyRect(bitmap, normalizedSelection))
                    }
                }
            }
        }
        val beforeImageBitmap = remember(beforeBitmap) { beforeBitmap?.asImageBitmap() }
        val afterImageBitmap = remember(afterBitmap) { afterBitmap?.asImageBitmap() }
        val setApplyDialogComment: (ApplyDialogCommentKind, String) -> Unit = { kind, message ->
            applyDialogCommentKind = kind
            applyDialogComment = message
        }
        val closeApplyDialog = {
            showApplyDialog = false
            applyDialogComment = ""
            applyDialogCommentKind = ApplyDialogCommentKind.None
        }
        val optionCommentLines = remember(applyOverwrite, existingOverrideBitmap) {
            buildList {
                if (!applyOverwrite && existingOverrideBitmap != null) {
                    add("Overwrite disabled: apply will be rejected")
                }
            }
        }
        val fallbackCommentText = remember(optionCommentLines) { optionCommentLines.joinToString("\n") }
        val hasExplicitComment = applyDialogCommentKind != ApplyDialogCommentKind.None && applyDialogComment.isNotBlank()
        val resolvedCommentKind = when {
            hasExplicitComment -> applyDialogCommentKind
            fallbackCommentText.isNotBlank() -> ApplyDialogCommentKind.Warn
            else -> ApplyDialogCommentKind.None
        }
        val resolvedCommentText = when {
            hasExplicitComment -> applyDialogComment
            else -> fallbackCommentText
        }
        val resolvedCommentPrefix = when (resolvedCommentKind) {
            ApplyDialogCommentKind.None -> ""
            ApplyDialogCommentKind.Info -> "[INFO] "
            ApplyDialogCommentKind.Warn -> "[WARN] "
            ApplyDialogCommentKind.Error -> "[ERROR] "
        }
        val resolvedCommentAlpha = when (resolvedCommentKind) {
            ApplyDialogCommentKind.None -> 0f
            ApplyDialogCommentKind.Info -> 0.80f
            ApplyDialogCommentKind.Warn -> 0.88f
            ApplyDialogCommentKind.Error -> 1f
        }
        AlertDialog(
            onDismissRequest = closeApplyDialog,
            title = { Text("Apply to Sprite") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Applies the current image (or selection) to a sprite asset.")
                    Text(
                        text = "Target: $applyTargetLabel",
                        modifier = Modifier.testTag("spriteEditorApplyTarget"),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Source")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier
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
                            Row(
                                modifier = Modifier
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
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Preview")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("Before")
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .testTag("spriteEditorApplyPreviewBefore"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (beforeImageBitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = beforeImageBitmap,
                                            contentDescription = "Apply Preview Before",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp),
                                        )
                                    } else {
                                        Text(
                                            text = "No preview",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("After")
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .testTag("spriteEditorApplyPreviewAfter"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (afterImageBitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = afterImageBitmap,
                                            contentDescription = "Apply Preview After",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp),
                                        )
                                    } else {
                                        Text(
                                            text = "No preview",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                        val commentLines = remember(resolvedCommentPrefix, resolvedCommentText) {
                            if (resolvedCommentText.isBlank()) {
                                emptyList()
                            } else {
                                resolvedCommentText
                                    .split("\n")
                                    .filter { it.isNotBlank() }
                                    .mapIndexed { index, line ->
                                        if (index == 0) "$resolvedCommentPrefix$line" else line
                                    }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = APPLY_DIALOG_COMMENT_MIN_HEIGHT)
                                .testTag("spriteEditorApplyCommentArea"),
                        ) {
                            when {
                                commentLines.size == 1 -> {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.TopStart,
                                    ) {
                                        Text(
                                            text = commentLines.first(),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.alpha(resolvedCommentAlpha),
                                            maxLines = 3,
                                            overflow = TextOverflow.Clip,
                                            softWrap = true,
                                        )
                                    }
                                }

                                commentLines.size >= 2 -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(APPLY_DIALOG_COMMENT_SLOT_SPACING),
                                    ) {
                                        commentLines.forEach { line ->
                                            Text(
                                                text = line,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.alpha(resolvedCommentAlpha),
                                                maxLines = 3,
                                                overflow = TextOverflow.Clip,
                                                softWrap = true,
                                            )
                                        }
                                    }
                                }

                                else -> {
                                    Text(
                                        text = " ",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .alpha(0f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Clip,
                                        softWrap = true,
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Options")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("spriteEditorApplyOverwrite"),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = applyOverwrite,
                                onCheckedChange = {
                                    applyOverwrite = it
                                    if (applyDialogCommentKind != ApplyDialogCommentKind.Error) {
                                        setApplyDialogComment(ApplyDialogCommentKind.None, "")
                                    }
                                },
                            )
                            Text("Overwrite existing")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("spriteEditorApplyPreserveAlpha"),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = applyPreserveAlpha,
                                onCheckedChange = {
                                    applyPreserveAlpha = it
                                    if (applyDialogCommentKind != ApplyDialogCommentKind.Error) {
                                        setApplyDialogComment(ApplyDialogCommentKind.None, "")
                                    }
                                },
                            )
                            Text("Preserve transparency")
                        }
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .fillMaxWidth(),
                    ) {
                        SpriteEditorStandardOutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                                .padding(horizontal = 2.dp)
                                .testTag("spriteEditorApplyResetDefault"),
                            label = "Reset to Default",
                            onClick = {
                                scope.launch {
                                    closeApplyDialog()
                                    withFrameNanos { }
                                    SpriteSettingsSessionSpriteOverride.bitmap = null
                                    deleteCurrentSpriteSheetOverride(context)
                                    settingsPreferences.saveSpriteCurrentSheetOverrideEnabled(enabled = false)
                                    showSnackbarMessage("Reset to default")
                                }
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SpriteEditorCancelApplyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            onCancel = closeApplyDialog,
                            onApply = {
                                val current = editorState
                                if (current == null) {
                                    setApplyDialogComment(ApplyDialogCommentKind.Error, "No sprite loaded")
                                    return@SpriteEditorCancelApplyRow
                                }
                                scope.launch {
                                    val existingOverride = SpriteSettingsSessionSpriteOverride.bitmap
                                    if (existingOverride != null && !applyOverwrite) {
                                        setApplyDialogComment(
                                            ApplyDialogCommentKind.Warn,
                                            "Apply rejected: enable Overwrite existing",
                                        )
                                        return@launch
                                    }

                                    val sourceBitmap = when (applySource) {
                                        ApplySource.FullImage -> ensureArgb8888(current.bitmap)
                                        ApplySource.Selection -> {
                                            val normalizedSelection = rectNormalizeClamp(
                                                current.selection,
                                                current.bitmap.width,
                                                current.bitmap.height,
                                            )
                                            if (normalizedSelection.w < 1 || normalizedSelection.h < 1) {
                                                setApplyDialogComment(
                                                    ApplyDialogCommentKind.Error,
                                                    "Selection is empty or invalid",
                                                )
                                                return@launch
                                            }
                                            ensureArgb8888(copyRect(current.bitmap, normalizedSelection))
                                        }
                                    }

                                    val bitmapToSave = if (applyPreserveAlpha) {
                                        val before = beforeBitmap
                                        if (before == null) {
                                            setApplyDialogComment(
                                                ApplyDialogCommentKind.Error,
                                                "Preserve transparency requires existing sprite sheet",
                                            )
                                            return@launch
                                        }
                                        if (before.width != sourceBitmap.width || before.height != sourceBitmap.height) {
                                            setApplyDialogComment(
                                                ApplyDialogCommentKind.Error,
                                                "Bitmap size mismatch: before=${before.width}x${before.height}, src=${sourceBitmap.width}x${sourceBitmap.height}",
                                            )
                                            return@launch
                                        }
                                        compositePreserveTransparency(dst = ensureArgb8888(before), src = sourceBitmap)
                                    } else {
                                        sourceBitmap
                                    }

                                    SpriteSettingsSessionSpriteOverride.bitmap = bitmapToSave
                                    val saved = saveCurrentSpriteSheetOverride(context, bitmapToSave)
                                    if (!saved) {
                                        setApplyDialogComment(
                                            ApplyDialogCommentKind.Error,
                                            "Failed to persist Sprite Settings (Current)",
                                        )
                                        return@launch
                                    }
                                    settingsPreferences.saveSpriteCurrentSheetOverrideEnabled(enabled = true)
                                    closeApplyDialog()
                                    withFrameNanos { }
                                    showSnackbarMessage("Applied to Sprite Settings (Current)")
                                }
                            },
                            cancelTestTag = "spriteEditorApplyCancel",
                            applyTestTag = "spriteEditorApplyConfirm",
                        )
                    }
                }
            },
            dismissButton = {},
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Downscale mode")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = resizeDownscaleMode == ResizeDownscaleMode.PixelArtStable,
                                    onClick = { resizeDownscaleMode = ResizeDownscaleMode.PixelArtStable },
                                    role = Role.RadioButton,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = resizeDownscaleMode == ResizeDownscaleMode.PixelArtStable,
                                onClick = null,
                            )
                            Text("PixelArt Stable")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = resizeDownscaleMode == ResizeDownscaleMode.DefaultMultiStep,
                                    onClick = { resizeDownscaleMode = ResizeDownscaleMode.DefaultMultiStep },
                                    role = Role.RadioButton,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = resizeDownscaleMode == ResizeDownscaleMode.DefaultMultiStep,
                                onClick = null,
                            )
                            Text("MultiStep")
                        }
                    }
                    if (resizeDownscaleMode == ResizeDownscaleMode.DefaultMultiStep) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Step Factor")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = resizeStepFactor == 0.5f,
                                        onClick = { resizeStepFactor = 0.5f },
                                        role = Role.RadioButton,
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = resizeStepFactor == 0.5f,
                                    onClick = null,
                                )
                                Text("0.5")
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = resizeStepFactor == 0.75f,
                                        onClick = { resizeStepFactor = 0.75f },
                                        role = Role.RadioButton,
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = resizeStepFactor == 0.75f,
                                    onClick = null,
                                )
                                Text("0.75")
                            }
                        }
                    }
                    if (resizeDownscaleMode == ResizeDownscaleMode.PixelArtStable) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("PixelArt method")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = resizePixelArtMethod == PixelArtStableMethod.CenterSample,
                                        onClick = { resizePixelArtMethod = PixelArtStableMethod.CenterSample },
                                        role = Role.RadioButton,
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = resizePixelArtMethod == PixelArtStableMethod.CenterSample,
                                    onClick = null,
                                )
                                Text("CenterSample")
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = resizePixelArtMethod == PixelArtStableMethod.DarkDominant,
                                        onClick = { resizePixelArtMethod = PixelArtStableMethod.DarkDominant },
                                        role = Role.RadioButton,
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = resizePixelArtMethod == PixelArtStableMethod.DarkDominant,
                                    onClick = null,
                                )
                                Text("DarkDominant")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // [dp] 左右/下: ボタン領域の横幅と下余白をCanvas Sizeダイアログに揃える(余白)に関係
                        .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            // [dp] 最大幅: ボタン行の横幅上限をCanvas Sizeダイアログに揃える(サイズ)に関係
                            .widthIn(max = 320.dp)
                            .fillMaxWidth(),
                    ) {
                        SpriteEditorCancelApplyRow(
                            onCancel = { showResizeDialog = false },
                            onApply = {
                                showResizeDialog = false
                                val current = editorState
                                if (current == null) {
                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                } else {
                                    runResizeSelection(
                                        current,
                                        anchor = resizeAnchor,
                                        stepFactor = resizeStepFactor,
                                        downscaleMode = resizeDownscaleMode,
                                        pixelArtMethod = resizePixelArtMethod,
                                        repeated = false,
                                    )
                                }
                            },
                        )
                    }
                }
            },
            dismissButton = {},
        )
    }

    if (showCanvasSizeDialog) {
        AlertDialog(
            onDismissRequest = { showCanvasSizeDialog = false },
            title = { Text("Canvas Size") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // [dp] 左右: ダイアログ本文の横幅を揃えるための最小余白(余白)に関係
                        .padding(start = 24.dp, end = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            // [dp] 最大幅: ダイアログ本文の横幅上限(サイズ)に関係
                            .widthIn(max = 320.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = canvasWidthInput,
                                onValueChange = { input ->
                                    canvasWidthInput = clampPxFieldValue(
                                        canvasWidthInput,
                                        input,
                                        4096,
                                    )
                                },
                                label = { Text("W(px)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(54.dp)
                                    // Material3の最小高さ制約で54.dpに収まらない場合があるため保険として残す
                                    .heightIn(min = 54.dp),
                            )
                            OutlinedTextField(
                                value = canvasHeightInput,
                                onValueChange = { input ->
                                    canvasHeightInput = clampPxFieldValue(
                                        canvasHeightInput,
                                        input,
                                        4096,
                                    )
                                },
                                label = { Text("H(px)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(54.dp)
                                    // Material3の最小高さ制約で54.dpに収まらない場合があるため保険として残す
                                    .heightIn(min = 54.dp),
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Anchor")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.selectable(
                                        selected = canvasAnchor == ResizeAnchor.TopLeft,
                                        onClick = { canvasAnchor = ResizeAnchor.TopLeft },
                                        role = Role.RadioButton,
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = canvasAnchor == ResizeAnchor.TopLeft,
                                        onClick = null,
                                    )
                                    Text("TopLeft")
                                }
                                Row(
                                    modifier = Modifier.selectable(
                                        selected = canvasAnchor == ResizeAnchor.Center,
                                        onClick = { canvasAnchor = ResizeAnchor.Center },
                                        role = Role.RadioButton,
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = canvasAnchor == ResizeAnchor.Center,
                                        onClick = null,
                                    )
                                    Text("Center")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // [dp] 左右・下: ダイアログボタンの余白(余白)に関係
                        .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            // [dp] 最大幅: ダイアログ内ボタンの横幅上限(サイズ)に関係
                            .widthIn(max = 320.dp)
                            .fillMaxWidth(),
                    ) {
                        SpriteEditorStandardOutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                // [dp] 左右: 1段目ボタンの横幅を詰めるための最小余白(余白)に関係
                                .padding(horizontal = 4.dp)
                                // [dp] 左右: 1段目ボタンの見た目幅を少しだけ詰める最小余白(余白)に関係
                                .padding(horizontal = 2.dp),
                            label = "Reset 288x288",
                            onClick = {
                                canvasWidthInput = TextFieldValue("288")
                                canvasHeightInput = TextFieldValue("288")
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(
                            modifier = Modifier
                                // [dp] 上下: 2段ボタン間の間隔(間隔)に関係
                                .height(12.dp)
                        )
                        SpriteEditorCancelApplyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                // [dp] 左右: 2段目ボタン全体の横幅を詰めるための最小余白(余白)に関係
                                .padding(horizontal = 4.dp),
                            onCancel = { showCanvasSizeDialog = false },
                            onApply = {
                                showCanvasSizeDialog = false
                                val current = editorState
                                if (current == null) {
                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                    return@SpriteEditorCancelApplyRow
                                }
                                val parsedW = canvasWidthInput.text.toIntOrNull()
                                val parsedH = canvasHeightInput.text.toIntOrNull()
                                val safeW = (parsedW ?: current.bitmap.width).coerceIn(1, 4096)
                                val safeH = (parsedH ?: current.bitmap.height).coerceIn(1, 4096)
                                canvasWidthInput = TextFieldValue(safeW.toString())
                                canvasHeightInput = TextFieldValue(safeH.toString())
                                if (safeW == current.bitmap.width && safeH == current.bitmap.height) {
                                    scope.launch { showSnackbarMessage("Canvas unchanged") }
                                    return@SpriteEditorCancelApplyRow
                                }
                                pushUndoSnapshot(current, undoStack, redoStack)
                                val resizedBitmap = resizeCanvas(
                                    current.bitmap,
                                    safeW,
                                    safeH,
                                    canvasAnchor,
                                )
                                val nextSelection = rectNormalizeClamp(
                                    current.selection,
                                    safeW,
                                    safeH,
                                )
                                editorState = current.withBitmap(resizedBitmap).withSelection(nextSelection)
                                isDirty = true
                                activeSheet = SheetType.None
                                scope.launch { showSnackbarMessage("Canvas resized to ${safeW}x${safeH}") }
                            },
                        )
                    }
                }
            },
            dismissButton = {},
        )
    }
}

@Composable
private fun SpriteEditorCancelApplyRow(
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    cancelLabel: String = "Cancel",
    applyLabel: String = "Apply",
    cancelTestTag: String? = null,
    applyTestTag: String? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpriteEditorStandardOutlinedButton(
            modifier = Modifier
                .weight(1f)
                // [dp] 左右: 左ボタンの見た目幅を少しだけ詰める最小余白(余白)に関係
                .padding(horizontal = 2.dp)
                .then(if (cancelTestTag != null) Modifier.testTag(cancelTestTag) else Modifier),
            label = cancelLabel,
            onClick = onCancel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SpriteEditorStandardButton(
            modifier = Modifier
                .weight(1f)
                // [dp] 左右: 右ボタンの見た目幅を少しだけ詰める最小余白(余白)に関係
                .padding(horizontal = 2.dp)
                .then(if (applyTestTag != null) Modifier.testTag(applyTestTag) else Modifier),
            label = applyLabel,
            onClick = onApply,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun digitsOnly(input: String): String = input.filter { ch -> ch.isDigit() }

@VisibleForTesting
internal fun clampPxFieldValue(prev: TextFieldValue, next: TextFieldValue, max: Int): TextFieldValue {
    val maxDigits = max.coerceAtLeast(1).toString().length
    val sanitized = digitsOnly(next.text)
    val parsed = sanitized.toLongOrNull()
    val exceedsMax = parsed != null && parsed > max
    val exceedsDigits = sanitized.length > maxDigits
    val clamped = clampPxInput(next.text, max)
    val prevText = prev.text
    if ((exceedsDigits || exceedsMax) && clamped == prevText) {
        return TextFieldValue(
            text = prevText,
            selection = TextRange(prevText.length),
            composition = null,
        )
    }
    return TextFieldValue(
        text = clamped,
        selection = TextRange(clamped.length),
        composition = null,
    )
}

@VisibleForTesting
internal fun clampPxInput(raw: String, max: Int): String {
    val sanitized = digitsOnly(raw)
    if (sanitized.isEmpty()) {
        return ""
    }
    val maxDigits = max.coerceAtLeast(1).toString().length
    val parsed = sanitized.toLongOrNull()
    if (parsed == null) {
        return sanitized.take(maxDigits)
    }
    val clamped = parsed.coerceIn(1L, max.toLong()).toString()
    return if (clamped.length > maxDigits) clamped.take(maxDigits) else clamped
}

@VisibleForTesting
internal fun rejectPxFieldValueOverMaxDigits(
    prev: TextFieldValue,
    nextRaw: String,
    maxDigits: Int = 4,
): TextFieldValue {
    val sanitized = digitsOnly(nextRaw)
    if (sanitized.isEmpty()) {
        return TextFieldValue(
            text = "",
            selection = TextRange(0),
            composition = null,
        )
    }
    if (sanitized.length > maxDigits) {
        return prev
    }
    return TextFieldValue(
        text = sanitized,
        selection = TextRange(sanitized.length),
        composition = null,
    )
}

// [dp] 縦: 見た目32dpを維持しつつタップ領域を確保
private val SpriteEditorButtonHeight = 32.dp
private val SpriteEditorButtonMinHeight = 46.dp
// [dp] 左右: ボタン内側の余白(余白)に関係
private val SpriteEditorButtonPadding = PaddingValues(horizontal = 8.dp)
private val SpriteEditorPillShape = RoundedCornerShape(999.dp)

@Composable
private fun SpriteEditorStandardButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            // [dp] 縦: 見た目32dpを維持しつつタップ領域を確保
            .height(SpriteEditorButtonHeight)
            .heightIn(min = SpriteEditorButtonMinHeight),
        contentPadding = SpriteEditorButtonPadding,
        shape = SpriteEditorPillShape,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                maxLines = maxLines,
                overflow = overflow,
            )
        }
    }
}

@Composable
private fun SpriteEditorStandardOutlinedButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            // [dp] 縦: 見た目32dpを維持しつつタップ領域を確保
            .height(SpriteEditorButtonHeight)
            .heightIn(min = SpriteEditorButtonMinHeight),
        contentPadding = SpriteEditorButtonPadding,
        shape = SpriteEditorPillShape,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                maxLines = maxLines,
                overflow = overflow,
            )
        }
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

private fun currentSpriteSheetOverrideFile(context: android.content.Context): File {
    return File(context.filesDir, "sprite_settings/current_sprite_sheet.png")
}

private suspend fun saveCurrentSpriteSheetOverride(context: android.content.Context, bitmap: Bitmap): Boolean {
    return withContext(Dispatchers.IO) {
        val targetFile = currentSpriteSheetOverrideFile(context)
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        targetFile.parentFile?.mkdirs()
        runCatching {
            val safeBitmap = ensureArgb8888(bitmap)
            FileOutputStream(tempFile).use { output ->
                safeBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            if (targetFile.exists() && !targetFile.delete()) {
                tempFile.delete()
                return@runCatching false
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                return@runCatching false
            }
            true
        }.getOrDefault(false)
    }
}

private suspend fun deleteCurrentSpriteSheetOverride(context: android.content.Context): Boolean {
    return withContext(Dispatchers.IO) {
        val targetFile = currentSpriteSheetOverrideFile(context)
        if (!targetFile.exists()) {
            return@withContext true
        }
        runCatching { targetFile.delete() }.getOrDefault(false)
    }
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
