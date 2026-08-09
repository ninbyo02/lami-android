package io.github.ninbyo02.lami.ui.screens.spriteeditor

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import io.github.ninbyo02.lami.R
import io.github.ninbyo02.lami.ui.common.LocalAppSnackbarHostState
import io.github.ninbyo02.lami.ui.screens.settings.SettingsPreferences
import io.github.ninbyo02.lami.ui.common.PROJECT_SNACKBAR_SHORT_MS
import io.github.ninbyo02.lami.sprite.compositePreserveTransparency
import io.github.ninbyo02.lami.sprite.resolveCurrentSpriteSheetOverrideFile
import io.github.ninbyo02.lami.ui.screens.settings.SpriteSettingsSessionSpriteOverride
import io.github.ninbyo02.lami.ui.components.rememberLamiEditorSpriteBackdropColor
import io.github.ninbyo02.lami.ui.screens.spriteeditor.FILL_REGION_TRANSPARENT_ALPHA_THRESHOLD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import kotlin.math.floor
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
    ColorPalette,
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

internal data class SpriteEditorSheetItem(
    val label: String,
    val testTag: String,
    val opensApplyDialog: Boolean = false,
)

internal fun spriteEditorToolsSheetItems(): List<SpriteEditorSheetItem> {
    return listOf(
        SpriteEditorSheetItem(label = "Color Palette", testTag = "spriteEditorSheetItemColorPalette"),
        SpriteEditorSheetItem(label = "Eyedropper", testTag = "spriteEditorSheetItemEyedropper"),
        SpriteEditorSheetItem(label = "Flip Copy", testTag = "spriteEditorSheetItemFlipCopy"),
        SpriteEditorSheetItem(label = "Grayscale", testTag = "spriteEditorSheetItemGrayscale"),
        SpriteEditorSheetItem(label = "Outline", testTag = "spriteEditorSheetItemOutline"),
        SpriteEditorSheetItem(label = "Binarize", testTag = "spriteEditorSheetItemBinarize"),
        SpriteEditorSheetItem(label = "Reduce to 256 Colors", testTag = "spriteEditorSheetItemReduceTo256Colors"),
        SpriteEditorSheetItem(label = "Clear Background", testTag = "spriteEditorSheetItemClearBackground"),
        SpriteEditorSheetItem(label = "Fill Connected", testTag = "spriteEditorSheetItemFillConnected"),
        SpriteEditorSheetItem(label = "Fill Selection", testTag = "spriteEditorSheetItemFillSelection"),
        SpriteEditorSheetItem(label = "Clear Region", testTag = "spriteEditorSheetItemClearRegion"),
        SpriteEditorSheetItem(
            label = "Center Content in Box",
            testTag = "spriteEditorSheetItemCenterContentInBox",
        ),
    )
}

internal data class SpriteEditorColorHistory(
    val currentColor: Int,
    val recentColors: List<Int>,
) {
    fun select(color: Int): SpriteEditorColorHistory {
        val opaqueColor = color or 0xFF000000.toInt()
        val opaqueCurrent = currentColor or 0xFF000000.toInt()
        if (opaqueColor == opaqueCurrent) {
            return this
        }
        return SpriteEditorColorHistory(
            currentColor = opaqueColor,
            recentColors = (listOf(opaqueCurrent) + recentColors.map { it or 0xFF000000.toInt() })
                .filter { it != opaqueColor }
                .distinct()
                .take(8),
        )
    }
}

internal const val DISMISS_COLOR_PALETTE_AFTER_SELECTION = false

internal fun spriteEditorPaletteSelectionRingWidthDp(selected: Boolean): Int? = if (selected) 3 else null

internal fun eyedropperPaletteColorForSample(sampled: Int): Int? {
    return if (android.graphics.Color.alpha(sampled) == 0) null else nearestFixedPaletteColor(sampled)
}

internal data class EyedropperSelectionDecision(
    val selectedColor: Int?,
    val activateTapFallback: Boolean,
    val message: String,
)

internal fun decideEyedropperSelectionResult(
    result: UniformSelectionColorResult,
): EyedropperSelectionDecision {
    return when (result.status) {
        UniformSelectionColorStatus.UNIFORM -> {
            val color = result.color
            if (color == null || android.graphics.Color.alpha(color) == 0) {
                EyedropperSelectionDecision(null, false, "Cannot read selection color")
            } else {
                EyedropperSelectionDecision(
                    selectedColor = nearestFixedPaletteColor(color),
                    activateTapFallback = false,
                    message = "Color selected from box",
                )
            }
        }
        UniformSelectionColorStatus.TRANSPARENT ->
            EyedropperSelectionDecision(null, false, "Selection is transparent")
        UniformSelectionColorStatus.MIXED ->
            EyedropperSelectionDecision(null, true, "Selection contains multiple colors. Tap a pixel.")
        UniformSelectionColorStatus.CANCELLED ->
            EyedropperSelectionDecision(null, false, "Color scan cancelled")
        UniformSelectionColorStatus.TOO_LARGE ->
            EyedropperSelectionDecision(null, false, "Selection too large")
        UniformSelectionColorStatus.RECYCLED,
        UniformSelectionColorStatus.UNSUPPORTED_CONFIG,
        UniformSelectionColorStatus.READ_FAILED ->
            EyedropperSelectionDecision(null, false, "Cannot read selection color")
    }
}

internal data class SpriteEditorPaletteSwatchSemantics(
    val contentDescription: String,
    val testTag: String,
    val selected: Boolean,
)

internal fun spriteEditorPaletteSwatchSemantics(
    label: String,
    color: Int,
    currentColor: Int,
    testTag: String,
): SpriteEditorPaletteSwatchSemantics {
    return SpriteEditorPaletteSwatchSemantics(
        contentDescription = spriteEditorPaletteSwatchContentDescription(label, color),
        testTag = testTag,
        selected = (color and 0x00FFFFFF) == (currentColor and 0x00FFFFFF),
    )
}

internal fun shouldPushHistoryForPaletteBitmapResult(result: PaletteBitmapResult): Boolean {
    return result.changed && !result.rejected
}

internal data class PaletteBitmapApplicationDecision(
    val adopted: Boolean,
    val message: String,
)

internal fun decidePaletteBitmapApplication(
    currentUnchanged: Boolean,
    result: PaletteBitmapResult,
    unchangedMessage: String = "No pixels changed",
    appliedMessage: String,
): PaletteBitmapApplicationDecision {
    return when {
        !currentUnchanged -> PaletteBitmapApplicationDecision(
            adopted = false,
            message = "Sprite changed; operation skipped",
        )

        result.rejected -> PaletteBitmapApplicationDecision(
            adopted = false,
            message = paletteBitmapResultMessage(result),
        )

        !shouldPushHistoryForPaletteBitmapResult(result) -> PaletteBitmapApplicationDecision(
            adopted = false,
            message = unchangedMessage,
        )

        else -> PaletteBitmapApplicationDecision(
            adopted = true,
            message = appliedMessage,
        )
    }
}

internal fun spriteEditorPaletteSwatchContentDescription(label: String, color: Int): String {
    return "$label ${spriteEditorPaletteHexColor(color)}"
}

internal fun spriteEditorPaletteHexColor(color: Int): String {
    return "#%06X".format(color and 0x00FFFFFF)
}

internal class PaletteBitmapResultOwner(
    private val source: Bitmap,
) : AutoCloseable {
    private val owned = AtomicReference<PaletteBitmapResult?>(null)

    fun publish(result: PaletteBitmapResult): PaletteBitmapResult {
        val previous = owned.getAndSet(result)
        recycleIfNew(previous)
        return result
    }

    fun current(): PaletteBitmapResult? = owned.get()

    fun take(): PaletteBitmapResult? = owned.getAndSet(null)

    override fun close() {
        recycleIfNew(owned.getAndSet(null))
    }

    private fun recycleIfNew(result: PaletteBitmapResult?) {
        val bitmap = result?.bitmap ?: return
        if (bitmap !== source && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

private fun paletteBitmapResultMessage(result: PaletteBitmapResult): String {
    return when (result.rejectionReason) {
        PaletteBitmapRejectionReason.NONE -> "No pixels changed"
        PaletteBitmapRejectionReason.TOO_LARGE -> "Image too large for sprite operation (max 4,194,304 pixels)"
        PaletteBitmapRejectionReason.CANCELLED -> "Operation cancelled"
        PaletteBitmapRejectionReason.RECYCLED,
        PaletteBitmapRejectionReason.UNSUPPORTED_CONFIG,
        PaletteBitmapRejectionReason.COPY_FAILED,
        PaletteBitmapRejectionReason.READ_FAILED,
        PaletteBitmapRejectionReason.WRITE_FAILED -> "Sprite operation rejected"
    }
}

private sealed class LastToolOp {
    data object Grayscale : LastToolOp()
    data object Outline : LastToolOp()
    data object Binarize : LastToolOp()
    data object ReduceTo256Colors : LastToolOp()
    data object ClearBackground : LastToolOp()
    data object ClearRegion : LastToolOp()
    data object FillConnected : LastToolOp()
    data object CenterContentInBox : LastToolOp()
    data class ResizeSelection(
        val targetMaxPx: Int,
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
            LastToolOp.ReduceTo256Colors -> listOf("ReduceTo256Colors")
            LastToolOp.ClearBackground -> listOf("ClearBackground")
            LastToolOp.ClearRegion -> listOf("ClearRegion")
            LastToolOp.FillConnected -> listOf("FillConnected")
            LastToolOp.CenterContentInBox -> listOf("CenterContentInBox")
            is LastToolOp.ResizeSelection -> listOf(
                "ResizeSelection",
                op.targetMaxPx.toString(),
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
            "ReduceTo256Colors" -> LastToolOp.ReduceTo256Colors
            "ClearBackground" -> LastToolOp.ClearBackground
            "ClearRegion" -> LastToolOp.ClearRegion
            "FillConnected" -> LastToolOp.FillConnected
            "CenterContentInBox" -> LastToolOp.CenterContentInBox
            "ResizeSelection" -> {
                val targetMaxPx = data.getOrNull(1)?.toIntOrNull() ?: 96
                val anchorName = data.getOrNull(2) ?: ResizeAnchor.TopLeft.name
                val anchor = try { ResizeAnchor.valueOf(anchorName) } catch (_: IllegalArgumentException) { ResizeAnchor.TopLeft }
                val stepFactor = data.getOrNull(3)?.toFloatOrNull() ?: 0.5f
                val modeName = data.getOrNull(4) ?: ResizeDownscaleMode.PixelArtStable.name
                val downscaleMode = try { ResizeDownscaleMode.valueOf(modeName) } catch (_: IllegalArgumentException) { ResizeDownscaleMode.PixelArtStable }
                val methodName = data.getOrNull(5) ?: PixelArtStableMethod.CenterSample.name
                val pixelArtMethod = try { PixelArtStableMethod.valueOf(methodName) } catch (_: IllegalArgumentException) { PixelArtStableMethod.CenterSample }
                LastToolOp.ResizeSelection(targetMaxPx, anchor, stepFactor, downscaleMode, pixelArtMethod)
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
    var isMinecraftSkinOverlayEnabled by rememberSaveable { mutableStateOf(false) }
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
    var resizeTargetMaxPx by rememberSaveable { mutableStateOf(96) }
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
    var canvasStretchMode by rememberSaveable { mutableStateOf(CanvasStretchMode.None) }
    var lastToolOp by rememberSaveable(stateSaver = LastToolOpSaver) { mutableStateOf<LastToolOp?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val undoStack = remember { ArrayDeque<EditorSnapshot>() }
    val redoStack = remember { ArrayDeque<EditorSnapshot>() }
    var fillStatusText by remember { mutableStateOf("Fill: mode=-") }
    var lastFillConnectedSeedType by remember { mutableStateOf<FillConnectedSeedType?>(null) }
    var currentColor by rememberSaveable { mutableStateOf(0xFF000000.toInt()) }
    var recentColors by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
    var isEyedropperActive by remember { mutableStateOf(false) }
    var paletteOperationJob by remember { mutableStateOf<Job?>(null) }
    val isPaletteOperationRunning = paletteOperationJob?.isActive == true
    val latestEditorState by rememberUpdatedState(editorState)
    val latestPreviewSize by rememberUpdatedState(previewSize)
    val latestDisplayScale by rememberUpdatedState(displayScale)
    val latestPanOffset by rememberUpdatedState(panOffset)

    fun selectCurrentColor(color: Int) {
        val updated = SpriteEditorColorHistory(currentColor, recentColors).select(color)
        currentColor = updated.currentColor
        recentColors = updated.recentColors
    }

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

    fun launchPaletteOperation(block: suspend CoroutineScope.() -> Unit) {
        if (paletteOperationJob?.isActive == true) {
            scope.launch { showSnackbarMessage("Sprite operation already running") }
            return
        }
        var jobRef: Job? = null
        val job = scope.launch {
            try {
                block()
            } finally {
                if (paletteOperationJob === jobRef) {
                    paletteOperationJob = null
                }
            }
        }
        jobRef = job
        paletteOperationJob = job
    }

    suspend fun runPaletteBitmapOperation(
        sourceBitmap: Bitmap,
        operation: ((row: Int) -> Boolean) -> PaletteBitmapResult,
        onResult: (PaletteBitmapResult, PaletteBitmapResultOwner) -> PaletteBitmapApplicationDecision,
    ) {
        val owner = PaletteBitmapResultOwner(sourceBitmap)
        val message = try {
            withContext(Dispatchers.Default) {
                owner.publish(operation { !isActive })
            }
            val result = owner.current() ?: return
            onResult(result, owner).message
        } finally {
            owner.close()
        }
        showSnackbarMessage(message)
    }

    fun applyPaletteBitmapResult(
        current: SpriteEditorState,
        result: PaletteBitmapResult,
        owner: PaletteBitmapResultOwner,
        unchangedMessage: String = "No pixels changed",
        appliedMessage: String,
        applied: () -> Unit = {},
    ): PaletteBitmapApplicationDecision {
        val decision = decidePaletteBitmapApplication(
            currentUnchanged = editorState === current,
            result = result,
            unchangedMessage = unchangedMessage,
            appliedMessage = appliedMessage,
        )
        if (decision.adopted) {
            pushUndoSnapshot(current, undoStack, redoStack)
            val nextEditorState = current.withBitmap(result.bitmap)
            editorState = nextEditorState
            owner.take()
            isDirty = true
            applied()
        }
        return decision
    }

    fun runResizeSelection(
        current: SpriteEditorState,
        targetMaxPx: Int,
        anchor: ResizeAnchor,
        stepFactor: Float,
        downscaleMode: ResizeDownscaleMode,
        pixelArtMethod: PixelArtStableMethod,
        repeated: Boolean,
    ) {
        val resizeResult = when (targetMaxPx) {
            64 -> resizeSelectionToMax64(current.bitmap, current.selection, anchor, stepFactor, 4, downscaleMode, pixelArtMethod)
            128 -> resizeSelectionToMax128(current.bitmap, current.selection, anchor, stepFactor, 4, downscaleMode, pixelArtMethod)
            288 -> resizeSelectionToMax288(current.bitmap, current.selection, anchor, stepFactor, 4, downscaleMode, pixelArtMethod)
            else -> resizeSelectionToMax96(current.bitmap, current.selection, targetMaxPx, anchor, stepFactor, 4, downscaleMode, pixelArtMethod)
        }
        if (!resizeResult.applied) {
            scope.launch { showSnackbarMessage("Resize skipped (already <= ${targetMaxPx}px)") }
            return
        }
        pushUndoSnapshot(current, undoStack, redoStack)
        editorState = current.withBitmap(resizeResult.bitmap).withSelection(resizeResult.selection)
        isDirty = true
        lastToolOp = LastToolOp.ResizeSelection(targetMaxPx, anchor, stepFactor, downscaleMode, pixelArtMethod)
        val message = if (repeated) "Repeated: Resize max ${targetMaxPx}px" else "Resize max ${targetMaxPx}px applied"
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

    Scaffold(
        // Settings 系では Scaffold 自体は Insets を受けず、topBar/content の座標だけを返す
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                // 上端余白の重複を防ぐため、TopAppBar 側の Insets は 0 に固定する
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
                modifier = Modifier
                    // [dp] 縦: TopAppBar 本体の描画領域は従来どおり 48.dp に保つ
                    .fillMaxWidth()
                    .height(48.dp)
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                // [非dp] 縦横: 画面全体 の fillMaxSize(制約)に関係
                .fillMaxSize()
                // 上下左右: Scaffold と TopBar が決めた描画領域に本文を揃える
                .padding(innerPadding)
                // Scaffold の Insets はこの階層で消費し、下位レイアウトへ重複させない
                .consumeWindowInsets(innerPadding),
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
                        val minecraftSkinPartLabel = remember(
                            isMinecraftSkinOverlayEnabled,
                            state?.bitmap?.width,
                            state?.bitmap?.height,
                            state?.selection,
                        ) {
                            val current = state
                            if (!isMinecraftSkinOverlayEnabled || current == null) {
                                null
                            } else {
                                minecraftSkinPartLabelAt(
                                    width = current.bitmap.width,
                                    height = current.bitmap.height,
                                    x = current.selection.x,
                                    y = current.selection.y,
                                ) ?: "-"
                            }
                        }
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
                                .pointerInput(
                                    isEyedropperActive,
                                ) {
                                    if (!isEyedropperActive) return@pointerInput
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = true)
                                        val up = waitForUpOrCancellation()
                                        if (up == null || up.isConsumed) {
                                            return@awaitEachGesture
                                        }
                                        val current = latestEditorState ?: return@awaitEachGesture
                                        val pixelOffset = previewOffsetToBitmapPixel(
                                            position = up.position,
                                            viewSize = latestPreviewSize,
                                            bitmapWidth = current.bitmap.width,
                                            bitmapHeight = current.bitmap.height,
                                            displayScale = latestDisplayScale,
                                            panOffset = latestPanOffset,
                                        ) ?: return@awaitEachGesture
                                        val sampled = current.bitmap.getPixel(pixelOffset.x, pixelOffset.y)
                                        val selectedColor = eyedropperPaletteColorForSample(sampled)
                                        if (selectedColor == null) {
                                            isEyedropperActive = false
                                            scope.launch {
                                                showSnackbarMessage("Selected pixel is transparent")
                                            }
                                            return@awaitEachGesture
                                        }
                                        selectCurrentColor(selectedColor)
                                        isEyedropperActive = false
                                    }
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
                                        val renderLeft = (size.width - destinationWidth) / 2f + panOffset.x
                                        val renderTop = (size.height - destinationHeight) / 2f + panOffset.y
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
                                        val renderLeft = (size.width - destinationWidth) / 2f + panOffset.x
                                        val renderTop = (size.height - destinationHeight) / 2f + panOffset.y
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
                                        if (isMinecraftSkinOverlayEnabled) {
                                            val skinRegions = minecraftSkinRegions(
                                                width = state.bitmap.width,
                                                height = state.bitmap.height,
                                            )
                                            if (skinRegions.isNotEmpty()) {
                                                val shadowColor = Color.Black.copy(alpha = 0.52f)
                                                val lineColor = Color(0xFF40E0D0).copy(alpha = 0.86f)
                                                val strokeWidth = max(1f, min(2.5f, renderScale / 3f))
                                                skinRegions.forEach { region ->
                                                    val left = renderLeft + region.x * renderScale
                                                    val top = renderTop + region.y * renderScale
                                                    val regionSize = Size(
                                                        width = region.w * renderScale,
                                                        height = region.h * renderScale,
                                                    )
                                                    drawRect(
                                                        color = shadowColor,
                                                        topLeft = Offset(left, top),
                                                        size = regionSize,
                                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                            width = strokeWidth + 1f,
                                                        ),
                                                    )
                                                    drawRect(
                                                        color = lineColor,
                                                        topLeft = Offset(left, top),
                                                        size = regionSize,
                                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                            width = strokeWidth,
                                                        ),
                                                    )
                                                }
                                            }
                                        }
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
                                                    x = renderLeft + copiedXPx,
                                                    y = renderTop + copiedYPx,
                                                ),
                                                size = Size(
                                                    width = copiedWPx.toFloat(),
                                                    height = copiedHPx.toFloat(),
                                                ),
                                            )
                                            drawRect(
                                                color = copiedColor,
                                                topLeft = Offset(
                                                    x = renderLeft + copiedXPx,
                                                    y = renderTop + copiedYPx,
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
                                                    x = floor(renderLeft + selectionXPx).toInt(),
                                                    y = floor(renderTop + selectionYPx).toInt(),
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
                                                x = renderLeft + selectionXPx,
                                                y = renderTop + selectionYPx,
                                            ),
                                            size = Size(
                                                width = selectionWPx.toFloat(),
                                                height = selectionHPx.toFloat(),
                                            ),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx.toFloat()),
                                        )
                                    }
                                }
                                if (minecraftSkinPartLabel != null) {
                                    Text(
                                        text = "MC: $minecraftSkinPartLabel",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                                shape = RoundedCornerShape(4.dp),
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("spriteEditorMinecraftSkinPart"),
                                    )
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
                                            enabled = !isPaletteOperationRunning,
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

                                                            LastToolOp.ReduceTo256Colors -> {
                                                                launchPaletteOperation {
                                                                    val sourceBitmap = current.bitmap
                                                                    runPaletteBitmapOperation(
                                                                        sourceBitmap = sourceBitmap,
                                                                        operation = { shouldCancel ->
                                                                            reduceToFixedPalette(sourceBitmap, shouldCancel)
                                                                        },
                                                                    ) { result, owner ->
                                                                        applyPaletteBitmapResult(
                                                                            current = current,
                                                                            result = result,
                                                                            owner = owner,
                                                                            appliedMessage = "Repeated: Reduce to 256 Colors",
                                                                        )
                                                                    }
                                                                }
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

                                                            is LastToolOp.ResizeSelection -> {
                                                                runResizeSelection(
                                                                    current,
                                                                    targetMaxPx = op.targetMaxPx,
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
                            BoxWithConstraints(
                                modifier = Modifier.weight(1f)
                            ) {
                                val sideDp = minOf(maxHeight, maxWidth)
                                Box(
                                    modifier = Modifier
                                        .size(sideDp)
                                        .align(Alignment.Center),
                                ) {
                                    previewContent()
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                // [dp] 縦: 右カラムの間隔(間隔)に関係
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
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
        val sheetTitle = when (activeSheet) {
            SheetType.More -> "More"
            SheetType.Tools -> "Tools"
            SheetType.ColorPalette -> "Color Palette"
            SheetType.None -> ""
        }
        val sheetItems = if (activeSheet == SheetType.More) {
            listOf(
                SpriteEditorSheetItem(label = "Resize...", testTag = "spriteEditorSheetItemResize"),
                SpriteEditorSheetItem(label = "Canvas Size...", testTag = "spriteEditorSheetItemCanvasSize"),
                SpriteEditorSheetItem(
                    label = if (isMinecraftSkinOverlayEnabled) {
                        "Minecraft Skin Overlay: ON"
                    } else {
                        "Minecraft Skin Overlay: OFF"
                    },
                    testTag = "spriteEditorSheetItemMinecraftSkinOverlay",
                ),
                SpriteEditorSheetItem(
                    label = "Apply to Sprite...",
                    testTag = "spriteEditorSheetItemApply",
                    opensApplyDialog = true,
                ),
            )
        } else if (activeSheet == SheetType.Tools) {
            spriteEditorToolsSheetItems()
        } else {
            emptyList()
        }
        ModalBottomSheet(
            onDismissRequest = { activeSheet = SheetType.None },
            sheetState = sheetState,
        ) {
            val contentModifier = Modifier
                // [dp] 全体: ボトムシート内容の最小余白(余白)に関係
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .then(
                    if (activeSheet == SheetType.Tools) {
                        Modifier.verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    },
                )
            Column(modifier = contentModifier) {
                Text(
                    text = sheetTitle,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(
                    modifier = Modifier
                        // [dp] 上下: タイトルと項目の間隔(間隔)に関係
                        .height(8.dp)
                )
                if (activeSheet == SheetType.ColorPalette) {
                    SpriteEditorColorPaletteSheet(
                        currentColor = currentColor,
                        recentColors = recentColors,
                        onColorSelected = { color ->
                            selectCurrentColor(color)
                            if (DISMISS_COLOR_PALETTE_AFTER_SELECTION) {
                                activeSheet = SheetType.None
                            }
                        },
                    )
                } else {
                    sheetItems.forEach { item ->
                    Button(
                        onClick = {
                            if (item.opensApplyDialog) {
                                activeSheet = SheetType.None
                                applyDialogComment = ""
                                applyDialogCommentKind = ApplyDialogCommentKind.None
                                showApplyDialog = true
                            } else if (item.testTag == "spriteEditorSheetItemColorPalette") {
                                activeSheet = SheetType.ColorPalette
                            } else if (item.testTag == "spriteEditorSheetItemEyedropper") {
                                val current = editorState
                                if (current == null) {
                                    activeSheet = SheetType.None
                                    isEyedropperActive = false
                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                } else {
                                    activeSheet = SheetType.None
                                    launchPaletteOperation {
                                        val sourceBitmap = current.bitmap
                                        val sourceSelection = current.selection
                                        val result = withContext(Dispatchers.Default) {
                                            findUniformSelectionColor(
                                                sourceBitmap,
                                                sourceSelection,
                                            ) { !isActive }
                                        }
                                        if (editorState !== current) {
                                            return@launchPaletteOperation
                                        }
                                        val decision = decideEyedropperSelectionResult(result)
                                        decision.selectedColor?.let(::selectCurrentColor)
                                        isEyedropperActive = decision.activateTapFallback
                                        showSnackbarMessage(decision.message)
                                    }
                                }
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
                            } else if (item.testTag == "spriteEditorSheetItemReduceTo256Colors") {
                                val current = editorState
                                if (current == null) {
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                } else {
                                    activeSheet = SheetType.None
                                    launchPaletteOperation {
                                        val sourceBitmap = current.bitmap
                                        runPaletteBitmapOperation(
                                            sourceBitmap = sourceBitmap,
                                            operation = { shouldCancel ->
                                                reduceToFixedPalette(sourceBitmap, shouldCancel)
                                            },
                                        ) { result, owner ->
                                            applyPaletteBitmapResult(
                                                current = current,
                                                result = result,
                                                owner = owner,
                                                appliedMessage = "Reduced to 256 colors",
                                            ) {
                                                lastToolOp = LastToolOp.ReduceTo256Colors
                                            }
                                        }
                                    }
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
                            } else if (item.testTag == "spriteEditorSheetItemFillSelection") {
                                val current = editorState
                                if (current == null) {
                                    activeSheet = SheetType.None
                                    scope.launch { showSnackbarMessage("No sprite loaded") }
                                } else {
                                    val fillColor = currentColor
                                    activeSheet = SheetType.None
                                    launchPaletteOperation {
                                        val sourceBitmap = current.bitmap
                                        runPaletteBitmapOperation(
                                            sourceBitmap = sourceBitmap,
                                            operation = { shouldCancel ->
                                                fillSelectionWithColor(
                                                    sourceBitmap,
                                                    current.selection,
                                                    fillColor,
                                                    shouldCancel,
                                                )
                                            },
                                        ) { result, owner ->
                                            applyPaletteBitmapResult(
                                                current = current,
                                                result = result,
                                                owner = owner,
                                                appliedMessage = "Selection filled",
                                            )
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
                                    canvasStretchMode = CanvasStretchMode.None
                                    activeSheet = SheetType.None
                                    showCanvasSizeDialog = true
                                }
                            } else if (item.testTag == "spriteEditorSheetItemMinecraftSkinOverlay") {
                                isMinecraftSkinOverlayEnabled = !isMinecraftSkinOverlayEnabled
                                activeSheet = SheetType.None
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
                        enabled = when (item.testTag) {
                            "spriteEditorSheetItemReduceTo256Colors",
                            "spriteEditorSheetItemFillSelection" -> !isPaletteOperationRunning && editorState != null
                            else -> true
                        },
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
                    Text("選択範囲を指定サイズ内に縮小（縦横比を維持）")
                    Column(
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(64, 96, 128, 288).chunked(2).forEach { targets ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                targets.forEach { target ->
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .selectable(selected = resizeTargetMaxPx == target, onClick = { resizeTargetMaxPx = target }, role = Role.RadioButton)
                                            .testTag("spriteEditorResizeMax$target"),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(selected = resizeTargetMaxPx == target, onClick = null)
                                        Text("最大 $target×$target")
                                    }
                                }
                            }
                        }
                    }
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
                                        targetMaxPx = resizeTargetMaxPx,
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
                                    val clamped = clampPxFieldValue(
                                        canvasWidthInput,
                                        input,
                                        4096,
                                    )
                                    canvasWidthInput = clamped
                                    if (canvasStretchMode == CanvasStretchMode.StretchHeightToWidth) {
                                        canvasHeightInput = TextFieldValue(clamped.text)
                                    }
                                },
                                label = { Text("W(px)") },
                                singleLine = true,
                                readOnly = canvasStretchMode == CanvasStretchMode.StretchWidthToHeight,
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
                                    val clamped = clampPxFieldValue(
                                        canvasHeightInput,
                                        input,
                                        4096,
                                    )
                                    canvasHeightInput = clamped
                                    if (canvasStretchMode == CanvasStretchMode.StretchWidthToHeight) {
                                        canvasWidthInput = TextFieldValue(clamped.text)
                                    }
                                },
                                label = { Text("H(px)") },
                                singleLine = true,
                                readOnly = canvasStretchMode == CanvasStretchMode.StretchHeightToWidth,
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
                            Text("Stretch mode")
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.selectable(
                                        selected = canvasStretchMode == CanvasStretchMode.None,
                                        onClick = { canvasStretchMode = CanvasStretchMode.None },
                                        role = Role.RadioButton,
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = canvasStretchMode == CanvasStretchMode.None,
                                        onClick = null,
                                    )
                                    Text("None")
                                }
                                Row(
                                    modifier = Modifier.selectable(
                                        selected = canvasStretchMode == CanvasStretchMode.StretchWidthToHeight,
                                        onClick = {
                                            canvasStretchMode = CanvasStretchMode.StretchWidthToHeight
                                            canvasWidthInput = TextFieldValue(canvasHeightInput.text)
                                        },
                                        role = Role.RadioButton,
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = canvasStretchMode == CanvasStretchMode.StretchWidthToHeight,
                                        onClick = null,
                                    )
                                    Text("Stretch width to height")
                                }
                                Row(
                                    modifier = Modifier.selectable(
                                        selected = canvasStretchMode == CanvasStretchMode.StretchHeightToWidth,
                                        onClick = {
                                            canvasStretchMode = CanvasStretchMode.StretchHeightToWidth
                                            canvasHeightInput = TextFieldValue(canvasWidthInput.text)
                                        },
                                        role = Role.RadioButton,
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = canvasStretchMode == CanvasStretchMode.StretchHeightToWidth,
                                        onClick = null,
                                    )
                                    Text("Stretch height to width")
                                }
                            }
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
                                canvasStretchMode = CanvasStretchMode.None
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
                                val targetSize = calculateCanvasStretchTargetSize(
                                    sourceWidth = current.bitmap.width,
                                    sourceHeight = current.bitmap.height,
                                    requestedWidth = safeW,
                                    requestedHeight = safeH,
                                    stretchMode = canvasStretchMode,
                                )
                                canvasWidthInput = TextFieldValue(targetSize.width.toString())
                                canvasHeightInput = TextFieldValue(targetSize.height.toString())
                                if (
                                    targetSize.width == current.bitmap.width &&
                                    targetSize.height == current.bitmap.height
                                ) {
                                    scope.launch { showSnackbarMessage("Canvas unchanged") }
                                    return@SpriteEditorCancelApplyRow
                                }
                                pushUndoSnapshot(current, undoStack, redoStack)
                                val resizedBitmap = when (canvasStretchMode) {
                                    CanvasStretchMode.None -> resizeCanvas(
                                        current.bitmap,
                                        targetSize.width,
                                        targetSize.height,
                                        canvasAnchor,
                                    )

                                    CanvasStretchMode.StretchWidthToHeight,
                                    CanvasStretchMode.StretchHeightToWidth -> stretchCanvasToSize(
                                        current.bitmap,
                                        targetSize.width,
                                        targetSize.height,
                                    )
                                }
                                val nextSelection = rectNormalizeClamp(
                                    current.selection,
                                    targetSize.width,
                                    targetSize.height,
                                )
                                editorState = current.withBitmap(resizedBitmap).withSelection(nextSelection)
                                isDirty = true
                                activeSheet = SheetType.None
                                scope.launch {
                                    showSnackbarMessage("Canvas resized to ${targetSize.width}x${targetSize.height}")
                                }
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
    enabled: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
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

@Composable
private fun SpriteEditorColorPaletteSheet(
    currentColor: Int,
    recentColors: List<Int>,
    onColorSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Current Color", style = MaterialTheme.typography.labelMedium)
        SpriteEditorPaletteSwatch(
            color = currentColor,
            label = "Current Color",
            currentColor = currentColor,
            testTag = "spriteEditorCurrentColor",
            onClick = { onColorSelected(currentColor) },
        )
        if (recentColors.isNotEmpty()) {
            Text("Recent Colors", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                recentColors.take(8).forEachIndexed { index, color ->
                    SpriteEditorPaletteSwatch(
                        color = color,
                        label = "Recent Color ${index + 1}",
                        currentColor = currentColor,
                        testTag = "spriteEditorRecentColor$index",
                        onClick = { onColorSelected(color) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .testTag("spriteEditorFixedPalette"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(FIXED_SPRITE_PALETTE.size) { index ->
                val color = FIXED_SPRITE_PALETTE[index]
                SpriteEditorPaletteSwatch(
                    color = color,
                    label = "Palette Color $index",
                    currentColor = currentColor,
                    testTag = "spriteEditorPaletteColor$index",
                    onClick = { onColorSelected(color) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpriteEditorPaletteSwatch(
    color: Int,
    label: String,
    currentColor: Int,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantics = spriteEditorPaletteSwatchSemantics(label, color, currentColor, testTag)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .minimumInteractiveComponentSize()
            .semantics {
                this.contentDescription = semantics.contentDescription
                this.selected = semantics.selected
            }
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .testTag(semantics.testTag),
        contentAlignment = Alignment.Center,
    ) {
        val ringWidth = spriteEditorPaletteSelectionRingWidthDp(semantics.selected)?.dp
        val ringModifier = if (ringWidth == null) {
            Modifier.size(38.dp)
        } else {
            Modifier
                .size(38.dp)
                .border(ringWidth, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                .padding(ringWidth)
        }
        Box(
            modifier = ringModifier,
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
            )
        }
    }
}

internal fun previewOffsetToBitmapPixel(
    position: Offset,
    viewSize: IntSize,
    bitmapWidth: Int,
    bitmapHeight: Int,
    displayScale: Float,
    panOffset: Offset,
): IntOffset? {
    if (viewSize.width <= 0 || viewSize.height <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
        return null
    }
    val scaleX = viewSize.width.toFloat() / bitmapWidth
    val scaleY = viewSize.height.toFloat() / bitmapHeight
    val fitScale = min(scaleX, scaleY)
    val renderScale = fitScale * displayScale
    if (renderScale <= 0f) {
        return null
    }
    val destinationWidth = bitmapWidth * renderScale
    val destinationHeight = bitmapHeight * renderScale
    val renderLeft = (viewSize.width - destinationWidth) / 2f + panOffset.x
    val renderTop = (viewSize.height - destinationHeight) / 2f + panOffset.y
    val bitmapXFloat = (position.x - renderLeft) / renderScale
    val bitmapYFloat = (position.y - renderTop) / renderScale
    if (bitmapXFloat < 0f || bitmapYFloat < 0f) {
        return null
    }
    if (bitmapXFloat >= bitmapWidth.toFloat() || bitmapYFloat >= bitmapHeight.toFloat()) {
        return null
    }
    return IntOffset(floor(bitmapXFloat).toInt(), floor(bitmapYFloat).toInt())
}

private fun internalAutosaveFile(context: android.content.Context): File {
    return File(context.filesDir, "sprite_editor/sprite_editor_autosave.png")
}

private fun currentSpriteSheetOverrideFile(context: android.content.Context): File {
    return resolveCurrentSpriteSheetOverrideFile(context)
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
