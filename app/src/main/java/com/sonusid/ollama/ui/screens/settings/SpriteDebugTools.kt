package com.sonusid.ollama.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sonusid.ollama.R
import com.sonusid.ollama.data.SpriteSheetConfig
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.ui.components.LamiSprite3x3
import com.sonusid.ollama.ui.components.LamiStatusSprite
import com.sonusid.ollama.ui.components.LamiSpriteStatus
import com.sonusid.ollama.ui.components.rememberLamiSprite3x3FrameMaps
import com.sonusid.ollama.ui.components.toFrameYOffsetPxMap
import com.sonusid.ollama.ui.screens.debug.SpriteDebugViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpriteDebugTools(navController: NavController, viewModel: SpriteDebugViewModel) {
    val statuses = remember { LamiSpriteStatus.values() }
    var selectedStatus by rememberSaveable { mutableStateOf(LamiSpriteStatus.Idle) }
    var spriteSize by rememberSaveable { mutableStateOf(96f) }
    var animationsEnabled by rememberSaveable { mutableStateOf(true) }
    var replacementEnabled by rememberSaveable { mutableStateOf(true) }
    var blinkEffectEnabled by rememberSaveable { mutableStateOf(true) }
    var autoCropTransparentArea by rememberSaveable { mutableStateOf(true) }
    var contentOffset by rememberSaveable { mutableStateOf(0f) }
    var contentOffsetY by rememberSaveable { mutableStateOf(0f) }
    var rawFrameIndex by rememberSaveable { mutableStateOf(0f) }
    val spriteDebugState by viewModel.uiState.collectAsState()

    val spriteSheetConfig = remember { SpriteSheetConfig.default3x3() }
    val frameMaps = rememberLamiSprite3x3FrameMaps()
    val frameYOffsetMap = remember(frameMaps) { frameMaps.toFrameYOffsetPxMap() }
    val maxFrameIndex = remember(frameMaps, spriteSheetConfig) {
        val measuredMax = listOf(
            frameMaps.offsetMap.keys.maxOrNull(),
            frameMaps.sizeMap.keys.maxOrNull()
        ).mapNotNull { it }
            .maxOrNull()
        val configMaxIndex = (spriteSheetConfig.rows * spriteSheetConfig.cols - 1).coerceAtLeast(0)
        measuredMax?.coerceAtLeast(configMaxIndex) ?: configMaxIndex
    }
    val frameIndex = rawFrameIndex.roundToInt().coerceIn(0, maxFrameIndex)
    val frameInfo = remember(frameMaps, frameIndex) {
        frameMaps.offsetMap[frameIndex] to frameMaps.sizeMap[frameIndex]
    }
    val frameYOffset = frameYOffsetMap[frameIndex] ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sprite Debug Tools") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(painterResource(R.drawable.back), contentDescription = "back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("選択中のステータス", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(spriteSize.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                LamiStatusSprite(
                                    status = selectedStatus,
                                    sizeDp = spriteSize.dp,
                                    contentOffsetDp = contentOffset.dp,
                                    contentOffsetYDp = contentOffsetY.dp,
                                    animationsEnabled = animationsEnabled,
                                    replacementEnabled = replacementEnabled,
                                    blinkEffectEnabled = blinkEffectEnabled,
                                    autoCropTransparentArea = autoCropTransparentArea,
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("ステータス: ${selectedStatus.name}")
                                Text("サイズ: ${spriteSize.roundToInt()}dp")
                                Text("フレームY補正: ${frameYOffset}px")
                            }
                        }
                        ControlSlider(
                            label = "サイズ (${spriteSize.roundToInt()}dp)",
                            value = spriteSize,
                            onValueChange = { spriteSize = it },
                            valueRange = 48f..144f
                        )
                        ControlSlider(
                            label = "Xオフセット (${contentOffset.roundToInt()}dp)",
                            value = contentOffset,
                            onValueChange = { contentOffset = it },
                            valueRange = -12f..12f
                        )
                        ControlSlider(
                            label = "Yオフセット (${contentOffsetY.roundToInt()}dp)",
                            value = contentOffsetY,
                            onValueChange = { contentOffsetY = it },
                            valueRange = -12f..12f
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ControlSwitch("アニメーション", animationsEnabled) { animationsEnabled = it }
                            ControlSwitch("置換", replacementEnabled) { replacementEnabled = it }
                            ControlSwitch("点滅", blinkEffectEnabled) { blinkEffectEnabled = it }
                            ControlSwitch("透過トリム", autoCropTransparentArea) { autoCropTransparentArea = it }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Sprite Debug ROI: ${spriteDebugState.boxes.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { navController.navigate(Routes.SPRITE_DEBUG_CANVAS) }) {
                                Text("キャンバスビューを開く")
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("ステータスを選択", style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(statuses) { status ->
                                FilterChip(
                                    selected = selectedStatus == status,
                                    onClick = { selectedStatus = status },
                                    label = { Text(status.name) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("フレーム確認", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("フレーム #$frameIndex")
                                Text("オフセット: ${frameInfo.first?.x ?: 0}, ${frameInfo.first?.y ?: 0}")
                                val sizeText = frameInfo.second?.let { "${it.width} x ${it.height}" } ?: "未計測"
                                Text("サイズ: $sizeText")
                                Text("Y補正: ${frameYOffset}px")
                            }
                            LamiSprite3x3(
                                frameIndex = frameIndex,
                                sizeDp = 84.dp,
                                contentOffsetDp = contentOffset.dp,
                                contentOffsetYDp = contentOffsetY.dp,
                                frameSrcOffsetMap = frameMaps.offsetMap,
                                frameSrcSizeMap = frameMaps.sizeMap,
                                frameYOffsetPxMap = frameYOffsetMap,
                                autoCropTransparentArea = autoCropTransparentArea,
                            )
                        }
                        ControlSlider(
                            label = "フレームインデックス", 
                            value = rawFrameIndex,
                            onValueChange = { rawFrameIndex = it },
                            valueRange = 0f..maxFrameIndex.toFloat()
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("ギャラリー", style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(statuses) { status ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    LamiStatusSprite(
                                        status = status,
                                        sizeDp = 56.dp,
                                        contentOffsetDp = contentOffset.dp,
                                        contentOffsetYDp = contentOffsetY.dp,
                                        animationsEnabled = animationsEnabled,
                                        replacementEnabled = replacementEnabled,
                                        blinkEffectEnabled = blinkEffectEnabled,
                                        autoCropTransparentArea = autoCropTransparentArea,
                                    )
                                    Text(status.name, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                    TextButton(onClick = { selectedStatus = status }) {
                                        Text("選択")
                                    }
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
private fun ControlSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@Composable
private fun ControlSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}
