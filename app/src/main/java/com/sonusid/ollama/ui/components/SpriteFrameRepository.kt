package com.sonusid.ollama.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sonusid.ollama.data.BoxPosition
import com.sonusid.ollama.data.SpriteSheetConfig
import com.sonusid.ollama.ui.screens.debug.SpriteBox
import com.sonusid.ollama.ui.screens.debug.SpriteDebugDataStore
import com.sonusid.ollama.ui.screens.debug.SpriteDebugPreferences
import com.sonusid.ollama.ui.screens.debug.SpriteDebugState
import com.sonusid.ollama.ui.screens.settings.SettingsPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlin.math.roundToInt

class SpriteFrameRepository(
    private val spriteDebugDataStore: SpriteDebugDataStore,
    private val settingsPreferences: SettingsPreferences,
) {
    private val defaultConfig = SpriteSheetConfig.default3x3()
    private val defaultBoxes = defaultConfig.toSpriteBoxes()

    val frameMaps: Flow<LamiSpriteFrameMaps> = combine(
        spriteDebugDataStore.observeState(),
        settingsPreferences.spriteSheetConfig,
    ) { state, persistedConfig ->
        val config = state?.spriteSheetConfig?.normalize(defaultConfig) ?: persistedConfig.normalize(defaultConfig)
        val boxes = state?.boxes?.takeIf { it.isNotEmpty() } ?: config.toSpriteBoxes()
        boxes.toFrameMaps(config)
    }
        .onStart { emit(defaultBoxes.toFrameMaps(defaultConfig)) }
        .catch { emit(defaultBoxes.toFrameMaps(defaultConfig)) }

    suspend fun latestFrameMaps(): LamiSpriteFrameMaps = frameMaps.first()
}

@Composable
fun rememberSpriteFrameRepository(): SpriteFrameRepository {
    val context = LocalContext.current
    return remember {
        SpriteFrameRepository(
            spriteDebugDataStore = SpriteDebugPreferences(context.applicationContext),
            settingsPreferences = SettingsPreferences(context.applicationContext),
        )
    }
}

@Composable
fun rememberSpriteFrameMaps(repository: SpriteFrameRepository = rememberSpriteFrameRepository()): LamiSpriteFrameMaps {
    val defaultConfig = remember { SpriteSheetConfig.default3x3() }
    val defaultMaps = remember { defaultConfig.toSpriteBoxes().toFrameMaps(defaultConfig) }
    val frameMaps by repository.frameMaps.collectAsState(initial = defaultMaps)
    return frameMaps
}

fun List<SpriteBox>.toFrameMaps(config: SpriteSheetConfig): LamiSpriteFrameMaps {
    val frameWidth = config.frameWidth.coerceAtLeast(1)
    val frameHeight = config.frameHeight.coerceAtLeast(1)
    val columns = config.cols.coerceAtLeast(1)
    val offsetMap = mutableMapOf<Int, IntOffset>()
    val sizeMap = mutableMapOf<Int, IntSize>()
    forEach { box ->
        offsetMap[box.index] = IntOffset(x = box.x.roundToInt().coerceAtLeast(0), y = box.y.roundToInt().coerceAtLeast(0))
        sizeMap[box.index] = IntSize(width = box.width.roundToInt().coerceAtLeast(1), height = box.height.roundToInt().coerceAtLeast(1))
    }
    return LamiSpriteFrameMaps(
        offsetMap = offsetMap,
        sizeMap = sizeMap,
        frameSize = IntSize(width = frameWidth, height = frameHeight),
        columns = columns,
    )
}

fun SpriteSheetConfig.toSpriteBoxes(): List<SpriteBox> = boxes.map { position ->
    SpriteBox(
        index = position.frameIndex,
        x = position.x.toFloat(),
        y = position.y.toFloat(),
        width = position.width.toFloat(),
        height = position.height.toFloat(),
    )
}

fun SpriteSheetConfig.normalize(defaultConfig: SpriteSheetConfig = SpriteSheetConfig.default3x3()): SpriteSheetConfig {
    val validationError = validate()
    if (validationError != null) return defaultConfig
    return this
}

fun SpriteDebugState.toBoxPositions(): List<BoxPosition> =
    boxes.map { box ->
        BoxPosition(
            frameIndex = box.index,
            x = box.x.roundToInt(),
            y = box.y.roundToInt(),
            width = box.width.roundToInt(),
            height = box.height.roundToInt(),
        )
    }
