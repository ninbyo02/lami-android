package com.sonusid.ollama.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonusid.ollama.R
import com.sonusid.ollama.api.RetrofitClient
import com.sonusid.ollama.viewmodels.LamiStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LamiAvatar(
    baseUrl: String,
    selectedModel: String?,
    lastError: String?,
    lamiStatus: LamiStatus = LamiStatus.CONNECTING,
    modifier: Modifier = Modifier,
    onNavigateSettings: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var animationsEnabled by rememberSaveable { mutableStateOf(true) }
    var replacementEnabled by rememberSaveable { mutableStateOf(true) }
    var blinkEffectEnabled by rememberSaveable { mutableStateOf(false) }
    var showStatusDetails by rememberSaveable { mutableStateOf(true) }
    var avatarSize by rememberSaveable { mutableStateOf(36) }
    var lastUpdated by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val initializationState = RetrofitClient.getLastInitializationState()
    val fallbackActive = initializationState?.usedFallback == true
    val fallbackMessage = initializationState?.errorMessage
    val statusLabel = remember(lamiStatus) {
        when (lamiStatus) {
            LamiStatus.CONNECTING -> "接続中"
            LamiStatus.READY -> "接続良好"
            LamiStatus.DEGRADED -> "フォールバック中"
            LamiStatus.NO_MODELS -> "モデルなし"
            LamiStatus.OFFLINE -> "オフライン"
            LamiStatus.ERROR -> "エラー"
            LamiStatus.TALKING -> "話し中"
        }
    }

    LaunchedEffect(baseUrl, selectedModel, lastError, fallbackActive) {
        lastUpdated = formatter.format(Date())
    }

    Box(
        modifier = modifier
            .size(avatarSize.dp)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    showMenu = true
                },
                onLongClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    showSheet = true
                }
            )
    ) {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = "Lami avatar",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent { drawContent() }
        )

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("""状態: $statusLabel""") },
                onClick = { }
            )
            DropdownMenuItem(
                text = { Text("""接続先: ${baseUrl.ifBlank { "未設定" }}""") },
                onClick = { }
            )
            DropdownMenuItem(
                text = { Text("""モデル: ${selectedModel ?: "未選択"}""") },
                onClick = { }
            )
            DropdownMenuItem(
                text = { Text("""フォールバック: ${if (fallbackActive) "ON" else "OFF"}""") },
                onClick = { }
            )
            if (fallbackActive && !fallbackMessage.isNullOrBlank()) {
                DropdownMenuItem(
                    text = { Text("""理由: $fallbackMessage""") },
                    onClick = { }
                )
            }
            DropdownMenuItem(text = { Text("最終更新: $lastUpdated") }, onClick = { })
            if (showStatusDetails) {
                DropdownMenuItem(
                    text = { Text("""エラー概要: ${lastError ?: "なし"}""") },
                    onClick = { }
                )
            }
        }

        if (showSheet) {
            ModalBottomSheet(
                sheetState = sheetState,
                onDismissRequest = { showSheet = false }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Lami コントロール", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        IconButton(onClick = {
                            onNavigateSettings?.invoke()
                            showSheet = false
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.settings),
                                contentDescription = "設定を開く"
                            )
                        }
                    }
                    ToggleRow(
                        label = "アニメーション",
                        checked = animationsEnabled,
                        onCheckedChange = { animationsEnabled = it }
                    )
                    ToggleRow(
                        label = "置換",
                        checked = replacementEnabled,
                        onCheckedChange = { replacementEnabled = it }
                    )
                    ToggleRow(
                        label = "点滅エフェクト",
                        checked = blinkEffectEnabled,
                        onCheckedChange = { blinkEffectEnabled = it }
                    )
                    ToggleRow(
                        label = "ステータス詳細表示",
                        checked = showStatusDetails,
                        onCheckedChange = { showStatusDetails = it }
                    )
                    Column {
                        Text("表示サイズ (${avatarSize}dp)", fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = avatarSize.toFloat(),
                            onValueChange = { value ->
                                val snapped = (value.roundToInt() / 2) * 2
                                avatarSize = snapped.coerceIn(32, 40)
                            },
                            valueRange = 32f..40f,
                            steps = 3
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onNavigateSettings?.invoke()
                            showSheet = false
                        }
                    ) {
                        Text("設定画面へ移動")
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
