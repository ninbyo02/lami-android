package com.sonusid.ollama.ui.screens.debug
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Parcelable
import androidx.appcompat.content.res.AppCompatResources
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonusid.ollama.R
import java.util.ArrayDeque
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

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
data class SpriteMatchScore(
    val from: Int,
    val to: Int,
    val score: Float,
) : Parcelable

@Parcelize
data class SpriteDebugState(
    val selectedBoxIndex: Int = 0,
    val boxes: List<SpriteBox> = defaultBoxes(IntSize(256, 256)),
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

private data class SpriteSheetScale(
    val scale: Float,
    val offset: Offset,
) {
    fun imageToCanvas(offsetImage: Offset): Offset = Offset(
        x = offset.x + offsetImage.x * scale,
        y = offset.y + offsetImage.y * scale,
    )

    fun canvasToImage(offsetCanvas: Offset): Offset = Offset(
        x = (offsetCanvas.x - offset.x) / scale,
        y = (offsetCanvas.y - offset.y) / scale,
    )

    fun imageSizeToCanvas(size: Size): Size = Size(width = size.width * scale, height = size.height * scale)
}

class SpriteDebugViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(savedStateHandle.get<SpriteDebugState>(KEY_STATE) ?: SpriteDebugState())
    val uiState: StateFlow<SpriteDebugState> = _uiState.asStateFlow()
    private val _sheetBitmap = MutableStateFlow<ImageBitmap?>(null)
    val sheetBitmap: StateFlow<ImageBitmap?> = _sheetBitmap.asStateFlow()
    private val undoStack = ArrayDeque<Bitmap>()
    private val redoStack = ArrayDeque<Bitmap>()
    private var spriteSheetBitmap: Bitmap? = null

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                savedStateHandle[KEY_STATE] = state
            }
        }
        recomputeMatches()
    }

    fun setSpriteSheet(bitmap: Bitmap?) {
        spriteSheetBitmap = bitmap
        _sheetBitmap.value = bitmap?.asImageBitmap()
        val current = _uiState.value
        val targetBitmap = bitmap ?: return
        if (current.boxes.isEmpty() || current.boxes.all { it.width == 0f }) {
            _uiState.update {
                it.copy(boxes = SpriteDebugState.defaultBoxes(IntSize(targetBitmap.width, targetBitmap.height))).withMatches()
            }
        } else {
            recomputeMatches()
        }
    }

    fun selectBox(index: Int) {
        val bounded = index.coerceIn(0, _uiState.value.boxes.lastIndex)
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
        val snappedDelta = if (state.snapToGrid) snapDelta(imageDelta, state.step) else imageDelta
        val newBox = selected.copy(
            x = (selected.x + snappedDelta.x).coerceIn(0f, bitmap.width - selected.width),
            y = (selected.y + snappedDelta.y).coerceIn(0f, bitmap.height - selected.height),
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
        val bitmap = spriteSheetBitmap ?: return
        val state = _uiState.value
        val selected = state.boxes.getOrNull(state.selectedBoxIndex) ?: return
        val bestOffset = Offset(state.searchRadius / 2f, -state.searchRadius / 2f)
        val candidate = selected.copy(
            x = (selected.x + bestOffset.x).coerceIn(0f, bitmap.width - selected.width),
            y = (selected.y + bestOffset.y).coerceIn(0f, bitmap.height - selected.height),
        )
        replaceBox(candidate)
    }

    fun autoSearchAll() {
        val bitmap = spriteSheetBitmap ?: return
        val state = _uiState.value
        val updated = state.boxes.map { box ->
            val deltaX = ((box.index % state.spriteSheetConfig.cols) - 1) * state.step.toFloat()
            val deltaY = ((box.index / state.spriteSheetConfig.cols) - 1) * state.step.toFloat()
            box.copy(
                x = (box.x + deltaX).coerceIn(0f, bitmap.width - box.width),
                y = (box.y + deltaY).coerceIn(0f, bitmap.height - box.height),
            )
        }
        updateState { copy(boxes = updated) }
    }

    fun resetBoxes() {
        val bitmap = spriteSheetBitmap ?: return
        updateState { copy(boxes = SpriteDebugState.defaultBoxes(IntSize(bitmap.width, bitmap.height))) }
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
            val width = box.width.toInt().coerceAtLeast(1).coerceAtMost(bitmap.width - x)
            val height = box.height.toInt().coerceAtLeast(1).coerceAtMost(bitmap.height - y)
            runCatching {
                Bitmap.createBitmap(bitmap, x, y, width, height).asImageBitmap()
            }.getOrNull()
        }
    }

    private fun snapDelta(delta: Offset, step: Int): Offset {
        val snappedX = (delta.x / step).toInt() * step
        val snappedY = (delta.y / step).toInt() * step
        return Offset(snappedX.toFloat(), snappedY.toFloat())
    }

    private fun replaceBox(box: SpriteBox) {
        val state = _uiState.value
        val updated = state.boxes.toMutableList().apply { this[state.selectedBoxIndex] = box }
        updateState { copy(boxes = updated) }
    }

    private fun updateState(block: SpriteDebugState.() -> SpriteDebugState) {
        _uiState.update { block(it).withMatches() }
    }

    private fun SpriteDebugState.withMatches(): SpriteDebugState {
        val (scores, best) = calculateMatchScores(boxes, sobelThreshold)
        return copy(matchScores = scores, bestMatchIndices = best)
    }

    private fun calculateMatchScores(boxes: List<SpriteBox>, sobelThreshold: Float): Pair<List<SpriteMatchScore>, List<Int>> {
        val scores = boxes.zipWithNext { current, next ->
            val distance = (kotlin.math.abs(current.x - next.x) + kotlin.math.abs(current.y - next.y)).coerceAtLeast(1f)
            val thresholdWeight = (1f - sobelThreshold.coerceIn(0f, 1f))
            val similarity = (1f / distance * thresholdWeight).coerceIn(0f, 1f)
            SpriteMatchScore(current.index + 1, next.index + 1, similarity)
        }
        val bestScore = scores.maxOfOrNull { it.score }
        val bestIndices = if (bestScore != null) {
            scores.filter { it.score == bestScore }.flatMap { listOf(it.from - 1, it.to - 1) }
        } else emptyList()
        return scores to bestIndices
    }

    private fun recomputeMatches() {
        _uiState.update { it.withMatches() }
    }

    private fun pushUndo(current: Bitmap) {
        undoStack.addLast(current.copy(Bitmap.Config.ARGB_8888, true))
        if (undoStack.size > 5) {
            undoStack.removeFirst()
        }
    }

    companion object {
        private const val KEY_STATE = "sprite_debug_state"
    }
}

private fun createPlaceholderBitmap(): Bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)

private fun loadSpriteBitmap(context: Context): Bitmap? {
    val drawable = AppCompatResources.getDrawable(context, R.drawable.logo) ?: return createPlaceholderBitmap()
    val width: Int = drawable.intrinsicWidth.takeIf { it > 0 } ?: return createPlaceholderBitmap()
    val height: Int = drawable.intrinsicHeight.takeIf { it > 0 } ?: return createPlaceholderBitmap()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    return bitmap
}

@Composable
fun SpriteDebugScreen(viewModel: SpriteDebugViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetBitmap by viewModel.sheetBitmap.collectAsState()
    val context = LocalContext.current
    val spriteBitmap = remember { loadSpriteBitmap(context) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpriteSheetCanvas(
            uiState = uiState,
            spriteBitmap = sheetBitmap ?: spriteBitmap?.asImageBitmap() ?: createPlaceholderBitmap().asImageBitmap(),
            onDragBox = { deltaImage -> viewModel.updateBoxPosition(deltaImage) },
            onStroke = { offset -> viewModel.applyStroke(offset) },
        )
        ControlPanel(
            uiState = uiState,
            onSelectBox = { index -> rememberedState = rememberedState.copy(selectedBoxIndex = index) },
            onNudge = { dx, dy -> viewModel.nudgeSelected(dx, dy) },
            onUpdateX = { value -> viewModel.updateBoxCoordinate(x = value, y = null) },
            onUpdateY = { value -> viewModel.updateBoxCoordinate(x = null, y = value) },
            onStepChange = { step -> rememberedState = rememberedState.copy(step = step) },
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
        )
        MatchList(uiState = uiState)
        PreviewPanel(
            uiState = uiState,
            viewModel = viewModel,
            rememberedState = rememberedState,
            sheetBitmap = sheetBitmap,
            onUpdatePreviewSpeed = { speed -> viewModel.updatePreviewSpeed(speed) },
            onToggleOnion = { viewModel.toggleOnionSkin(it) },
            onToggleCenter = { viewModel.toggleCenterLine(it) },
        )
    }
}

@Composable
private fun SpriteSheetCanvas(
    uiState: SpriteDebugState,
    spriteBitmap: ImageBitmap,
    onDragBox: (Offset) -> Unit,
    onStroke: (Offset) -> Unit,
) {
    val intrinsicSize = Size(spriteBitmap.width.toFloat(), spriteBitmap.height.toFloat())
    var layoutSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = Modifier
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

        val scale = remember(layoutSize, intrinsicSize) {
            val scaleRatio = if (layoutSize == Size.Zero) 1f else minOf(
                layoutSize.width / intrinsicSize.width,
                layoutSize.height / intrinsicSize.height,
            )
            val offset = Offset(
                (layoutSize.width - intrinsicSize.width * scaleRatio) / 2f,
                (layoutSize.height - intrinsicSize.height * scaleRatio) / 2f,
            )
            SpriteSheetScale(scaleRatio, offset)
        }

        val selectedColor = MaterialTheme.colorScheme.primary
        val normalColor = MaterialTheme.colorScheme.outlineVariant
        val bestColor = MaterialTheme.colorScheme.tertiary
        val centerLineColor = MaterialTheme.colorScheme.error

        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(uiState.selectedBoxIndex, uiState.snapToGrid, uiState.editingMode) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (uiState.editingMode == SpriteEditMode.None) {
                            onDragBox(Offset(dragAmount.x / scale.scale, dragAmount.y / scale.scale))
                        } else {
                            onStroke(scale.canvasToImage(change.position))
                        }
                    }
                },
        ) {
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
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(9) { index ->
                    Button(onClick = { onSelectBox(index) }, enabled = index != uiState.selectedBoxIndex) {
                        Text(text = "Box ${index + 1}")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CoordinateField("X", uiState.boxes.getOrNull(uiState.selectedBoxIndex)?.x) { value ->
                    onUpdateX(value)
                }
                CoordinateField("Y", uiState.boxes.getOrNull(uiState.selectedBoxIndex)?.y) { value ->
                    onUpdateY(value)
                }
                StepSelector(step = uiState.step, onStepChange = onStepChange)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Snap")
                    Switch(checked = uiState.snapToGrid, onCheckedChange = { onSnapToggle() })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "微調整")
                listOf(1, 2, 4).forEach { step ->
                    IconButton(onClick = { onNudge(-step, 0) }) { Text(text = "-${step}") }
                    IconButton(onClick = { onNudge(step, 0) }) { Text(text = "+${step}") }
                    IconButton(onClick = { onNudge(0, -step) }) { Text(text = "^${step}") }
                    IconButton(onClick = { onNudge(0, step) }) { Text(text = "v${step}") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "探索半径 ${uiState.searchRadius.toInt()}px")
                Slider(value = uiState.searchRadius, onValueChange = onSearchRadiusChange, valueRange = 0f..32f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Sobel閾値 ${"%.2f".format(uiState.sobelThreshold)}")
                Slider(value = uiState.sobelThreshold, onValueChange = onThresholdChange, valueRange = 0f..1f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAutoSearchOne) { Text(text = "選択のみ自動探索") }
                Button(onClick = onAutoSearchAll) { Text(text = "全体自動探索") }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoordinateField(label: String, value: Float?, onValueChange: (Float?) -> Unit) {
    var text by rememberSaveable(value) { mutableStateOf(value?.toInt()?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it.toFloatOrNull())
        },
        modifier = Modifier.size(width = 120.dp, height = 64.dp),
        label = { Text(text = label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = TextFieldDefaults.outlinedTextFieldColors(),
        singleLine = true,
    )
}

@Composable
private fun StepSelector(step: Int, onStepChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "ステップ ${step}px")
        listOf(1, 2, 4, 8).forEach { candidate ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Checkbox(checked = candidate == step, onCheckedChange = { onStepChange(candidate) })
                Text(text = candidate.toString())
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
                    val isBest = uiState.bestMatchIndices.contains(score.from - 1) || uiState.bestMatchIndices.contains(score.to - 1)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isBest) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(text = "${score.from}→${score.to}")
                            Text(text = "Score: ${"%.2f".format(score.score)}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewPanel(
    uiState: SpriteDebugState,
    viewModel: SpriteDebugViewModel,
    rememberedState: SpriteDebugState,
    sheetBitmap: ImageBitmap?,
    onUpdatePreviewSpeed: (Long) -> Unit,
    onToggleOnion: (Boolean) -> Unit,
    onToggleCenter: (Boolean) -> Unit,
) {
    var playing by rememberSaveable { mutableStateOf(false) }
    var frameIndex by rememberSaveable { mutableIntStateOf(0) }
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

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = { playing = !playing }) {
                    Icon(imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "play")
                }
                Text(text = "速度 ${rememberedState.previewSpeedMs}ms")
                Slider(
                    value = rememberedState.previewSpeedMs.toFloat(),
                    onValueChange = { onUpdatePreviewSpeed(it.toLong()) },
                    valueRange = 120f..2000f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "オニオンスキン")
                    Switch(checked = uiState.onionSkin, onCheckedChange = onToggleOnion)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "中心線")
                    Switch(checked = uiState.showCenterLine, onCheckedChange = onToggleCenter)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (frames.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .border(2.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = glow), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(bitmap = frames[frameIndex % frames.size], contentDescription = "frame", modifier = Modifier.fillMaxSize())
                        if (uiState.onionSkin && frames.size > 1) {
                            val previous = frames[(frameIndex - 1 + frames.size) % frames.size]
                            Image(bitmap = previous, contentDescription = "onion", modifier = Modifier.fillMaxSize(), alpha = 0.3f)
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "プレビュー順")
                    uiState.boxes.forEach { box ->
                        Text(text = "Frame ${box.index + 1} (${box.x.toInt()}, ${box.y.toInt()})")
                    }
                }
            }
        }
    }
}
