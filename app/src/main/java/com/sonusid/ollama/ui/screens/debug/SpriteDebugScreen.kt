package com.sonusid.ollama.ui.screens.debug
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Parcelable
import android.util.Log
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import androidx.savedstate.SavedStateRegistryOwner
import com.sonusid.ollama.R
import com.sonusid.ollama.ui.components.LamiSpriteStatus
import com.sonusid.ollama.ui.components.LamiStatusSprite
import com.sonusid.ollama.util.SpriteAnalysis
import com.sonusid.ollama.ui.screens.settings.copyJsonToClipboard
import com.sonusid.ollama.ui.screens.settings.pasteJsonFromClipboard
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlin.math.hypot
import kotlin.math.max

private const val SPRITE_DEBUG_TAG = "SpriteDebug"
private const val DEFAULT_SPRITE_SIZE = 288

@Parcelize
data class SpriteSheetConfig(
    val rows: Int = 3,
    val cols: Int = 3,
    val order: List<Int> = List(9) { it },
) : Parcelable

@Parcelize
data class SpriteBox(
    val index: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) : Parcelable

enum class SpriteEditMode { None, Pen, Eraser }

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
    val matchScores: List<SpriteMatchScore> = emptyList(),
    val bestMatchIndices: List<Int> = emptyList(),
    val spriteSheetConfig: SpriteSheetConfig = SpriteSheetConfig(),
) : Parcelable {
    companion object {
        fun defaultBoxes(size: IntSize, config: SpriteSheetConfig = SpriteSheetConfig()): List<SpriteBox> {
            val cellWidth = size.width / config.cols.toFloat()
            val cellHeight = size.height / config.rows.toFloat()
            return config.order.mapIndexed { index, order ->
                val col = order % config.cols
                val row = order / config.cols
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
    private val undoStack = ArrayDeque<Bitmap>()
    private val redoStack = ArrayDeque<Bitmap>()
    private var spriteSheetBitmap: Bitmap? = null
    private var analysisJob: Job? = null

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                savedStateHandle[KEY_STATE] = state
            }
        }
        viewModelScope.launch(ioDispatcher) {
            restorePersistedState()
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
        updateState { copy(boxes = SpriteDebugState.defaultBoxes(IntSize(bitmap.width, bitmap.height))) }
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
                applyOffsetsFromResult(result, focusIndex, applyOffsetsForAll)
                applyAnalysisResult(result, highlightIndex = focusIndex)
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

    private fun initializeState(): SpriteDebugState =
        runCatching { savedStateHandle.get<SpriteDebugState>(KEY_STATE) }
            .onFailure { throwable ->
                Log.w(SPRITE_DEBUG_TAG, "Failed to restore sprite debug state. Resetting to default.", throwable)
                savedStateHandle.remove<SpriteDebugState>(KEY_STATE)
            }
            .getOrNull()
            .orEmptyState()
            .ensureBoxes(IntSize(DEFAULT_SPRITE_SIZE, DEFAULT_SPRITE_SIZE))

    private fun updateState(block: SpriteDebugState.() -> SpriteDebugState) {
        val targetSize = spriteSheetBitmap?.let { IntSize(it.width, it.height) } ?: IntSize(DEFAULT_SPRITE_SIZE, DEFAULT_SPRITE_SIZE)
        _uiState.update { current ->
            block(current).ensureBoxes(targetSize)
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
    ) {
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
        val targetSize = spriteSheetBitmap?.let { IntSize(it.width, it.height) } ?: IntSize(DEFAULT_SPRITE_SIZE, DEFAULT_SPRITE_SIZE)
        if (persistedState != null) {
            _uiState.update { persistedState.ensureBoxes(targetSize) }
        }
        if (persistedResult != null) {
            applyAnalysisResult(persistedResult, persist = false)
        }
    }

    private fun persistStateAsync(state: SpriteDebugState = _uiState.value) {
        viewModelScope.launch(ioDispatcher) { dataStore.saveState(state) }
    }

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
        applyAnalysisResult(parsed)
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
                    ) as T
                }
            }
    }

    private fun SpriteDebugState.ensureBoxes(targetSize: IntSize): SpriteDebugState {
        val expectedSize = max(9, spriteSheetConfig.order.size.takeIf { it > 0 } ?: spriteSheetConfig.rows * spriteSheetConfig.cols)
        val needsDefaultBoxes = boxes.size < expectedSize || boxes.any { it.width <= 0f || it.height <= 0f }
        val refreshedBoxes = if (needsDefaultBoxes) {
            val defaults = SpriteDebugState.defaultBoxes(targetSize)
            Log.d(SPRITE_DEBUG_TAG, "Normalized ROI to ${defaults.size} items (fallback to default grid)")
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
        val boundedIndex = selectedBoxIndex.coerceIn(0, 8)
        return copy(boxes = constrainedBoxes, selectedBoxIndex = boundedIndex)
    }

    private fun SpriteDebugState?.orEmptyState(): SpriteDebugState = this ?: SpriteDebugState()
}

private fun loadSpriteBitmap(context: Context): Bitmap? {
    val resources = context.resources
    val spriteBitmap = BitmapFactory.decodeResource(resources, R.drawable.lami_sprite_3x3_288)
    if (spriteBitmap != null && spriteBitmap.width > 0 && spriteBitmap.height > 0) {
        Log.d(SPRITE_DEBUG_TAG, "Loaded sprite sheet lami_sprite_3x3_288 (${spriteBitmap.width}x${spriteBitmap.height})")
        return spriteBitmap
    }
    Log.w(SPRITE_DEBUG_TAG, "Failed to decode sprite sheet. Sprite bitmap is null or empty.")
    return null
}

@Composable
fun SpriteDebugScreen(viewModel: SpriteDebugViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetBitmap by viewModel.sheetBitmap.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }
    var stateJson by remember(uiState) { mutableStateOf(gson.toJson(uiState)) }
    var analysisJson by remember(analysisResult) { mutableStateOf(analysisResult?.let { gson.toJson(it) }.orEmpty()) }
    val spriteBitmap = remember { loadSpriteBitmap(context) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(spriteBitmap) {
        val bitmap = spriteBitmap ?: return@LaunchedEffect
        viewModel.setSpriteSheet(bitmap)
    }

    var rememberedState by rememberSaveable { mutableStateOf(uiState) }

    LaunchedEffect(uiState) {
        rememberedState = uiState
    }

    LaunchedEffect(rememberedState.selectedBoxIndex) { viewModel.selectBox(rememberedState.selectedBoxIndex) }
    LaunchedEffect(rememberedState.step) { viewModel.updateStep(rememberedState.step) }

    fun showError(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val resolvedBitmap = remember(sheetBitmap, spriteBitmap) {
        (sheetBitmap ?: spriteBitmap?.asImageBitmap())?.takeIf { it.width > 0 && it.height > 0 }
    }
    val shouldShowLoading = resolvedBitmap == null
    LaunchedEffect(shouldShowLoading, sheetBitmap, spriteBitmap) {
        if (shouldShowLoading) {
            Log.d(
                SPRITE_DEBUG_TAG,
                "Canvas initialization skipped. sheetBitmap=${sheetBitmap?.width}x${sheetBitmap?.height}, loaded=${spriteBitmap?.width}x${spriteBitmap?.height}",
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
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("プレビュー") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("ギャラリー") })
            }
            when (selectedTab) {
                0 -> if (shouldShowLoading) {
                    LoadingCanvasPlaceholder()
                } else {
                    AdjustTabContent(
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
                1 -> PreviewTabContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    rememberedState = rememberedState,
                    sheetBitmap = resolvedBitmap,
                    onRememberedStateChange = { rememberedState = it },
                )
                else -> GalleryTabContent()
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
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpriteSheetCanvas(
            uiState = uiState,
            spriteBitmap = spriteBitmap,
            onDragBox = { deltaImage -> viewModel.updateBoxPosition(deltaImage) },
            onStroke = { offset -> viewModel.applyStroke(offset) },
            onBoxSelected = { index -> onRememberedStateChange(rememberedState.copy(selectedBoxIndex = index)) },
            modifier = Modifier.fillMaxWidth(),
        )
        SheetPreview(
            uiState = uiState,
            spriteBitmap = spriteBitmap,
            onSelectBox = { index -> onRememberedStateChange(rememberedState.copy(selectedBoxIndex = index)) },
        )
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
            onEditingModeChange = { viewModel.setEditingMode(it) },
            onBrushSizeChange = { viewModel.setBrushSize(it) },
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
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
private fun PreviewTabContent(
    uiState: SpriteDebugState,
    viewModel: SpriteDebugViewModel,
    rememberedState: SpriteDebugState,
    sheetBitmap: ImageBitmap?,
    onRememberedStateChange: (SpriteDebugState) -> Unit,
) {
    val hasSheetBitmap = sheetBitmap?.let { it.width > 0 && it.height > 0 } == true
    if (!hasSheetBitmap) {
        LoadingCanvasPlaceholder(modifier = Modifier.fillMaxSize())
        return
    }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            itemsIndexed(uiState.boxes) { index, _ ->
                FilterChip(
                    selected = uiState.selectedBoxIndex == index,
                    onClick = { onRememberedStateChange(rememberedState.copy(selectedBoxIndex = index)) },
                    label = { Text(text = "Box ${index + 1}") },
                    modifier = Modifier.semantics { contentDescription = "Box ${index + 1} を選択" },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp),
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
}

@Composable
private fun GalleryTabContent() {
    val statuses = remember { LamiSpriteStatus.values() }
    var selectedStatusName by rememberSaveable { mutableStateOf(LamiSpriteStatus.Idle.name) }
    val selectedStatus = remember(selectedStatusName) {
        runCatching { LamiSpriteStatus.valueOf(selectedStatusName) }.getOrDefault(LamiSpriteStatus.Idle)
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
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "選択中: ${selectedStatus.name}", style = MaterialTheme.typography.titleMedium)
                        Text(text = "ステータスセットを切り替えてプレビューを確認できます。")
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
            .aspectRatio(1.6f)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = spriteBitmap,
            contentDescription = "Sprite sheet",
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .onSizeChanged { layoutSize = Size(it.width.toFloat(), it.height.toFloat()) },
        )

        val scale = remember(layoutSize, intrinsicSize) { calculateScale(intrinsicSize, layoutSize) }

        val selectedColor = MaterialTheme.colorScheme.primary
        val normalColor = MaterialTheme.colorScheme.outlineVariant
        val bestColor = MaterialTheme.colorScheme.tertiary
        val centerLineColor = MaterialTheme.colorScheme.error
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
                .pointerInput(uiState.selectedBoxIndex, uiState.snapToGrid, uiState.editingMode) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val safeScale = scale.scale.coerceAtLeast(0.01f)
                        if (uiState.editingMode == SpriteEditMode.None) {
                            val deltaImage = Offset(dragAmount.x / safeScale, dragAmount.y / safeScale)
                            if (!deltaImage.isFiniteOffset()) {
                                Log.w(SPRITE_DEBUG_TAG, "Ignoring non-finite drag delta: dragAmount=$dragAmount safeScale=$safeScale")
                                return@detectDragGestures
                            }
                            onDragBox(deltaImage)
                        } else {
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
                    }
                }
                .pointerInput(uiState.boxes, scale) {
                    detectTapGestures { tapOffset ->
                        val imageOffset = scale.canvasToImage(tapOffset)
                        if (!imageOffset.isFiniteOffset()) {
                            Log.w(SPRITE_DEBUG_TAG, "Ignoring non-finite tap: tapOffset=$tapOffset scale=${scale.scale} offset=${scale.offset}")
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
private fun SheetPreview(
    uiState: SpriteDebugState,
    spriteBitmap: ImageBitmap,
    onSelectBox: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val intrinsicSize = Size(spriteBitmap.width.toFloat(), spriteBitmap.height.toFloat())
    var layoutSize by remember { mutableStateOf(Size.Zero) }
    val selectedColor = MaterialTheme.colorScheme.primary
    val normalColor = MaterialTheme.colorScheme.outlineVariant
    val bestColor = MaterialTheme.colorScheme.tertiary
    val centerLineColor = MaterialTheme.colorScheme.error
    val onionColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "シート全体プレビュー", style = MaterialTheme.typography.titleMedium)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                ) {
                    Image(
                        bitmap = spriteBitmap,
                        contentDescription = "Sprite sheet overview",
                        modifier = Modifier
                            .matchParentSize()
                            .onSizeChanged { layoutSize = Size(it.width.toFloat(), it.height.toFloat()) },
                        contentScale = ContentScale.Fit,
                    )
                    val scale = remember(layoutSize, intrinsicSize) { calculateScale(intrinsicSize, layoutSize) }
                    val isScaleValid = remember(scale) { scale.isValid() && scale.scale > 0f }
                    if (!isScaleValid) {
                        LaunchedEffect(scale) {
                            Log.d(SPRITE_DEBUG_TAG, "Sheet preview skipped due to invalid scale: $scale")
                        }
                        LoadingCanvasPlaceholder(modifier = Modifier.matchParentSize())
                        return@BoxWithConstraints
                    }
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(uiState.selectedBoxIndex, uiState.boxes) {
                                detectTapGestures { tapOffset ->
                                    val imageTap = scale.canvasToImage(tapOffset)
                                    val containing = uiState.boxes.firstOrNull { box ->
                                        imageTap.x in box.x..(box.x + box.width) && imageTap.y in box.y..(box.y + box.height)
                                    }
                                    val nearest = containing ?: uiState.boxes.minByOrNull { box ->
                                        val cx = box.x + box.width / 2f
                                        val cy = box.y + box.height / 2f
                                        val dx = cx - imageTap.x
                                        val dy = cy - imageTap.y
                                        dx * dx + dy * dy
                                    }
                                    nearest?.let { onSelectBox(it.index) }
                                }
                            },
                    ) {
                        if (!scale.isValid()) return@Canvas
                        val step = 96f
                        var x = 0f
                        while (x <= spriteBitmap.width) {
                            val start = scale.imageToCanvas(Offset(x, 0f))
                            val end = scale.imageToCanvas(Offset(x, spriteBitmap.height.toFloat()))
                            drawLine(color = gridColor, start = start, end = end, strokeWidth = 1f)
                            x += step
                        }
                        var y = 0f
                        while (y <= spriteBitmap.height) {
                            val start = scale.imageToCanvas(Offset(0f, y))
                            val end = scale.imageToCanvas(Offset(spriteBitmap.width.toFloat(), y))
                            drawLine(color = gridColor, start = start, end = end, strokeWidth = 1f)
                            y += step
                        }

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

                        if (uiState.onionSkin && uiState.boxes.isNotEmpty()) {
                            val prev = uiState.boxes.getOrNull(uiState.selectedBoxIndex - 1)
                            val next = uiState.boxes.getOrNull(uiState.selectedBoxIndex + 1)
                            listOfNotNull(prev, next).forEach { box ->
                                val topLeft = scale.imageToCanvas(Offset(box.x, box.y))
                                val size = scale.imageSizeToCanvas(Size(box.width, box.height))
                                drawRect(
                                    color = onionColor.copy(alpha = 0.2f),
                                    topLeft = topLeft,
                                    size = size,
                                )
                            }
                        }

                        if (uiState.showCenterLine) {
                            val verticalStart = scale.imageToCanvas(Offset(spriteBitmap.width / 2f, 0f))
                            val verticalEnd = scale.imageToCanvas(Offset(spriteBitmap.width / 2f, spriteBitmap.height.toFloat()))
                            val horizontalStart = scale.imageToCanvas(Offset(0f, spriteBitmap.height / 2f))
                            val horizontalEnd = scale.imageToCanvas(
                                Offset(spriteBitmap.width.toFloat(), spriteBitmap.height / 2f),
                            )
                            drawLine(
                                color = centerLineColor,
                                start = verticalStart,
                                end = verticalEnd,
                                strokeWidth = 2f,
                            )
                            drawLine(
                                color = centerLineColor,
                                start = horizontalStart,
                                end = horizontalEnd,
                                strokeWidth = 2f,
                            )
                        }
                    }
                }
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
    onEditingModeChange: (SpriteEditMode) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    isAnalyzing: Boolean,
    onCancelAnalysis: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "スナップ")
                    Switch(checked = uiState.snapToGrid, onCheckedChange = { onSnapToggle() })
                }
            }
            FineTunePadXY(
                onAdjustX = { delta -> onNudge(delta, 0) },
                onAdjustY = { delta -> onNudge(0, delta) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            EditToolbar(
                editingMode = uiState.editingMode,
                brushSize = uiState.brushSize,
                onEditingModeChange = onEditingModeChange,
                onBrushSizeChange = onBrushSizeChange,
                onUndo = onUndo,
                onRedo = onRedo,
            )
        }
    }
}

@Composable
private fun FineTunePadXY(onAdjustX: (Int) -> Unit, onAdjustY: (Int) -> Unit) {
    val steps = listOf(1, 2, 4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "微調整")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                steps.forEach { step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdjustmentButton(label = "←$step", modifier = Modifier.weight(1f)) { onAdjustX(-step) }
                        AdjustmentButton(label = "→$step", modifier = Modifier.weight(1f)) { onAdjustX(step) }
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                steps.forEach { step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdjustmentButton(label = "↑$step", modifier = Modifier.weight(1f)) { onAdjustY(-step) }
                        AdjustmentButton(label = "↓$step", modifier = Modifier.weight(1f)) { onAdjustY(step) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustmentButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
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
        Text(text = "ステップ ${step}px")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(1, 2, 4, 8).forEach { candidate ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = candidate == step, onCheckedChange = { onStepChange(candidate) })
                    Text(text = candidate.toString())
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
