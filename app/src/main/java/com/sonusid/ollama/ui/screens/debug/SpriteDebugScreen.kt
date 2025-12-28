package com.sonusid.ollama.ui.screens.debug
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import androidx.savedstate.SavedStateRegistryOwner
import com.sonusid.ollama.R
import com.sonusid.ollama.data.SpriteSheetConfig
import com.sonusid.ollama.ui.components.LamiSpriteStatus
import com.sonusid.ollama.ui.components.LamiStatusSprite
import com.sonusid.ollama.ui.screens.settings.SettingsPreferences
import com.sonusid.ollama.ui.screens.settings.copyJsonToClipboard
import com.sonusid.ollama.ui.screens.settings.pasteJsonFromClipboard
import com.sonusid.ollama.util.SpriteAnalysis
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import kotlin.math.hypot
import kotlin.math.max

private const val SPRITE_DEBUG_TAG = "SpriteDebug"
private const val DEFAULT_SPRITE_SIZE = 288
val SpriteBoxCountKey = SemanticsPropertyKey<Int>("SpriteBoxCount")
var SemanticsPropertyReceiver.spriteBoxCount by SpriteBoxCountKey

@Parcelize
data class SpriteBox(
    val index: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) : Parcelable

enum class SpriteEditMode { None, Pen, Eraser, Selection }

@Parcelize
data class SelectionArea(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
) : Parcelable {
    fun toBox(index: Int = -1): SpriteBox = SpriteBox(index = index, x = x, y = y, width = width, height = height)
}

private fun SpriteBox.toSelection(): SelectionArea = SelectionArea(x = x, y = y, width = width, height = height)

@Parcelize
enum class ResizeHandle : Parcelable { TopLeft, TopRight, BottomLeft, BottomRight }

@Parcelize
data class MatchOffset(
    val dx: Int = 0,
    val dy: Int = 0,
) : Parcelable

@Parcelize
data class SpriteMatchScore(
    val from: Int,
    val to: Int,
    val score: Float,
    val offset: MatchOffset = MatchOffset(),
) : Parcelable

@Parcelize
data class SpriteDebugState(
    val selectedBoxIndex: Int = 0,
    val boxes: List<SpriteBox> = defaultBoxes(IntSize(DEFAULT_SPRITE_SIZE, DEFAULT_SPRITE_SIZE)),
    val step: Int = 1,
    val snapToGrid: Boolean = false,
    val searchRadius: Float = 6f,
    val sobelThreshold: Float = 0.5f,
    val editingMode: SpriteEditMode = SpriteEditMode.None,
    val previewSpeedMs: Long = 650L,
    val onionSkin: Boolean = false,
    val showCenterLine: Boolean = false,
    val brushSize: Float = 6f,
    val selection: SelectionArea? = null,
    val activeHandle: ResizeHandle? = null,
    val matchScores: List<SpriteMatchScore> = emptyList(),
    val bestMatchIndices: List<Int> = emptyList(),
    val spriteSheetConfig: SpriteSheetConfig = SpriteSheetConfig.default3x3(),
) : Parcelable {
    companion object {
        fun defaultBoxes(size: IntSize, config: SpriteSheetConfig = SpriteSheetConfig.default3x3()): List<SpriteBox> {
            val sheetWidth = (config.frameWidth * config.cols).takeIf { it > 0 } ?: size.width
            val sheetHeight = (config.frameHeight * config.rows).takeIf { it > 0 } ?: size.height
            val scaleX = (size.width / sheetWidth.toFloat()).takeIf { it.isFinite() && it > 0f } ?: 1f
            val scaleY = (size.height / sheetHeight.toFloat()).takeIf { it.isFinite() && it > 0f } ?: 1f
            val orderedBoxes = config.boxes
                .takeIf { it.isNotEmpty() }
                ?.sortedBy { it.frameIndex }
                ?.mapIndexed { index, box ->
                    SpriteBox(
                        index = index,
                        x = box.x * scaleX,
                        y = box.y * scaleY,
                        width = box.width * scaleX,
                        height = box.height * scaleY,
                    )
                }
            if (!orderedBoxes.isNullOrEmpty()) return orderedBoxes
            val rows = config.rows.takeIf { it > 0 } ?: 3
            val cols = config.cols.takeIf { it > 0 } ?: 3
            val cellWidth = (size.width / cols.toFloat()).takeIf { it.isFinite() && it > 0f } ?: 1f
            val cellHeight = (size.height / rows.toFloat()).takeIf { it.isFinite() && it > 0f } ?: 1f
            val total = (rows * cols).takeIf { it > 0 } ?: 9
            return List(total) { index ->
                val col = index % cols
                val row = index / cols
                SpriteBox(
                    index = index,
                    x = col * cellWidth,
                    y = row * cellHeight,
                    width = cellWidth,
                    height = cellHeight,
                )
            }
        }
    }
}

private fun SpriteBox.containsPoint(point: Offset): Boolean =
    point.x in x..(x + width) && point.y in y..(y + height)

private fun Offset.isFiniteOffset(): Boolean = x.isFinite() && y.isFinite()

private fun Size.isFiniteSize(): Boolean = width.isFinite() && height.isFinite()

private data class SpriteSheetScale(
    val scale: Float,
    val offset: Offset,
) {
    fun imageToCanvas(offsetImage: Offset): Offset {
        val safeScale = scale.coerceAtLeast(0.01f)
        if (!offset.isFiniteOffset() || !offsetImage.isFiniteOffset()) {
            Log.w(SPRITE_DEBUG_TAG, "imageToCanvas received non-finite input: offset=$offset offsetImage=$offsetImage safeScale=$safeScale")
            return Offset.Zero
        }
        val result = Offset(
            x = offset.x + offsetImage.x * safeScale,
            y = offset.y + offsetImage.y * safeScale,
        )
        if (!result.isFiniteOffset()) {
            Log.w(SPRITE_DEBUG_TAG, "imageToCanvas produced non-finite result: offset=$offset offsetImage=$offsetImage safeScale=$safeScale")
            return Offset.Zero
        }
        return result
    }

    fun canvasToImage(offsetCanvas: Offset): Offset {
        val safeScale = scale.coerceAtLeast(0.01f)
        if (!offsetCanvas.isFiniteOffset() || !offset.isFiniteOffset()) {
            Log.w(SPRITE_DEBUG_TAG, "canvasToImage received non-finite input: offsetCanvas=$offsetCanvas offset=$offset safeScale=$safeScale")
            return Offset.Zero
        }
        val result = Offset(
            x = (offsetCanvas.x - offset.x) / safeScale,
            y = (offsetCanvas.y - offset.y) / safeScale,
        )
        if (!result.isFiniteOffset()) {
            Log.w(SPRITE_DEBUG_TAG, "canvasToImage produced non-finite result: offsetCanvas=$offsetCanvas offset=$offset safeScale=$safeScale")
            return Offset.Zero
        }
        return result
    }

    fun imageSizeToCanvas(size: Size): Size {
        val safeScale = scale.coerceAtLeast(0.01f)
        if (!size.isFiniteSize()) {
            Log.w(SPRITE_DEBUG_TAG, "imageSizeToCanvas received non-finite input: size=$size safeScale=$safeScale")
            return Size.Zero
        }
        val result = Size(width = size.width * safeScale, height = size.height * safeScale)
        if (!result.isFiniteSize()) {
            Log.w(SPRITE_DEBUG_TAG, "imageSizeToCanvas produced non-finite result: size=$size safeScale=$safeScale")
            return Size.Zero
        }
        return result
    }
}

private fun calculateScale(intrinsicSize: Size, layoutSize: Size): SpriteSheetScale {
    val intrinsicWidth = intrinsicSize.width
    val intrinsicHeight = intrinsicSize.height
    val layoutWidth = layoutSize.width
    val layoutHeight = layoutSize.height
    val hasInvalidSize = intrinsicWidth <= 0f || intrinsicHeight <= 0f || layoutWidth <= 0f || layoutHeight <= 0f
    val rawScale = if (hasInvalidSize) 0f else minOf(layoutWidth / intrinsicWidth, layoutHeight / intrinsicHeight)
    val scaleRatio = maxOf(0.01f, rawScale)
    val offset = Offset(
        (layoutSize.width - intrinsicSize.width * scaleRatio) / 2f,
        (layoutSize.height - intrinsicSize.height * scaleRatio) / 2f,
    )
    return SpriteSheetScale(scaleRatio, offset)
}

private fun SpriteSheetScale.isValid(): Boolean = scale.isFinite() && offset.x.isFinite() && offset.y.isFinite()

class SpriteDebugViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val dataStore: SpriteDebugDataStore,
    private val settingsPreferences: SettingsPreferences,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val gson: Gson = Gson(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(initializeState())
    val uiState: StateFlow<SpriteDebugState> = _uiState.asStateFlow()
    private val _analysisResult = MutableStateFlow<SpriteAnalysis.SpriteAnalysisResult?>(null)
    val analysisResult: StateFlow<SpriteAnalysis.SpriteAnalysisResult?> = _analysisResult.asStateFlow()
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()
    private val _sheetBitmap = MutableStateFlow<ImageBitmap?>(null)
    val sheetBitmap: StateFlow<ImageBitmap?> = _sheetBitmap.asStateFlow()
    private val _reloadSignal = MutableStateFlow(0)
    val reloadSignal: StateFlow<Int> = _reloadSignal.asStateFlow()
    private val undoStack = ArrayDeque<Bitmap>()
    private val redoStack = ArrayDeque<Bitmap>()
    private var spriteSheetBitmap: Bitmap? = null
    private var analysisJob: Job? = null
    private var selectionAnchor: Offset? = null

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                savedStateHandle[KEY_STATE] = state
            }
        }
        viewModelScope.launch(ioDispatcher) {
            restorePersistedState()
            observeSpriteSheetConfig()
        }
    }

    fun setSpriteSheet(bitmap: Bitmap?) {
        spriteSheetBitmap = bitmap
        _sheetBitmap.value = bitmap?.asImageBitmap()
        val targetSize = bitmap?.let { IntSize(it.width, it.height) } ?: IntSize(DEFAULT_SPRITE_SIZE, DEFAULT_SPRITE_SIZE)
        _uiState.update { current -> current.ensureBoxes(targetSize) }
        clearAnalysis()
        persistStateAsync()
    }

    fun reloadSpriteSheet() {
        _reloadSignal.update { it + 1 }
    }

    fun loadSpriteSheetFromUri(context: Context, uri: Uri, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch(ioDispatcher) {
            val bitmap = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
            val hasBitmap = bitmap != null
            if (hasBitmap) {
                Log.d(SPRITE_DEBUG_TAG, "Loaded sprite sheet from uri=$uri")
            } else {
                Log.w(SPRITE_DEBUG_TAG, "Failed to load sprite sheet from uri=$uri")
            }
            withContext(Dispatchers.Main) {
                setSpriteSheet(bitmap)
                onResult(hasBitmap)
            }
        }
    }

    fun selectBox(index: Int) {
        val bounded = index.coerceIn(0, 8)
        updateState { copy(selectedBoxIndex = bounded) }
    }

    fun updateStep(step: Int) {
        updateState { copy(step = step.coerceIn(1, 8)) }
    }

    fun toggleSnap() {
        updateState { copy(snapToGrid = !snapToGrid) }
    }

    fun updateSearchRadius(value: Float) {
        updateState { copy(searchRadius = value) }
    }

    fun updateThreshold(value: Float) {
        updateState { copy(sobelThreshold = value) }
    }

    fun updatePreviewSpeed(value: Long) {
        updateState { copy(previewSpeedMs = value.coerceIn(120L, 2000L)) }
    }

    fun toggleOnionSkin(enabled: Boolean) {
        updateState { copy(onionSkin = enabled) }
    }

    fun toggleCenterLine(enabled: Boolean) {
        updateState { copy(showCenterLine = enabled) }
    }

    fun setEditingMode(mode: SpriteEditMode) {
        updateState { copy(editingMode = mode) }
    }

    fun setBrushSize(size: Float) {
        updateState { copy(brushSize = size.coerceAtLeast(1f)) }
    }

    fun updateBoxPosition(imageDelta: Offset) {
        val state = _uiState.value
        val selected = state.boxes.getOrNull(state.selectedBoxIndex) ?: return
        val bitmap = spriteSheetBitmap ?: return
        val maxX = bitmap.width - selected.width
        val maxY = bitmap.height - selected.height
        if (maxX < 0f || maxY < 0f) return
        val snappedDelta = if (state.snapToGrid) snapDelta(imageDelta, state.step) else imageDelta
        val newBox = selected.copy(
            x = (selected.x + snappedDelta.x).coerceIn(0f, maxX),
            y = (selected.y + snappedDelta.y).coerceIn(0f, maxY),
        )
        replaceBox(newBox)
    }

    fun nudgeSelected(dx: Int, dy: Int) {
        updateBoxPosition(Offset(dx.toFloat(), dy.toFloat()))
    }

    fun updateBoxCoordinate(x: Float?, y: Float?) {
        val bitmap = spriteSheetBitmap ?: return
        val state = _uiState.value
        val selected = state.boxes.getOrNull(state.selectedBoxIndex) ?: return
        val newBox = selected.copy(
            x = (x ?: selected.x).coerceIn(0f, bitmap.width - selected.width),
            y = (y ?: selected.y).coerceIn(0f, bitmap.height - selected.height),
        )
        replaceBox(newBox)
    }

    private fun currentAspectRatio(): Float {
        val box = _uiState.value.boxes.getOrNull(_uiState.value.selectedBoxIndex) ?: return 0f
        val ratio = if (box.width > 0f) box.height / box.width else 0f
        return ratio.takeIf { it.isFinite() && it > 0f } ?: 0f
    }

    private fun clampToImage(offset: Offset): Offset {
        val bitmap = spriteSheetBitmap ?: return offset
        return Offset(
            x = offset.x.coerceIn(0f, bitmap.width.toFloat()),
            y = offset.y.coerceIn(0f, bitmap.height.toFloat()),
        )
    }

    fun beginSelection(anchor: Offset) {
        val clamped = clampToImage(anchor)
        selectionAnchor = clamped
        _uiState.update { current ->
            current.copy(selection = SelectionArea(x = clamped.x, y = clamped.y, width = 0f, height = 0f), activeHandle = null)
        }
    }

    fun updateSelectionArea(current: Offset) {
        val anchor = selectionAnchor ?: return
        val bitmap = spriteSheetBitmap ?: return
        val clamped = clampToImage(current)
        val dx = clamped.x - anchor.x
        val dy = clamped.y - anchor.y
        var width = kotlin.math.abs(dx)
        var height = kotlin.math.abs(dy)
        val ratio = currentAspectRatio()
        if (ratio > 0f) {
            height = width * ratio
        }
        var x = if (dx >= 0) anchor.x else anchor.x - width
        var y = if (dy >= 0) anchor.y else anchor.y - height
        width = width.coerceAtLeast(1f)
        height = height.coerceAtLeast(1f)
        val maxWidth = (bitmap.width - x).coerceAtLeast(1f)
        val maxHeight = (bitmap.height - y).coerceAtLeast(1f)
        width = width.coerceIn(1f, maxWidth)
        height = height.coerceIn(1f, maxHeight)
        x = x.coerceIn(0f, bitmap.width - width)
        y = y.coerceIn(0f, bitmap.height - height)
        _uiState.update { current ->
            current.copy(selection = SelectionArea(x = x, y = y, width = width, height = height))
        }
    }

    fun endSelection() {
        selectionAnchor = null
    }

    fun applySelectionToBox() {
        val selection = _uiState.value.selection ?: return
        val bitmap = spriteSheetBitmap ?: return
        val selected = _uiState.value.boxes.getOrNull(_uiState.value.selectedBoxIndex) ?: return
        val width = selection.width.coerceIn(1f, bitmap.width.toFloat())
        val height = selection.height.coerceIn(1f, bitmap.height.toFloat())
        val x = selection.x.coerceIn(0f, bitmap.width - width)
        val y = selection.y.coerceIn(0f, bitmap.height - height)
        replaceBox(selected.copy(x = x, y = y, width = width, height = height))
    }

    fun copySelectionToClipboard(manager: ClipboardManager) {
        val selection = _uiState.value.selection ?: _uiState.value.boxes.getOrNull(_uiState.value.selectedBoxIndex)?.toSelection()
        selection ?: return
        val json = gson.toJson(selection.toBox(index = _uiState.value.selectedBoxIndex))
        manager.setText(AnnotatedString(json))
    }

    fun pasteSelectionFromClipboard(manager: ClipboardManager): Boolean {
        val json = manager.getText()?.text ?: return false
        val parsed = runCatching { gson.fromJson(json, SpriteBox::class.java) }.getOrNull() ?: return false
        val clamped = clampBox(parsed)
        replaceOrAddBox(clamped)
        _uiState.update { current -> current.copy(selection = SelectionArea(clamped.x, clamped.y, clamped.width, clamped.height)) }
        return true
    }

    private fun clampBox(box: SpriteBox): SpriteBox {
        val bitmap = spriteSheetBitmap ?: return box
        val width = box.width.coerceIn(1f, bitmap.width.toFloat())
        val height = box.height.coerceIn(1f, bitmap.height.toFloat())
        val x = box.x.coerceIn(0f, bitmap.width - width)
        val y = box.y.coerceIn(0f, bitmap.height - height)
        return box.copy(x = x, y = y, width = width, height = height)
    }

    private fun replaceOrAddBox(box: SpriteBox) {
        val state = _uiState.value
        val target = state.selectedBoxIndex
        val updated = if (target in state.boxes.indices) {
            state.boxes.toMutableList().apply { this[target] = box.copy(index = target) }
        } else {
            state.boxes + box.copy(index = state.boxes.size)
        }
        _uiState.update { current -> current.copy(boxes = updated) }
    }

    fun autoSearchSingle() {
        val state = _uiState.value
        if (state.boxes.size < 2) return
        val focusIndex = state.selectedBoxIndex.coerceIn(0, state.boxes.lastIndex - 1)
        startAnalysis(focusIndex = focusIndex, applyOffsetsForAll = false)
    }

    fun autoSearchAll() {
        if (_uiState.value.boxes.size < 2) return
        startAnalysis(focusIndex = null, applyOffsetsForAll = true)
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        _isAnalyzing.value = false
    }

    fun resetBoxes() {
        val bitmap = spriteSheetBitmap ?: return
        updateState {
            copy(
                boxes = SpriteDebugState.defaultBoxes(IntSize(bitmap.width, bitmap.height), spriteSheetConfig),
                selectedBoxIndex = 0,
            )
        }
    }

    private fun startAnalysis(focusIndex: Int?, applyOffsetsForAll: Boolean) {
        val frames = extractFrameBitmaps()
        if (frames.size < 2) return
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch(defaultDispatcher) {
            _isAnalyzing.value = true
            try {
                val roiWidth = frames.first().width
                val roiHeight = frames.first().height
                val centerX = roiWidth / 2
                val centerY = roiHeight / 2
                val searchRadius = _uiState.value.searchRadius.toInt().coerceAtLeast(1)
                val threshold = (_uiState.value.sobelThreshold * 1024f).coerceAtLeast(1f)
                val result = SpriteAnalysis.analyzeConsecutiveFrames(
                    frames = frames,
                    centerX = centerX,
                    centerY = centerY,
                    roiWidth = roiWidth,
                    roiHeight = roiHeight,
                    searchRadius = searchRadius,
                    threshold = threshold,
                    useIoU = true,
                    dispatcher = defaultDispatcher,
                )
                applyAnalysisResult(
                    result = result,
                    highlightIndex = focusIndex,
                    applyOffsets = true,
                    applyOffsetsForAll = applyOffsetsForAll,
                    focusIndex = focusIndex,
                )
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun applyStroke(positionOnImage: Offset) {
        val bitmap = spriteSheetBitmap ?: return
        val state = _uiState.value
        val selected = state.boxes.getOrNull(state.selectedBoxIndex) ?: return
        val mode = state.editingMode
        if (mode == SpriteEditMode.None) return

        if (positionOnImage.x !in selected.x..(selected.x + selected.width) ||
            positionOnImage.y !in selected.y..(selected.y + selected.height)
        ) {
            return
        }
        pushUndo(bitmap)
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = AndroidCanvas(mutableBitmap)
        val paint = Paint().apply {
            color = if (mode == SpriteEditMode.Pen) android.graphics.Color.WHITE else android.graphics.Color.TRANSPARENT
            style = Paint.Style.FILL
            strokeWidth = state.brushSize
            isAntiAlias = true
            if (mode == SpriteEditMode.Eraser) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
        }
        canvas.drawCircle(positionOnImage.x, positionOnImage.y, state.brushSize, paint)
        spriteSheetBitmap = mutableBitmap
        _sheetBitmap.value = mutableBitmap.asImageBitmap()
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        spriteSheetBitmap?.let { redoStack.addLast(it.copy(Bitmap.Config.ARGB_8888, true)) }
        spriteSheetBitmap = if (undoStack.isNotEmpty()) undoStack.removeLast() else null
        _sheetBitmap.value = spriteSheetBitmap?.asImageBitmap()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        spriteSheetBitmap?.let { undoStack.addLast(it.copy(Bitmap.Config.ARGB_8888, true)) }
        spriteSheetBitmap = if (redoStack.isNotEmpty()) redoStack.removeLast() else null
        _sheetBitmap.value = spriteSheetBitmap?.asImageBitmap()
    }

    fun previewFrames(): List<ImageBitmap> {
        val bitmap = spriteSheetBitmap ?: return emptyList()
        return _uiState.value.boxes.mapNotNull { box ->
            val x = box.x.toInt().coerceAtLeast(0).coerceAtMost(bitmap.width - 1)
            val y = box.y.toInt().coerceAtLeast(0).coerceAtMost(bitmap.height - 1)
            val remainingWidth = bitmap.width - x
            val remainingHeight = bitmap.height - y
            if (remainingWidth <= 0 || remainingHeight <= 0) return@mapNotNull null
            val maxWidth = remainingWidth.coerceAtLeast(1)
            val maxHeight = remainingHeight.coerceAtLeast(1)
            val width = box.width.toInt().coerceAtLeast(1).coerceAtMost(maxWidth)
            val height = box.height.toInt().coerceAtLeast(1).coerceAtMost(maxHeight)
            if (width <= 0 || height <= 0) return@mapNotNull null
            runCatching {
                Bitmap.createBitmap(bitmap, x, y, width, height).asImageBitmap()
            }.getOrNull()
        }
    }

    private fun extractFrameBitmaps(): List<Bitmap> {
        val bitmap = spriteSheetBitmap ?: return emptyList()
        return _uiState.value.boxes.mapNotNull { box ->
            val x = box.x.toInt().coerceAtLeast(0).coerceAtMost(bitmap.width - 1)
            val y = box.y.toInt().coerceAtLeast(0).coerceAtMost(bitmap.height - 1)
            val remainingWidth = bitmap.width - x
            val remainingHeight = bitmap.height - y
            if (remainingWidth <= 0 || remainingHeight <= 0) return@mapNotNull null
            val maxWidth = remainingWidth.coerceAtLeast(1)
            val maxHeight = remainingHeight.coerceAtLeast(1)
            val width = box.width.toInt().coerceAtLeast(1).coerceAtMost(maxWidth)
            val height = box.height.toInt().coerceAtLeast(1).coerceAtMost(maxHeight)
            if (width <= 0 || height <= 0) return@mapNotNull null
            runCatching { Bitmap.createBitmap(bitmap, x, y, width, height) }.getOrNull()
        }
    }

    private fun snapDelta(delta: Offset, step: Int): Offset {
        val snappedX = (delta.x / step).toInt() * step
        val snappedY = (delta.y / step).toInt() * step
        return Offset(snappedX.toFloat(), snappedY.toFloat())
    }

    private fun replaceBox(box: SpriteBox) {
        val state = _uiState.value
        val targetIndex = state.selectedBoxIndex
        val existing = state.boxes.getOrNull(targetIndex) ?: return
        val updated = state.boxes.toMutableList().apply { this[targetIndex] = box.copy(index = existing.index) }
        updateState { copy(boxes = updated) }
    }

    private fun initializeState(): SpriteDebugState {
        val targetSize = IntSize(DEFAULT_SPRITE_SIZE, DEFAULT_SPRITE_SIZE)
        return runCatching { savedStateHandle.get<SpriteDebugState>(KEY_STATE) }
            .onFailure { throwable ->
                Log.w(SPRITE_DEBUG_TAG, "Failed to restore sprite debug state. Resetting to default.", throwable)
                savedStateHandle.remove<SpriteDebugState>(KEY_STATE)
            }
            .getOrNull()
            .orEmptyState()
            .normalizeToDefaultConfig(targetSize)
            .ensureBoxes(targetSize)
    }

    private fun updateState(block: SpriteDebugState.() -> SpriteDebugState) {
        _uiState.update { current ->
            block(current).ensureBoxes(targetSpriteSize())
        }
        persistStateAsync()
    }

    private fun applyOffsetsFromResult(
        result: SpriteAnalysis.SpriteAnalysisResult,
        focusIndex: Int?,
        applyOffsetsForAll: Boolean,
    ) {
        val bitmap = spriteSheetBitmap ?: return
        val applicable = if (applyOffsetsForAll) {
            result.bestOffsets.indices.toSet()
        } else {
            focusIndex?.let { setOf(it) } ?: emptySet()
        }
        if (applicable.isEmpty()) return
        updateState {
            val updated = boxes.mapIndexed { index, box ->
                val offsetIndex = index - 1
                if (offsetIndex in applicable) {
                    val offset = result.bestOffsets.getOrNull(offsetIndex)
                    if (offset != null) {
                        val maxX = bitmap.width - box.width
                        val maxY = bitmap.height - box.height
                        box.copy(
                            x = (box.x + offset.dx).coerceIn(0f, maxX),
                            y = (box.y + offset.dy).coerceIn(0f, maxY),
                        )
                    } else {
                        box
                    }
                } else {
                    box
                }
            }
            copy(boxes = updated)
        }
    }

    private fun applyAnalysisResult(
        result: SpriteAnalysis.SpriteAnalysisResult,
        highlightIndex: Int? = null,
        persist: Boolean = true,
        applyOffsets: Boolean = false,
        applyOffsetsForAll: Boolean = true,
        focusIndex: Int? = null,
    ) {
        if (applyOffsets) {
            applyOffsetsFromResult(result, focusIndex, applyOffsetsForAll)
        }
        _analysisResult.value = result
        val scores = result.scores.mapIndexed { index, score ->
            val offset = result.bestOffsets.getOrNull(index)
            SpriteMatchScore(
                from = index + 1,
                to = index + 2,
                score = score,
                offset = offset?.let { MatchOffset(it.dx, it.dy) } ?: MatchOffset(),
            )
        }
        val targetHighlight = highlightIndex ?: result.scores.withIndex().maxByOrNull { it.value }?.index
        val bestIndices = targetHighlight?.let { listOf(it + 1) } ?: emptyList()
        _uiState.update { current ->
            current.copy(matchScores = scores, bestMatchIndices = bestIndices)
        }
        persistStateAsync()
        if (persist) {
            persistAnalysisResult(result)
        }
    }

    private suspend fun restorePersistedState() {
        val persistedState = dataStore.readState()
        val persistedResult = dataStore.readAnalysisResult()
        val targetSize = targetSpriteSize()
        if (persistedState != null) {
            _uiState.update { persistedState.normalizeToDefaultConfig(targetSize).ensureBoxes(targetSize) }
        }
        if (persistedResult != null) {
            applyAnalysisResult(persistedResult, persist = false)
        }
    }

    private suspend fun observeSpriteSheetConfig() {
        settingsPreferences.spriteSheetConfig.collect { persistedConfig ->
            val defaultConfig = SpriteSheetConfig.default3x3()
            if (persistedConfig != defaultConfig) {
                settingsPreferences.resetSpriteSheetConfig()
            }
            val targetSize = targetSpriteSize()
            val defaultBoxes = SpriteDebugState.defaultBoxes(targetSize, defaultConfig)
            _uiState.update {
                it.copy(
                    spriteSheetConfig = defaultConfig,
                    boxes = defaultBoxes,
                    selectedBoxIndex = 0,
                ).ensureBoxes(targetSize)
            }
        }
    }

    private fun persistStateAsync(state: SpriteDebugState = _uiState.value) {
        viewModelScope.launch(ioDispatcher) { dataStore.saveState(state) }
    }

    private fun targetSpriteSize(): IntSize = spriteSheetBitmap?.let { IntSize(it.width, it.height) } ?: IntSize(DEFAULT_SPRITE_SIZE, DEFAULT_SPRITE_SIZE)

    private fun persistAnalysisResult(result: SpriteAnalysis.SpriteAnalysisResult) {
        viewModelScope.launch(ioDispatcher) { dataStore.saveAnalysisResult(result) }
    }

    private fun clearAnalysis() {
        _analysisResult.value = null
        _uiState.update { it.copy(matchScores = emptyList(), bestMatchIndices = emptyList()) }
        persistStateAsync()
        viewModelScope.launch(ioDispatcher) { dataStore.clearAnalysis() }
    }

    fun importStateFromJson(json: String): Boolean {
        val parsed = runCatching { gson.fromJson(json, SpriteDebugState::class.java) }.getOrNull() ?: return false
        _uiState.update { parsed.ensureBoxes(spriteSheetBitmap?.let { IntSize(it.width, it.height) } ?: IntSize(DEFAULT_SPRITE_SIZE, DEFAULT_SPRITE_SIZE)) }
        persistStateAsync()
        return true
    }

    fun importAnalysisResultFromJson(json: String): Boolean {
        val parsed = runCatching { gson.fromJson(json, SpriteAnalysis.SpriteAnalysisResult::class.java) }.getOrNull() ?: return false
        applyAnalysisResult(parsed, applyOffsets = true, applyOffsetsForAll = true, focusIndex = null)
        return true
    }

    private fun pushUndo(current: Bitmap) {
        undoStack.addLast(current.copy(Bitmap.Config.ARGB_8888, true))
        if (undoStack.size > 5) {
            undoStack.removeFirst()
        }
    }

    companion object {
        private const val KEY_STATE = "sprite_debug_state"

        fun provideFactory(owner: SavedStateRegistryOwner, context: Context): ViewModelProvider.Factory =
            object : AbstractSavedStateViewModelFactory(owner, null) {
                override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
                    return SpriteDebugViewModel(
                        savedStateHandle = handle,
                        dataStore = SpriteDebugPreferences(context.applicationContext),
                        settingsPreferences = SettingsPreferences(context.applicationContext),
                    ) as T
                }
            }
    }

    private fun SpriteDebugState.ensureBoxes(targetSize: IntSize): SpriteDebugState {
        val expectedSize = max(1, spriteSheetConfig.boxes.takeIf { it.isNotEmpty() }?.size ?: (spriteSheetConfig.rows * spriteSheetConfig.cols))
        val needsDefaultBoxes = boxes.size < expectedSize || boxes.any { it.width <= 0f || it.height <= 0f }
        val refreshedBoxes = if (needsDefaultBoxes) {
            val defaults = SpriteDebugState.defaultBoxes(targetSize, spriteSheetConfig)
            Log.d(SPRITE_DEBUG_TAG, "Normalized ROI to ${defaults.size} items (synced with spriteSheetConfig)")
            defaults
        } else {
            Log.d(SPRITE_DEBUG_TAG, "Normalized ROI to ${boxes.size} items")
            boxes
        }
        val constrainedBoxes = refreshedBoxes.map { box ->
            val maxWidth = targetSize.width.toFloat().coerceAtLeast(1f)
            val maxHeight = targetSize.height.toFloat().coerceAtLeast(1f)
            val width = box.width.coerceAtLeast(1f).coerceAtMost(maxWidth)
            val height = box.height.coerceAtLeast(1f).coerceAtMost(maxHeight)
            val x = box.x.coerceIn(0f, (maxWidth - width).coerceAtLeast(0f))
            val y = box.y.coerceIn(0f, (maxHeight - height).coerceAtLeast(0f))
            box.copy(
                x = x,
                y = y,
                width = width.coerceAtMost((maxWidth - x).coerceAtLeast(1f)),
                height = height.coerceAtMost((maxHeight - y).coerceAtLeast(1f)),
            )
        }
        val boundedIndex = selectedBoxIndex.coerceIn(0, constrainedBoxes.lastIndex.coerceAtLeast(0))
        return copy(boxes = constrainedBoxes, selectedBoxIndex = boundedIndex)
    }

    private fun SpriteDebugState.normalizeToDefaultConfig(targetSize: IntSize): SpriteDebugState {
        val defaultConfig = SpriteSheetConfig.default3x3()
        val defaultBoxes = SpriteDebugState.defaultBoxes(targetSize, defaultConfig)
        val boundedIndex = selectedBoxIndex.coerceIn(0, defaultBoxes.lastIndex.coerceAtLeast(0))
        return copy(
            spriteSheetConfig = defaultConfig,
            boxes = defaultBoxes,
            selectedBoxIndex = boundedIndex,
        )
    }

private fun SpriteDebugState?.orEmptyState(): SpriteDebugState = this ?: SpriteDebugState()
}

private sealed interface SpriteBitmapLoadState {
    object Loading : SpriteBitmapLoadState
    data class Success(val bitmap: Bitmap) : SpriteBitmapLoadState
    data class Error(val throwable: Throwable? = null) : SpriteBitmapLoadState
}

private suspend fun loadSpriteBitmap(context: Context): Bitmap? = withContext(Dispatchers.IO) {
    val resources = context.resources
    val spriteBitmap = BitmapFactory.decodeResource(resources, R.drawable.lami_sprite_3x3_288)
    if (spriteBitmap != null && spriteBitmap.width > 0 && spriteBitmap.height > 0) {
        Log.d(SPRITE_DEBUG_TAG, "Loaded sprite sheet lami_sprite_3x3_288 (${spriteBitmap.width}x${spriteBitmap.height})")
        return@withContext spriteBitmap
    }
    Log.w(SPRITE_DEBUG_TAG, "Failed to decode sprite sheet. Sprite bitmap is null or empty.")
    return@withContext null
}

@Composable
fun SpriteDebugScreen(viewModel: SpriteDebugViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetBitmap by viewModel.sheetBitmap.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val reloadSignal by viewModel.reloadSignal.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }
    var stateJson by remember(uiState) { mutableStateOf(gson.toJson(uiState)) }
    var analysisJson by remember(analysisResult) { mutableStateOf(analysisResult?.let { gson.toJson(it) }.orEmpty()) }
    val spriteLoadState by produceState<SpriteBitmapLoadState>(
        initialValue = SpriteBitmapLoadState.Loading,
        key1 = context,
        key2 = reloadSignal,
    ) {
        value = SpriteBitmapLoadState.Loading
        val bitmap = try {
            loadSpriteBitmap(context)
        } catch (exception: Exception) {
            Log.e(SPRITE_DEBUG_TAG, "Failed to load sprite sheet resource", exception)
            value = SpriteBitmapLoadState.Error(exception)
            return@produceState
        }
        if (bitmap != null) {
            viewModel.setSpriteSheet(bitmap)
            value = SpriteBitmapLoadState.Success(bitmap)
        } else {
            value = SpriteBitmapLoadState.Error()
        }
    }
    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.loadSpriteSheetFromUri(context, uri) { success ->
            if (!success) {
                scope.launch { snackbarHostState.showSnackbar("ファイルの読み込みに失敗しました") }
            }
        }
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    var rememberedState by rememberSaveable { mutableStateOf(uiState) }

    LaunchedEffect(uiState) {
        rememberedState = uiState
    }

    LaunchedEffect(rememberedState.selectedBoxIndex) { viewModel.selectBox(rememberedState.selectedBoxIndex) }
    LaunchedEffect(rememberedState.step) { viewModel.updateStep(rememberedState.step) }

    fun showError(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val loadedSpriteBitmap = (spriteLoadState as? SpriteBitmapLoadState.Success)?.bitmap
    val resolvedBitmap = remember(sheetBitmap, loadedSpriteBitmap) {
        (sheetBitmap ?: loadedSpriteBitmap?.asImageBitmap())?.takeIf { it.width > 0 && it.height > 0 }
    }
    val shouldShowLoading = resolvedBitmap == null && spriteLoadState is SpriteBitmapLoadState.Loading
    val shouldShowError = resolvedBitmap == null && spriteLoadState is SpriteBitmapLoadState.Error
    LaunchedEffect(shouldShowError, sheetBitmap, loadedSpriteBitmap) {
        if (shouldShowError) {
            Log.w(
                SPRITE_DEBUG_TAG,
                "Canvas initialization failed. sheetBitmap=${sheetBitmap?.width}x${sheetBitmap?.height}, loaded=${loadedSpriteBitmap?.width}x${loadedSpriteBitmap?.height}",
            )
            snackbarHostState.showSnackbar("Canvas初期化に失敗しました")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("調整") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("編集") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("プレビュー") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("ギャラリー") })
            }
            when (selectedTab) {
                0 -> when {
                    shouldShowLoading -> LoadingCanvasPlaceholder()
                    shouldShowError -> SpriteLoadError(
                        onRetry = viewModel::reloadSpriteSheet,
                        onOpenDocument = { openDocumentLauncher.launch(arrayOf("image/*")) },
                    )
                    else -> AdjustTabContent(
                        uiState = uiState,
                        rememberedState = rememberedState,
                        spriteBitmap = resolvedBitmap!!,
                        onRememberedStateChange = { rememberedState = it },
                        viewModel = viewModel,
                        isAnalyzing = isAnalyzing,
                        clipboardManager = clipboardManager,
                        snackbarHostState = snackbarHostState,
                        scope = scope,
                        stateJson = stateJson,
                        onStateJsonChange = { stateJson = it },
                        analysisJson = analysisJson,
                        onAnalysisJsonChange = { analysisJson = it },
                        onError = ::showError,
                    )
                }
                1 -> when {
                    shouldShowLoading -> LoadingCanvasPlaceholder()
                    shouldShowError -> SpriteLoadError(
                        onRetry = viewModel::reloadSpriteSheet,
                        onOpenDocument = { openDocumentLauncher.launch(arrayOf("image/*")) },
                    )
                    else -> EditTabContent(
                        uiState = uiState,
                        spriteBitmap = resolvedBitmap!!,
                        onRememberedStateChange = { rememberedState = it },
                        viewModel = viewModel,
                    )
                }
                2 -> when {
                    shouldShowLoading -> LoadingCanvasPlaceholder()
                    shouldShowError -> SpriteLoadError(
                        onRetry = viewModel::reloadSpriteSheet,
                        onOpenDocument = { openDocumentLauncher.launch(arrayOf("image/*")) },
                    )
                    else -> PreviewTabContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        rememberedState = rememberedState,
                        sheetBitmap = resolvedBitmap,
                        isAnalyzing = isAnalyzing,
                        onRememberedStateChange = { rememberedState = it },
                    )
                }
                else -> if (shouldShowError) {
                    SpriteLoadError(
                        onRetry = viewModel::reloadSpriteSheet,
                        onOpenDocument = { openDocumentLauncher.launch(arrayOf("image/*")) },
                    )
                } else {
                    GalleryTabContent()
                }
            }
        }
    }
}

@Composable
private fun AdjustTabContent(
    uiState: SpriteDebugState,
    rememberedState: SpriteDebugState,
    spriteBitmap: ImageBitmap,
    onRememberedStateChange: (SpriteDebugState) -> Unit,
    viewModel: SpriteDebugViewModel,
    isAnalyzing: Boolean,
    clipboardManager: ClipboardManager,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    stateJson: String,
    onStateJsonChange: (String) -> Unit,
    analysisJson: String,
    onAnalysisJsonChange: (String) -> Unit,
    onError: (String) -> Unit,
) {
    if (spriteBitmap.width <= 0 || spriteBitmap.height <= 0) {
        LoadingCanvasPlaceholder(modifier = Modifier.fillMaxSize())
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        var previewFrames by remember { mutableStateOf(emptyList<ImageBitmap>()) }
        var selectedPreview by remember { mutableStateOf<ImageBitmap?>(null) }
        var lastLoggedIndex by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(uiState.selectedBoxIndex, uiState.boxes, spriteBitmap) {
            val frames = viewModel.previewFrames()
            previewFrames = frames
            selectedPreview = frames.getOrNull(uiState.selectedBoxIndex)
            if (selectedPreview == null && lastLoggedIndex != uiState.selectedBoxIndex) {
                Log.w(SPRITE_DEBUG_TAG, "画像プレビューの生成に失敗しました index=${uiState.selectedBoxIndex}")
                lastLoggedIndex = uiState.selectedBoxIndex
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val maxCanvasWidth = maxWidth - 32.dp
            val maxCanvasHeight = maxHeight - 48.dp
            val canvasSize = remember(maxCanvasWidth, maxCanvasHeight) { minOf(maxCanvasWidth, maxCanvasHeight * 1.6f) }
            Box(
                modifier = Modifier
                    .width(canvasSize)
                    .height(canvasSize / 1.6f)
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                SpriteSheetCanvas(
                    uiState = uiState,
                    spriteBitmap = spriteBitmap,
                    onDragBox = { deltaImage -> viewModel.updateBoxPosition(deltaImage) },
                    onStroke = { offset -> viewModel.applyStroke(offset) },
                    onStartSelection = viewModel::beginSelection,
                    onUpdateSelection = viewModel::updateSelectionArea,
                    onEndSelection = viewModel::endSelection,
                    onBoxSelected = { index -> onRememberedStateChange(rememberedState.copy(selectedBoxIndex = index)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "プレビュー", style = MaterialTheme.typography.titleMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    val preview = selectedPreview
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = "選択中のボックスプレビュー",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(text = "画像が未設定", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "3x3 プレビューグリッド", style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val gridRange = 0 until 9
                        gridRange.chunked(3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { index ->
                                    val frame = previewFrames.getOrNull(index)
                                    val isSelected = uiState.selectedBoxIndex == index
                                    val borderColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                    val containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(containerColor)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = borderColor,
                                                shape = RoundedCornerShape(8.dp),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        frame?.let {
                                            Image(
                                                bitmap = it,
                                                contentDescription = "Box${index + 1} プレビュー",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } ?: Text(
                                            text = "未設定",
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(4.dp),
                                        )
                                        Text(
                                            text = "Box${index + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ControlPanel(
            uiState = uiState,
            onSelectBox = { index -> onRememberedStateChange(rememberedState.copy(selectedBoxIndex = index)) },
            onNudge = { dx, dy -> viewModel.nudgeSelected(dx, dy) },
            onUpdateX = { value -> viewModel.updateBoxCoordinate(x = value, y = null) },
            onUpdateY = { value -> viewModel.updateBoxCoordinate(x = null, y = value) },
            onStepChange = { step -> onRememberedStateChange(rememberedState.copy(step = step)) },
            onSnapToggle = { viewModel.toggleSnap() },
            onSearchRadiusChange = { viewModel.updateSearchRadius(it) },
            onThresholdChange = { viewModel.updateThreshold(it) },
            onAutoSearchOne = { viewModel.autoSearchSingle() },
            onAutoSearchAll = { viewModel.autoSearchAll() },
            onReset = { viewModel.resetBoxes() },
            isAnalyzing = isAnalyzing,
            onCancelAnalysis = { viewModel.cancelAnalysis() },
        )
        MatchList(uiState = uiState)
        DebugPersistencePanel(
            stateJson = stateJson,
            onStateJsonChange = onStateJsonChange,
            analysisJson = analysisJson,
            onAnalysisJsonChange = onAnalysisJsonChange,
            onApplyState = { json ->
                if (viewModel.importStateFromJson(json)) {
                    scope.launch { snackbarHostState.showSnackbar("SpriteDebugState を復元しました") }
                } else {
                    onError("State JSON の解析に失敗しました")
                }
            },
            onApplyAnalysis = { json ->
                if (json.isBlank()) {
                    onError("計算結果 JSON が空です")
                } else if (viewModel.importAnalysisResultFromJson(json)) {
                    scope.launch { snackbarHostState.showSnackbar("解析結果を適用しました") }
                } else {
                    onError("解析結果 JSON の解析に失敗しました")
                }
            },
            clipboardManager = clipboardManager,
            snackbarHostState = snackbarHostState,
            scope = scope,
        )
    }
}

@Composable
private fun EditTabContent(
    uiState: SpriteDebugState,
    spriteBitmap: ImageBitmap,
    onRememberedStateChange: (SpriteDebugState) -> Unit,
    viewModel: SpriteDebugViewModel,
) {
    val clipboardManager = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val maxCanvasWidth = maxWidth - 32.dp
            val maxCanvasHeight = maxHeight - 48.dp
            val canvasWidth = remember(maxCanvasWidth, maxCanvasHeight) { minOf(maxCanvasWidth, maxCanvasHeight * 1.6f) }
            Box(
                modifier = Modifier
                    .width(canvasWidth)
                    .height(canvasWidth / 1.6f),
            ) {
                SpriteSheetCanvas(
                    uiState = uiState,
                    spriteBitmap = spriteBitmap,
                    onDragBox = { deltaImage -> viewModel.updateBoxPosition(deltaImage) },
                    onStroke = { offset -> viewModel.applyStroke(offset) },
                    onStartSelection = viewModel::beginSelection,
                    onUpdateSelection = viewModel::updateSelectionArea,
                    onEndSelection = viewModel::endSelection,
                    onBoxSelected = { index -> onRememberedStateChange(uiState.copy(selectedBoxIndex = index)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "編集ツール", style = MaterialTheme.typography.titleMedium)
                EditToolbar(
                    editingMode = uiState.editingMode,
                    brushSize = uiState.brushSize,
                    onEditingModeChange = { mode ->
                        viewModel.setEditingMode(mode)
                        if (mode == SpriteEditMode.None) {
                            viewModel.endSelection()
                        }
                    },
                    onBrushSizeChange = viewModel::setBrushSize,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                )
                SelectionActions(
                    hasSelection = uiState.selection != null,
                    onApplySelection = viewModel::applySelectionToBox,
                    onCopy = { viewModel.copySelectionToClipboard(clipboardManager) },
                    onPaste = { viewModel.pasteSelectionFromClipboard(clipboardManager) },
                )
            }
        }
    }
}

@Composable
private fun SelectionActions(
    hasSelection: Boolean,
    onApplySelection: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onApplySelection, enabled = hasSelection, modifier = Modifier.weight(1f)) {
            Text(text = "選択範囲を適用")
        }
        Button(onClick = onCopy, modifier = Modifier.weight(1f)) {
            Text(text = "コピー")
        }
        Button(onClick = { onPaste() }, modifier = Modifier.weight(1f)) {
            Text(text = "ペースト")
        }
    }
}

@Composable
private fun PreviewTabContent(
    uiState: SpriteDebugState,
    viewModel: SpriteDebugViewModel,
    rememberedState: SpriteDebugState,
    sheetBitmap: ImageBitmap?,
    isAnalyzing: Boolean,
    onRememberedStateChange: (SpriteDebugState) -> Unit,
) {
    val hasSheetBitmap = sheetBitmap?.let { it.width > 0 && it.height > 0 } == true
    if (!hasSheetBitmap) {
        LoadingCanvasPlaceholder(modifier = Modifier.fillMaxSize())
        return
    }
    val clipboardManager = LocalClipboardManager.current
    var playing by rememberSaveable { mutableStateOf(false) }
    var frameIndex by rememberSaveable { mutableIntStateOf(0) }
    var controlsExpanded by rememberSaveable { mutableStateOf(true) }
    var previewListExpanded by rememberSaveable { mutableStateOf(false) }
    val frames = remember(uiState.boxes, rememberedState.previewSpeedMs, sheetBitmap) { viewModel.previewFrames() }

    LaunchedEffect(playing, frames.size, rememberedState.previewSpeedMs) {
        while (playing && frames.isNotEmpty()) {
            delay(rememberedState.previewSpeedMs)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    val glow by rememberInfiniteTransition(label = "preview-glow").animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = androidx.compose.animation.core.tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "glow",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        val isWideLayout = maxWidth >= 960.dp
        val spacing = 12.dp

        val previewSection: @Composable () -> Unit = {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = "プレビュー", style = MaterialTheme.typography.titleMedium)
                    if (frames.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .border(2.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = glow), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = frames[frameIndex % frames.size],
                                contentDescription = "frame",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                            if (uiState.onionSkin && frames.size > 1) {
                                val previous = frames[(frameIndex - 1 + frames.size) % frames.size]
                                val next = frames[(frameIndex + 1) % frames.size]
                                Image(bitmap = previous, contentDescription = "onion-prev", modifier = Modifier.fillMaxSize(), alpha = 0.25f)
                                Image(bitmap = next, contentDescription = "onion-next", modifier = Modifier.fillMaxSize(), alpha = 0.25f)
                            }
                            if (uiState.showCenterLine) {
                                val previewCenterLineColor = MaterialTheme.colorScheme.secondary
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    drawLine(
                                        color = previewCenterLineColor,
                                        start = Offset(size.width / 2f, 0f),
                                        end = Offset(size.width / 2f, size.height),
                                        strokeWidth = 2f,
                                    )
                                    drawLine(
                                        color = previewCenterLineColor,
                                        start = Offset(0f, size.height / 2f),
                                        end = Offset(size.width, size.height / 2f),
                                        strokeWidth = 2f,
                                    )
                                }
                            }
                        }
                    } else {
                        Text(text = "ROIが見つかりません", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(onClick = { playing = !playing }) {
                            Icon(imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "play")
                        }
                        Text(text = "速度 ${rememberedState.previewSpeedMs}ms", modifier = Modifier.weight(1f))
                        IconButton(onClick = { controlsExpanded = !controlsExpanded }) {
                            Icon(
                                imageVector = if (controlsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = "プレビューパネルを切り替え",
                            )
                        }
                    }
                    AnimatedVisibility(visible = controlsExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Slider(
                                value = rememberedState.previewSpeedMs.toFloat(),
                                onValueChange = {
                                    val speed = it.toLong()
                                    onRememberedStateChange(rememberedState.copy(previewSpeedMs = speed))
                                    viewModel.updatePreviewSpeed(speed)
                                },
                                valueRange = 120f..2000f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(text = "オニオンスキン")
                                    Switch(checked = uiState.onionSkin, onCheckedChange = { viewModel.toggleOnionSkin(it) })
                                }
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(text = "中心線")
                                    Switch(checked = uiState.showCenterLine, onCheckedChange = { viewModel.toggleCenterLine(it) })
                                }
                            }
                            TextButton(onClick = { previewListExpanded = !previewListExpanded }) {
                                Icon(
                                    imageVector = if (previewListExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "プレビュー順を開閉",
                                )
                                Text(text = "プレビュー順を表示")
                            }
                            AnimatedVisibility(visible = previewListExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    uiState.boxes.forEach { box ->
                                        Text(text = "Frame ${box.index + 1} (${box.x.toInt()}, ${box.y.toInt()})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val editorSection: @Composable () -> Unit = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    contentAlignment = Alignment.Center,
                ) {
                    val maxCanvasWidth = maxWidth - 32.dp
                    val maxCanvasHeight = maxHeight - 48.dp
                    val canvasWidth = remember(maxCanvasWidth, maxCanvasHeight) { minOf(maxCanvasWidth, maxCanvasHeight * 1.6f) }
                    Box(
                        modifier = Modifier
                            .width(canvasWidth)
                            .height(canvasWidth / 1.6f),
                    ) {
                        SpriteSheetCanvas(
                            uiState = uiState,
                            spriteBitmap = sheetBitmap!!,
                            onDragBox = { deltaImage -> viewModel.updateBoxPosition(deltaImage) },
                            onStroke = { offset -> viewModel.applyStroke(offset) },
                            onStartSelection = viewModel::beginSelection,
                            onUpdateSelection = viewModel::updateSelectionArea,
                            onEndSelection = viewModel::endSelection,
                            onBoxSelected = { index ->
                                viewModel.selectBox(index)
                                onRememberedStateChange(rememberedState.copy(selectedBoxIndex = index))
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(text = "編集ツール", style = MaterialTheme.typography.titleMedium)
                        EditToolbar(
                            editingMode = uiState.editingMode,
                            brushSize = uiState.brushSize,
                            onEditingModeChange = { mode ->
                                viewModel.setEditingMode(mode)
                                if (mode == SpriteEditMode.None) {
                                    viewModel.endSelection()
                                }
                            },
                            onBrushSizeChange = viewModel::setBrushSize,
                            onUndo = viewModel::undo,
                            onRedo = viewModel::redo,
                        )
                        SelectionActions(
                            hasSelection = uiState.selection != null,
                            onApplySelection = viewModel::applySelectionToBox,
                            onCopy = { viewModel.copySelectionToClipboard(clipboardManager) },
                            onPaste = { viewModel.pasteSelectionFromClipboard(clipboardManager) },
                        )
                    }
                }
                ControlPanel(
                    uiState = uiState,
                    onSelectBox = { index ->
                        viewModel.selectBox(index)
                        onRememberedStateChange(rememberedState.copy(selectedBoxIndex = index))
                    },
                    onNudge = { dx, dy -> viewModel.nudgeSelected(dx, dy) },
                    onUpdateX = { value -> viewModel.updateBoxCoordinate(x = value, y = null) },
                    onUpdateY = { value -> viewModel.updateBoxCoordinate(x = null, y = value) },
                    onStepChange = { step ->
                        onRememberedStateChange(rememberedState.copy(step = step))
                        viewModel.updateStep(step)
                    },
                    onSnapToggle = { viewModel.toggleSnap() },
                    onSearchRadiusChange = { viewModel.updateSearchRadius(it) },
                    onThresholdChange = { viewModel.updateThreshold(it) },
                    onAutoSearchOne = { viewModel.autoSearchSingle() },
                    onAutoSearchAll = { viewModel.autoSearchAll() },
                    onReset = { viewModel.resetBoxes() },
                    isAnalyzing = isAnalyzing,
                    onCancelAnalysis = { viewModel.cancelAnalysis() },
                )
            }
        }

        if (isWideLayout) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                Box(modifier = Modifier.weight(1f)) { previewSection() }
                Box(modifier = Modifier.weight(1f)) { editorSection() }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                previewSection()
                editorSection()
            }
        }
    }
}

@Composable
private fun GalleryTabContent() {
    val statuses = remember { LamiSpriteStatus.values() }
    var selectedStatusName by rememberSaveable { mutableStateOf(LamiSpriteStatus.Idle.name) }
    val selectedStatus = remember(selectedStatusName) {
        runCatching { LamiSpriteStatus.valueOf(selectedStatusName) }.getOrDefault(LamiSpriteStatus.Idle)
    }
    val statusDescriptions: Map<LamiSpriteStatus, String> = remember {
        mapOf(
            LamiSpriteStatus.Idle to "待機中のスプライトアニメーションです。",
            LamiSpriteStatus.TalkLong to "発話中のフレーム遷移を確認できます。",
            LamiSpriteStatus.TalkCalm to "リッスン状態のサンプルを再生します。",
            LamiSpriteStatus.Thinking to "思考状態のニュアンスを確認します。",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "ギャラリー (ステータスセット切替)", style = MaterialTheme.typography.titleMedium)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LamiStatusSprite(status = selectedStatus, sizeDp = 96.dp)
                    val description = statusDescriptions[selectedStatus] ?: "詳細情報なし"
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.semantics { contentDescription = description },
                    ) {
                        Text(text = "選択中: ${selectedStatus.name}", style = MaterialTheme.typography.titleMedium)
                        Text(text = description)
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(statuses) { status ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            LamiStatusSprite(status = status, sizeDp = 64.dp)
                            Text(status.name, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            TextButton(onClick = { selectedStatusName = status.name }) {
                                Text(if (selectedStatus == status) "選択中" else "選択")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpriteLoadError(
    onRetry: () -> Unit,
    onOpenDocument: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = "スプライトの読み込みに失敗しました",
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry) {
                    Text("再試行")
                }
                FilledTonalButton(onClick = onOpenDocument) {
                    Text("ファイルから読み込む")
                }
            }
        }
    }
}

@Composable
private fun LoadingCanvasPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(text = "キャンバスを初期化しています…")
        }
    }
}

@Composable
private fun SpriteSheetCanvas(
    uiState: SpriteDebugState,
    spriteBitmap: ImageBitmap,
    onDragBox: (Offset) -> Unit,
    onStroke: (Offset) -> Unit,
    onStartSelection: (Offset) -> Unit = {},
    onUpdateSelection: (Offset) -> Unit = {},
    onEndSelection: () -> Unit = {},
    onBoxSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val intrinsicSize = Size(spriteBitmap.width.toFloat(), spriteBitmap.height.toFloat())
    var layoutSize by remember { mutableStateOf(Size.Zero) }
    LaunchedEffect(intrinsicSize) {
        Log.d(
            SPRITE_DEBUG_TAG,
            "Rendering sprite sheet size=${intrinsicSize.width.toInt()}x${intrinsicSize.height.toInt()} config=${uiState.spriteSheetConfig.rows}x${uiState.spriteSheetConfig.cols}",
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.6f, matchHeightConstraintsFirst = true)
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = spriteBitmap,
            contentDescription = "Sprite sheet",
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .onSizeChanged { layoutSize = Size(it.width.toFloat(), it.height.toFloat()) },
        )

        val scale = remember(layoutSize, intrinsicSize) { calculateScale(intrinsicSize, layoutSize) }

        val selectedColor = MaterialTheme.colorScheme.primary
        val normalColor = MaterialTheme.colorScheme.outlineVariant
        val bestColor = MaterialTheme.colorScheme.tertiary
        val centerLineColor = MaterialTheme.colorScheme.error
        val selectionFillColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
        val selectionStrokeColor = MaterialTheme.colorScheme.secondary
        val isScaleValid = remember(scale) { scale.isValid() && scale.scale > 0f }
        if (!isScaleValid) {
            LaunchedEffect(scale) {
                Log.d(SPRITE_DEBUG_TAG, "Canvas rendering skipped due to invalid scale: $scale")
            }
            LoadingCanvasPlaceholder()
            return@Box
        }
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .semantics {
                    contentDescription = "Sprite sheet overlay"
                    spriteBoxCount = uiState.boxes.size
                }
                .pointerInput(uiState.selectedBoxIndex, uiState.snapToGrid, uiState.editingMode) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val imageOffset = scale.canvasToImage(offset)
                            if (!imageOffset.isFiniteOffset()) {
                                Log.w(SPRITE_DEBUG_TAG, "Ignoring non-finite drag start: offset=$offset scale=${scale.scale} imageOffset=$imageOffset")
                                return@detectDragGestures
                            }
                            when (uiState.editingMode) {
                                SpriteEditMode.Pen, SpriteEditMode.Eraser -> onStroke(imageOffset)
                                SpriteEditMode.Selection -> onStartSelection(imageOffset)
                                SpriteEditMode.None -> Unit
                            }
                        },
                        onDragEnd = {
                            if (uiState.editingMode == SpriteEditMode.Selection) {
                                onEndSelection()
                            }
                        },
                        onDragCancel = {
                            if (uiState.editingMode == SpriteEditMode.Selection) {
                                onEndSelection()
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val safeScale = scale.scale.coerceAtLeast(0.01f)
                        when (uiState.editingMode) {
                            SpriteEditMode.None -> {
                                val deltaImage = Offset(dragAmount.x / safeScale, dragAmount.y / safeScale)
                                if (!deltaImage.isFiniteOffset()) {
                                    Log.w(SPRITE_DEBUG_TAG, "Ignoring non-finite drag delta: dragAmount=$dragAmount safeScale=$safeScale")
                                    return@detectDragGestures
                                }
                                onDragBox(deltaImage)
                            }
                            SpriteEditMode.Pen, SpriteEditMode.Eraser -> {
                                val imageOffset = scale.canvasToImage(change.position)
                                if (!imageOffset.isFiniteOffset()) {
                                    Log.w(
                                        SPRITE_DEBUG_TAG,
                                        "Ignoring non-finite stroke position: position=${change.position} safeScale=$safeScale offset=${scale.offset}",
                                    )
                                    return@detectDragGestures
                                }
                                onStroke(imageOffset)
                            }
                            SpriteEditMode.Selection -> {
                                val imageOffset = scale.canvasToImage(change.position)
                                if (!imageOffset.isFiniteOffset()) {
                                    Log.w(
                                        SPRITE_DEBUG_TAG,
                                        "Ignoring non-finite selection drag: position=${change.position} safeScale=$safeScale offset=${scale.offset}",
                                    )
                                    return@detectDragGestures
                                }
                                onUpdateSelection(imageOffset)
                            }
                        }
                    }
                }
                .pointerInput(uiState.boxes, scale) {
                    detectTapGestures { tapOffset ->
                        val imageOffset = scale.canvasToImage(tapOffset)
                        if (!imageOffset.isFiniteOffset()) {
                            Log.w(SPRITE_DEBUG_TAG, "Ignoring non-finite tap: tapOffset=$tapOffset scale=${scale.scale} offset=${scale.offset}")
                            return@detectTapGestures
                        }
                        if (uiState.editingMode == SpriteEditMode.Pen || uiState.editingMode == SpriteEditMode.Eraser) {
                            onStroke(imageOffset)
                            return@detectTapGestures
                        }
                        val containingBox = uiState.boxes.minByOrNull { box ->
                            if (box.containsPoint(imageOffset)) 0f else Float.POSITIVE_INFINITY
                        }?.takeIf { it.containsPoint(imageOffset) }

                        val nearestBox = uiState.boxes.minByOrNull { box ->
                            val center = Offset(box.x + box.width / 2f, box.y + box.height / 2f)
                            hypot(center.x - imageOffset.x, center.y - imageOffset.y)
                        }

                        val targetBox = containingBox ?: nearestBox
                        targetBox?.let { onBoxSelected(it.index) }
                    }
                },
        ) {
            if (!scale.isValid()) return@Canvas
            uiState.boxes.forEach { box ->
                val topLeft = scale.imageToCanvas(Offset(box.x, box.y))
                val size = scale.imageSizeToCanvas(Size(box.width, box.height))
                val color = when {
                    box.index == uiState.selectedBoxIndex -> selectedColor
                    uiState.bestMatchIndices.contains(box.index) -> bestColor
                    else -> normalColor
                }
                drawRect(
                    color = color.copy(alpha = if (box.index == uiState.selectedBoxIndex) 0.3f else 0.18f),
                    topLeft = topLeft,
                    size = size,
                )
                drawRect(
                    color = color,
                    topLeft = topLeft,
                    size = size,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (box.index == uiState.selectedBoxIndex) 3f else 1.5f),
                )
            }
            uiState.selection?.let { selection ->
                val topLeft = scale.imageToCanvas(Offset(selection.x, selection.y))
                val size = scale.imageSizeToCanvas(Size(selection.width, selection.height))
                drawRect(
                    color = selectionFillColor,
                    topLeft = topLeft,
                    size = size,
                )
                drawRect(
                    color = selectionStrokeColor,
                    topLeft = topLeft,
                    size = size,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                )
            }
            if (uiState.showCenterLine) {
                drawLine(
                    color = centerLineColor,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = 2f,
                )
                drawLine(
                    color = centerLineColor,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 2f,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ControlPanel(
    uiState: SpriteDebugState,
    onSelectBox: (Int) -> Unit,
    onNudge: (Int, Int) -> Unit,
    onUpdateX: (Float?) -> Unit,
    onUpdateY: (Float?) -> Unit,
    onStepChange: (Int) -> Unit,
    onSnapToggle: () -> Unit,
    onSearchRadiusChange: (Float) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onAutoSearchOne: () -> Unit,
    onAutoSearchAll: () -> Unit,
    onReset: () -> Unit,
    isAnalyzing: Boolean,
    onCancelAnalysis: () -> Unit,
) {
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                itemsIndexed(uiState.boxes) { index, _ ->
                    FilterChip(
                        selected = uiState.selectedBoxIndex == index,
                        onClick = { onSelectBox(index) },
                        label = { Text(text = "Box ${index + 1}") },
                        modifier = Modifier.semantics { contentDescription = "Box ${index + 1} を選択" },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoordinateField(
                        label = "X",
                        value = uiState.boxes.getOrNull(uiState.selectedBoxIndex)?.x,
                        modifier = Modifier.weight(1f),
                    ) { value ->
                        onUpdateX(value)
                    }
                    CoordinateField(
                        label = "Y",
                        value = uiState.boxes.getOrNull(uiState.selectedBoxIndex)?.y,
                        modifier = Modifier.weight(1f),
                    ) { value ->
                        onUpdateY(value)
                    }
                }
                StepSelector(step = uiState.step, onStepChange = onStepChange)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "詳細設定", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                        Icon(
                            imageVector = if (detailsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (detailsExpanded) "詳細設定を閉じる" else "詳細設定を開く",
                        )
                        Text(text = if (detailsExpanded) "閉じる" else "開く")
                    }
                }
                AnimatedVisibility(visible = detailsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = "スナップ")
                                Switch(checked = uiState.snapToGrid, onCheckedChange = { onSnapToggle() })
                            }
                            Text(
                                text = "検出点に吸着",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "探索半径 ${uiState.searchRadius.toInt()}px")
                            Slider(
                                value = uiState.searchRadius,
                                onValueChange = onSearchRadiusChange,
                                valueRange = 0f..32f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Sobel閾値 ${"%.2f".format(uiState.sobelThreshold)}")
                            Slider(
                                value = uiState.sobelThreshold,
                                onValueChange = onThresholdChange,
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            FineTunePadXY(
                step = uiState.step,
                onAdjustX = { delta -> onNudge(delta, 0) },
                onAdjustY = { delta -> onNudge(0, delta) },
            )
            if (isAnalyzing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(text = "自動探索を実行中...", modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancelAnalysis) { Text(text = "キャンセル") }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onAutoSearchOne, modifier = Modifier.weight(1f), enabled = !isAnalyzing) { Text(text = "選択のみ自動探索") }
                Button(onClick = onAutoSearchAll, modifier = Modifier.weight(1f), enabled = !isAnalyzing) { Text(text = "全体自動探索") }
                TextButton(onClick = onReset) { Text(text = "リセット") }
            }
        }
    }
}

@Composable
private fun FineTunePadXY(step: Int, onAdjustX: (Int) -> Unit, onAdjustY: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "微調整（ステップ：${step}px）")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdjustmentButton(
                label = "← ${step}px",
                contentDescription = "${step}ピクセル左に移動",
                modifier = Modifier.weight(1f),
            ) { onAdjustX(-step) }
            AdjustmentButton(
                label = "↑ ${step}px",
                contentDescription = "${step}ピクセル上に移動",
                modifier = Modifier.weight(1f),
            ) { onAdjustY(-step) }
            AdjustmentButton(
                label = "↓ ${step}px",
                contentDescription = "${step}ピクセル下に移動",
                modifier = Modifier.weight(1f),
            ) { onAdjustY(step) }
            AdjustmentButton(
                label = "→ ${step}px",
                contentDescription = "${step}ピクセル右に移動",
                modifier = Modifier.weight(1f),
            ) { onAdjustX(step) }
        }
    }
}

@Composable
private fun AdjustmentButton(
    label: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    val semanticsModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }
    FilledTonalButton(
        onClick = onClick,
        modifier = semanticsModifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoordinateField(label: String, value: Float?, modifier: Modifier = Modifier, onValueChange: (Float?) -> Unit) {
    var text by rememberSaveable(value) { mutableStateOf(value?.toInt()?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it.toFloatOrNull())
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = TextFieldDefaults.outlinedTextFieldColors(),
        singleLine = true,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepSelector(step: Int, onStepChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text = "ステップ（px）")
        Text(text = "${step}px を選択中", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(1, 2, 4, 8).forEach { candidate ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.semantics { contentDescription = "${candidate}ピクセルに変更" },
                ) {
                    RadioButton(selected = candidate == step, onClick = { onStepChange(candidate) })
                    Text(text = "$candidate px")
                }
            }
        }
    }
}

@Composable
private fun EditToolbar(
    editingMode: SpriteEditMode,
    brushSize: Float,
    onEditingModeChange: (SpriteEditMode) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "編集モード")
            Button(onClick = { onEditingModeChange(SpriteEditMode.Pen) }, enabled = editingMode != SpriteEditMode.Pen) {
                Text(text = "ペン")
            }
            Button(onClick = { onEditingModeChange(SpriteEditMode.Eraser) }, enabled = editingMode != SpriteEditMode.Eraser) {
                Text(text = "消しゴム")
            }
            Button(onClick = { onEditingModeChange(SpriteEditMode.Selection) }, enabled = editingMode != SpriteEditMode.Selection) {
                Text(text = "選択")
            }
            TextButton(onClick = { onEditingModeChange(SpriteEditMode.None) }) { Text(text = "終了") }
            IconButton(onClick = onUndo) { Text(text = "Undo") }
            IconButton(onClick = onRedo) { Text(text = "Redo") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "ブラシ太さ ${brushSize.toInt()}px")
            Slider(value = brushSize, onValueChange = onBrushSizeChange, valueRange = 1f..32f)
        }
    }
}

@Composable
private fun MatchList(uiState: SpriteDebugState) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "一致率一覧", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.matchScores) { score ->
                    val isBest = uiState.bestMatchIndices.contains(score.to - 1)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBest) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = "${score.from}→${score.to}")
                            Text(text = "Score: ${"%.2f".format(score.score)}")
                            Text(text = "dx=${score.offset.dx}, dy=${score.offset.dy}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugPersistencePanel(
    stateJson: String,
    onStateJsonChange: (String) -> Unit,
    analysisJson: String,
    onAnalysisJsonChange: (String) -> Unit,
    onApplyState: (String) -> Unit,
    onApplyAnalysis: (String) -> Unit,
    clipboardManager: ClipboardManager,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        JsonTransferCard(
            title = "SpriteDebugState JSON",
            jsonText = stateJson,
            onJsonChange = onStateJsonChange,
            onApply = onApplyState,
            clipboardManager = clipboardManager,
            snackbarHostState = snackbarHostState,
            scope = scope,
            emptyClipboardMessage = "クリップボードが空です",
        )
        JsonTransferCard(
            title = "解析結果 JSON",
            jsonText = analysisJson,
            onJsonChange = onAnalysisJsonChange,
            onApply = onApplyAnalysis,
            clipboardManager = clipboardManager,
            snackbarHostState = snackbarHostState,
            scope = scope,
            emptyClipboardMessage = "クリップボードが空です",
        )
    }
}

@Composable
private fun JsonTransferCard(
    title: String,
    jsonText: String,
    onJsonChange: (String) -> Unit,
    onApply: (String) -> Unit,
    clipboardManager: ClipboardManager,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    emptyClipboardMessage: String,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = jsonText,
                onValueChange = onJsonChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text(text = "JSON") },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        copyJsonToClipboard(
                            clipboardManager = clipboardManager,
                            scope = scope,
                            snackbarHostState = snackbarHostState,
                            json = jsonText,
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "コピー")
                }
                Button(
                    onClick = {
                        pasteJsonFromClipboard(
                            clipboardManager = clipboardManager,
                            onPaste = { pasted -> onJsonChange(pasted) },
                            onEmpty = { scope.launch { snackbarHostState.showSnackbar(emptyClipboardMessage) } },
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "貼り付け")
                }
                Button(
                    onClick = { onApply(jsonText) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "適用")
                }
            }
        }
    }
}
