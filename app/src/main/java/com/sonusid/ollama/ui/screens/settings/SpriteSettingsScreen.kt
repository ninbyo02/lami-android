package com.sonusid.ollama.ui.screens.settings

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sonusid.ollama.R
import com.sonusid.ollama.data.SpriteSheetConfig
import com.sonusid.ollama.data.boxesWithInternalIndex
import com.sonusid.ollama.data.isUninitialized
import com.sonusid.ollama.data.toInternalFrameIndex
import com.sonusid.ollama.data.BoxPosition as SpriteSheetBoxPosition
import com.sonusid.ollama.ui.components.SpriteFrameRegion
import com.sonusid.ollama.ui.components.drawFramePlaceholder
import com.sonusid.ollama.ui.components.drawFrameRegion
import com.sonusid.ollama.ui.components.toDstRect
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BoxPosition(val x: Int, val y: Int)

private const val DEFAULT_BOX_SIZE_PX = 88

private fun clampPosition(
    position: BoxPosition,
    boxSizePx: Int,
    sheetWidth: Int,
    sheetHeight: Int
): BoxPosition {
    val maxX = (sheetWidth - boxSizePx).coerceAtLeast(0)
    val maxY = (sheetHeight - boxSizePx).coerceAtLeast(0)
    return BoxPosition(
        x = position.x.coerceIn(0, maxX),
        y = position.y.coerceIn(0, maxY)
    )
}

private fun boxPositionsSaver() = androidx.compose.runtime.saveable.listSaver<List<BoxPosition>, Int>(
    save = { list -> list.flatMap { position -> listOf(position.x, position.y) } },
    restore = { flat ->
        flat.chunked(2).map { (x, y) ->
            BoxPosition(x = x, y = y)
        }
    }
)

private fun defaultBoxPositions(): List<BoxPosition> =
    SpriteSheetConfig.default3x3()
        .boxesWithInternalIndex()
        .sortedBy { it.frameIndex }
        .map { box -> BoxPosition(box.x, box.y) }

@Composable
fun SpriteSettingsScreen(navController: NavController) {
    val imageBitmap: ImageBitmap =
        ImageBitmap.imageResource(LocalContext.current.resources, R.drawable.lami_sprite_3x3_288)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val settingsPreferences = remember(context.applicationContext) {
        SettingsPreferences(context.applicationContext)
    }
    val spriteSheetConfig by settingsPreferences.spriteSheetConfig.collectAsState(initial = SpriteSheetConfig.default3x3())
    val readyAnimationSettings by settingsPreferences.readyAnimationSettings.collectAsState(initial = ReadyAnimationSettings.DEFAULT)

    var selectedNumber by rememberSaveable { mutableStateOf(1) }
    var boxSizePx by rememberSaveable { mutableStateOf(DEFAULT_BOX_SIZE_PX) }
    var boxPositions by rememberSaveable(stateSaver = boxPositionsSaver()) { mutableStateOf(defaultBoxPositions()) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var displayScale by remember { mutableStateOf(1f) }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var readyFrameInput by rememberSaveable { mutableStateOf("1,2,3,2") }
    var readyIntervalInput by rememberSaveable { mutableStateOf("700") }
    var appliedReadyFrames by rememberSaveable { mutableStateOf(listOf(0, 1, 2, 1)) }
    var appliedReadyIntervalMs by rememberSaveable { mutableStateOf(700) }
    var readyFramesError by rememberSaveable { mutableStateOf<String?>(null) }
    var readyIntervalError by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAnimation by rememberSaveable { mutableStateOf("Ready") }

    LaunchedEffect(spriteSheetConfig) {
        val validConfig = spriteSheetConfig
            .takeIf { it.isUninitialized().not() && it.validate() == null }
            ?.copy(boxes = spriteSheetConfig.boxesWithInternalIndex())
            ?: SpriteSheetConfig.default3x3()

        val resolvedBoxes = validConfig.boxes
        boxSizePx = validConfig.frameWidth.coerceAtLeast(1)
        boxPositions = resolvedBoxes
            .sortedBy { it.frameIndex }
            .map { position -> BoxPosition(position.x, position.y) }
        selectedNumber = selectedNumber.coerceIn(1, boxPositions.size.coerceAtLeast(1))
    }

    LaunchedEffect(readyAnimationSettings) {
        val normalizedFrames = readyAnimationSettings.frameSequence.ifEmpty { listOf(0) }
        appliedReadyFrames = normalizedFrames
        appliedReadyIntervalMs = readyAnimationSettings.intervalMs
        readyFrameInput = normalizedFrames.joinToString(separator = ",") { value -> (value + 1).toString() }
        readyIntervalInput = readyAnimationSettings.intervalMs.toString()
    }

    val selectedIndex = selectedNumber - 1
    val selectedPosition = boxPositions.getOrNull(selectedIndex)

    fun clampAllPositions(newBoxSizePx: Int): List<BoxPosition> =
        boxPositions.map { position ->
            clampPosition(position, newBoxSizePx, imageBitmap.width, imageBitmap.height)
        }

    fun updateBoxSize(delta: Int) {
        val maxSize = minOf(imageBitmap.width, imageBitmap.height).coerceAtLeast(1)
        val desiredSize = (boxSizePx + delta).coerceIn(1, maxSize)
        if (desiredSize != boxSizePx) {
            boxSizePx = desiredSize
            boxPositions = clampAllPositions(desiredSize)
        }
    }

    fun updateSelectedPosition(deltaX: Int, deltaY: Int) {
        if (selectedIndex !in boxPositions.indices) return
        val current = boxPositions[selectedIndex]
        val updated = clampPosition(
            position = BoxPosition(
                x = current.x + deltaX,
                y = current.y + deltaY
            ),
            boxSizePx = boxSizePx,
            sheetWidth = imageBitmap.width,
            sheetHeight = imageBitmap.height
        )
        if (updated != current) {
            boxPositions = boxPositions.toMutableList().also { positions ->
                positions[selectedIndex] = updated
            }
        }
    }

    fun buildSpriteSheetConfig(): SpriteSheetConfig {
        val spriteSheetConfig = SpriteSheetConfig(
            rows = 3,
            cols = 3,
            frameWidth = boxSizePx,
            frameHeight = boxSizePx,
            boxes = boxPositions.mapIndexed { index, position ->
                SpriteSheetBoxPosition(
                    frameIndex = index,
                    x = position.x,
                    y = position.y,
                    width = boxSizePx,
                    height = boxSizePx
                )
            }
        )
        return spriteSheetConfig
    }

    fun parseReadyFrames(input: String): List<Int>? {
        val normalized = input
            .replace("，", ",")
            .replace("、", ",")
            .split(",")
            .map { token -> token.trim() }
            .filter { token -> token.isNotEmpty() }
        if (normalized.isEmpty()) return null
        val parsed = normalized.mapNotNull { token -> token.toIntOrNull() }
        if (parsed.size != normalized.size) return null
        if (parsed.any { value -> value !in 1..9 }) return null
        // UIは1始まりのため、内部利用時に0始まりへ変換する
        return parsed.map { value -> value - 1 }
    }

    fun parseReadyIntervalMs(input: String): Int? {
        val rawValue = input.trim().toIntOrNull() ?: return null
        if (rawValue < 1) return null
        return rawValue
    }

    fun saveSpriteSheetConfig() {
        coroutineScope.launch {
            runCatching {
                val config = buildSpriteSheetConfig()
                val error = config.validate()
                if (error != null) {
                    throw IllegalArgumentException(error)
                }
                settingsPreferences.saveSpriteSheetConfig(config)
            }.onSuccess {
                snackbarHostState.showSnackbar("保存しました")
            }.onFailure { throwable ->
                snackbarHostState.showSnackbar("保存に失敗しました: ${throwable.message}")
            }
        }
    }

    fun copySpriteSheetConfig() {
        coroutineScope.launch {
            runCatching {
                val config = buildSpriteSheetConfig()
                val error = config.validate()
                if (error != null) {
                    throw IllegalArgumentException(error)
                }
                val jsonString = config.toJson()
                clipboardManager.setText(AnnotatedString(jsonString))
            }.onSuccess {
                snackbarHostState.showSnackbar("JSONをコピーしました")
            }.onFailure { throwable ->
                snackbarHostState.showSnackbar("コピーに失敗しました: ${throwable.message}")
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }, bottomBar = {
        if (tabIndex == 0) {
            SpriteSettingsControls(
                selectedNumber = selectedNumber,
                selectedPosition = selectedPosition,
                boxSizePx = boxSizePx,
                onPrev = { selectedNumber = if (selectedNumber <= 1) 9 else selectedNumber - 1 },
                onNext = { selectedNumber = if (selectedNumber >= 9) 1 else selectedNumber + 1 },
                onMoveXNegative = { updateSelectedPosition(deltaX = -1, deltaY = 0) },
                onMoveXPositive = { updateSelectedPosition(deltaX = 1, deltaY = 0) },
                onMoveYNegative = { updateSelectedPosition(deltaX = 0, deltaY = -1) },
                onMoveYPositive = { updateSelectedPosition(deltaX = 0, deltaY = 1) },
                onSizeDecrease = { updateBoxSize(-4) },
                onSizeIncrease = { updateBoxSize(4) },
                onSave = { saveSpriteSheetConfig() },
                onCopy = { copySpriteSheetConfig() }
            )
        }
    }) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.back),
                        contentDescription = "戻る"
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Text("Sprite Settings")
                    Spacer(modifier = Modifier.height(8.dp))
                    TabRow(selectedTabIndex = tabIndex) {
                        Tab(
                            selected = tabIndex == 0,
                            onClick = { tabIndex = 0 },
                            text = { Text("調整") }
                        )
                        Tab(
                            selected = tabIndex == 1,
                            onClick = { tabIndex = 1 },
                            text = { Text("アニメ") }
                        )
                    }
                    when (tabIndex) {
                        0 -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("元画像解像度: ${imageBitmap.width} x ${imageBitmap.height} px")
                                    Text("表示倍率: ${"%.2f".format(displayScale)}x")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .padding(top = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.lami_sprite_3x3_288),
                                        contentDescription = "Sprite Preview",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .onSizeChanged { newContainerSize: IntSize ->
                                                containerSize = newContainerSize
                                                if (imageBitmap.width != 0) {
                                                    displayScale = newContainerSize.width / imageBitmap.width.toFloat()
                                                }
                                            },
                                        contentScale = ContentScale.Fit
                                    )
                                    if (selectedPosition != null && containerSize.width > 0 && containerSize.height > 0) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val scaleX = this.size.width / imageBitmap.width
                                            val scaleY = this.size.height / imageBitmap.height
                                            drawRect(
                                                color = Color.Red,
                                                topLeft = Offset(
                                                    x = selectedPosition.x * scaleX,
                                                    y = selectedPosition.y * scaleY
                                                ),
                                                size = Size(
                                                    width = boxSizePx * scaleX,
                                                    height = boxSizePx * scaleY
                                                ),
                                                style = Stroke(width = 2.dp.toPx())
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("操作バーは下部に固定されています")
                                    Text("選択中: ${selectedNumber}/9")
                                }
                            }
                        }

                        1 -> {
                            ReadyAnimationTab(
                                imageBitmap = imageBitmap,
                                spriteSheetConfig = spriteSheetConfig,
                                selectedAnimation = selectedAnimation,
                                onSelectedAnimationChange = { selectedAnimation = it },
                                readyFrameInput = readyFrameInput,
                                onReadyFrameInputChange = {
                                    readyFrameInput = it
                                    readyFramesError = null
                                },
                                readyIntervalInput = readyIntervalInput,
                                onReadyIntervalInputChange = {
                                    readyIntervalInput = it
                                    readyIntervalError = null
                                },
                                readyFramesError = readyFramesError,
                                readyIntervalError = readyIntervalError,
                                appliedFrames = appliedReadyFrames,
                                appliedIntervalMs = appliedReadyIntervalMs,
                                onApply = {
                                    val frames = parseReadyFrames(readyFrameInput)
                                    val intervalMs = parseReadyIntervalMs(readyIntervalInput)
                                    if (frames == null) {
                                        readyFramesError = "1〜9のカンマ区切りで入力してください"
                                        return@ReadyAnimationTab
                                    }
                                    if (intervalMs == null) {
                                        readyIntervalError = "1以上の数値を入力してください"
                                        return@ReadyAnimationTab
                                    }
                                    readyFramesError = null
                                    readyIntervalError = null
                                    appliedReadyFrames = frames
                                    appliedReadyIntervalMs = intervalMs
                                    Log.d(
                                        "SpriteAnim",
                                        "Ready applied frames=${frames.map { it + 1 }} intervalMs=$intervalMs"
                                    )
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("プレビューに適用しました")
                                    }
                                },
                                onSave = {
                                    val frames = parseReadyFrames(readyFrameInput)
                                    val intervalMs = parseReadyIntervalMs(readyIntervalInput)
                                    if (frames == null) {
                                        readyFramesError = "1〜9のカンマ区切りで入力してください"
                                        return@ReadyAnimationTab
                                    }
                                    if (intervalMs == null) {
                                        readyIntervalError = "1以上の数値を入力してください"
                                        return@ReadyAnimationTab
                                    }
                                    readyFramesError = null
                                    readyIntervalError = null
                                    coroutineScope.launch {
                                        settingsPreferences.saveReadyAnimationSettings(
                                            ReadyAnimationSettings(
                                                frameSequence = frames,
                                                intervalMs = intervalMs,
                                            )
                                        )
                                        snackbarHostState.showSnackbar("Readyアニメを保存しました")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun ReadyAnimationTab(
    imageBitmap: ImageBitmap,
    spriteSheetConfig: SpriteSheetConfig,
    selectedAnimation: String,
    onSelectedAnimationChange: (String) -> Unit,
    readyFrameInput: String,
    onReadyFrameInputChange: (String) -> Unit,
    readyIntervalInput: String,
    onReadyIntervalInputChange: (String) -> Unit,
    readyFramesError: String?,
    readyIntervalError: String?,
    appliedFrames: List<Int>,
    appliedIntervalMs: Int,
    onApply: () -> Unit,
    onSave: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Readyアニメ設定", modifier = Modifier.padding(horizontal = 8.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isWide = maxWidth > 540.dp
            val inputContent: @Composable () -> Unit = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimationDropdown(
                        items = listOf("Ready", "Insertion"),
                        selectedItem = selectedAnimation,
                        onSelectedItemChange = onSelectedAnimationChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (selectedAnimation == "Ready") {
                        ReadyForm(
                            readyFrameInput = readyFrameInput,
                            onReadyFrameInputChange = onReadyFrameInputChange,
                            readyIntervalInput = readyIntervalInput,
                            onReadyIntervalInputChange = onReadyIntervalInputChange,
                            readyFramesError = readyFramesError,
                            readyIntervalError = readyIntervalError,
                            onApply = onApply,
                            onSave = onSave
                        )
                    } else {
                        Text("未実装", modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
            val previewContent: @Composable () -> Unit = {
                ReadyAnimationPreview(
                    imageBitmap = imageBitmap,
                    spriteSheetConfig = spriteSheetConfig,
                    frames = appliedFrames,
                    intervalMs = appliedIntervalMs,
                )
            }
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) { inputContent() }
                    Box(modifier = Modifier.weight(1f)) { previewContent() }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    inputContent()
                    previewContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimationDropdown(
    items: List<String>,
    selectedItem: String,
    onSelectedItemChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        TextField(
            value = selectedItem,
            onValueChange = {},
            readOnly = true,
            label = { Text("アニメ種別") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onSelectedItemChange(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ReadyForm(
    readyFrameInput: String,
    onReadyFrameInputChange: (String) -> Unit,
    readyIntervalInput: String,
    onReadyIntervalInputChange: (String) -> Unit,
    readyFramesError: String?,
    readyIntervalError: String?,
    onApply: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = readyFrameInput,
            onValueChange = onReadyFrameInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("フレーム列 (例: 1,2,3)") },
            singleLine = true,
            isError = readyFramesError != null,
            supportingText = readyFramesError?.let { errorText ->
                { Text(errorText, color = Color.Red) }
            }
        )
        OutlinedTextField(
            value = readyIntervalInput,
            onValueChange = onReadyIntervalInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("周期 (ms)") },
            singleLine = true,
            isError = readyIntervalError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = readyIntervalError?.let { errorText ->
                { Text(errorText, color = Color.Red) }
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(modifier = Modifier.weight(1f), onClick = onApply) {
                Text("更新")
            }
            Button(modifier = Modifier.weight(1f), onClick = onSave) {
                Text("保存")
            }
        }
    }
}

@Composable
private fun ReadyAnimationPreview(
    imageBitmap: ImageBitmap,
    spriteSheetConfig: SpriteSheetConfig,
    frames: List<Int>,
    intervalMs: Int,
) {
    val resolvedFrames = frames.ifEmpty { listOf(0) }
    var currentFrameIndex by remember(resolvedFrames) { mutableStateOf(0) }

    LaunchedEffect(resolvedFrames, intervalMs) {
        if (resolvedFrames.isEmpty()) return@LaunchedEffect
        currentFrameIndex = 0
        while (isActive && resolvedFrames.isNotEmpty()) {
            delay(intervalMs.toLong().coerceAtLeast(1L))
            currentFrameIndex = (currentFrameIndex + 1) % resolvedFrames.size
        }
    }

    val boxByFrameIndex = remember(spriteSheetConfig) {
        spriteSheetConfig.boxes.associateBy { box ->
            spriteSheetConfig.toInternalFrameIndex(box.frameIndex) ?: box.frameIndex
        }
    }
    val currentFrame = resolvedFrames.getOrNull(currentFrameIndex)?.coerceIn(0, spriteSheetConfig.frameCount - 1)
    val currentBox = currentFrame?.let { frameIndex -> boxByFrameIndex[frameIndex] }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("プレビュー", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .size(96.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (currentBox == null) return@Canvas
                val region = SpriteFrameRegion(
                    srcOffset = IntOffset(currentBox.x, currentBox.y),
                    srcSize = IntSize(currentBox.width, currentBox.height),
                )
                val (dstSizeInt, dstOffsetInt) = ContentScale.Fit.toDstRect(
                    srcSize = region.srcSize,
                    canvasSize = this.size
                )
                drawFrameRegion(
                    sheet = imageBitmap,
                    region = region,
                    dstSize = dstSizeInt,
                    dstOffset = dstOffsetInt,
                    placeholder = { offset, dstSize -> drawFramePlaceholder(offset, dstSize) },
                )
            }
        }
        Text("フレーム: ${(currentFrame ?: 0) + 1} / ${spriteSheetConfig.frameCount}")
        Text("周期: ${intervalMs}ms")
        val appliedFramesUi = resolvedFrames.map { it + 1 }
        Text(
            "Applied: frames=${appliedFramesUi.joinToString(",")} interval=${intervalMs}ms",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun SpriteSettingsControls(
    selectedNumber: Int,
    selectedPosition: BoxPosition?,
    boxSizePx: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onMoveXNegative: () -> Unit,
    onMoveXPositive: () -> Unit,
    onMoveYNegative: () -> Unit,
    onMoveYPositive: () -> Unit,
    onSizeDecrease: () -> Unit,
    onSizeIncrease: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrev) {
                    Text(text = "前へ")
                }
                Button(onClick = onNext) {
                    Text(text = "次へ")
                }
                Text(text = "選択中: $selectedNumber/9")
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onMoveXNegative) { Text("X-") }
                    IconButton(onClick = onMoveXPositive) { Text("X+") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onMoveYNegative) { Text("Y-") }
                    IconButton(onClick = onMoveYPositive) { Text("Y+") }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "サイズ: ${boxSizePx}px")
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onSizeDecrease) { Text("-") }
                IconButton(onClick = onSizeIncrease) { Text("+") }
            }
        }

        Text(
            text = selectedPosition?.let { position ->
                "座標: ${position.x},${position.y},${boxSizePx},${boxSizePx}"
            } ?: "座標: -, -, -, -"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = onSave
            ) {
                Text(text = "保存")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = onCopy
            ) {
                Text(text = "コピー")
            }
        }
    }
}
