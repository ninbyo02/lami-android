package io.github.ninbyo02.lami.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun ReadyAnimationResponsiveLayout(
    isLandscapeOrWide: Boolean,
    screenWidthDp: Int,
    screenHeightDp: Int,
    previewContent: @Composable (Modifier) -> Unit,
    formContent: @Composable (Modifier) -> Unit,
) {
    if (isLandscapeOrWide) {
        BoxWithConstraints(
            modifier = Modifier
                // [非dp] 縦横: 画面全体 の fillMaxSize(制約)に関係
                .fillMaxSize()
        ) {
            val portraitLikeWidthDp = minOf(screenWidthDp, screenHeightDp).dp
            val previewWidth = minOf((maxWidth - 6.dp) / 2f, portraitLikeWidthDp)
            Row(
                modifier = Modifier
                    // [非dp] 縦横: 画面全体 の fillMaxSize(制約)に関係
                    .fillMaxSize(),
                // [dp] 横: 2カラム の間隔(間隔)に関係
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        // [非dp] 横: 左カラム の width(制約)に関係
                        .width(previewWidth)
                        // [非dp] 縦: 左カラム の fillMaxHeight(制約)に関係
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            // [非dp] 横: 左カラム内 の fillMaxWidth(制約)に関係
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        previewContent(Modifier.width(previewWidth))
                    }
                }
                formContent(
                    Modifier
                        // [非dp] 横: 右カラム の weight(制約)に関係
                        .weight(1f)
                        // [非dp] 縦: 右カラム の fillMaxHeight(制約)に関係
                        .fillMaxHeight()
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                // [非dp] 縦横: 画面全体 の fillMaxSize(制約)に関係
                .fillMaxSize()
        ) {
            previewContent(Modifier.fillMaxWidth())
            formContent(Modifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnimationDropdown(
    items: List<AnimationType>,
    selectedItem: AnimationType,
    onSelectedItemChange: (AnimationType) -> Unit,
    modifier: Modifier = Modifier,
    anchorTestTag: String? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        TextField(
            value = selectedItem.displayLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("アニメ種別") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .then(
                    if (anchorTestTag != null) {
                        // テストでドロップダウンを開くための最小限の testTag
                        Modifier.testTag(anchorTestTag)
                    } else {
                        Modifier
                    }
                )
                // [非dp] 横: 入力欄 の fillMaxWidth(制約)に関係
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.displayLabel) },
                    onClick = {
                        onSelectedItemChange(item)
                        expanded = false
                    }
                )
            }
        }
    }
}
