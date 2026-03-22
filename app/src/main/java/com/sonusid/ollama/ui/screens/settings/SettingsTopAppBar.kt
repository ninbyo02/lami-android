package com.sonusid.ollama.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sonusid.ollama.R

val SimpleScreenTopBarVisualNudgeDp = (-2).dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopAppBar(
    titleResId: Int,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 上: TopBar 背景はステータスバー背後までつなげて描画する
            .background(MaterialTheme.colorScheme.surface)
            .zIndex(1f)
    ) {
        TopAppBar(
            // Settings 画面と同様に TopAppBar 側の Insets は 0 に統一する
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = {
                SimpleScreenTopBarContentWrapper(
                    modifier = Modifier
                        .width(56.dp)
                        // 上: 戻る操作だけに安全域を適用し、背景コンテナは押し下げない
                        .offset(y = SimpleScreenTopBarVisualNudgeDp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.back),
                            contentDescription = null,
                        )
                    }
                }
            },
            title = {
                SimpleScreenTopBarContentWrapper(
                    modifier = Modifier
                        // 上: title だけに安全域を適用し、見た目の基準を維持する
                        .offset(y = SimpleScreenTopBarVisualNudgeDp)
                ) {
                    Text(stringResource(titleResId))
                }
            },
            modifier = Modifier
                // [dp] 縦: TopAppBar 本体の描画領域は従来どおり 48.dp に保つ
                .fillMaxWidth()
                .height(48.dp),
        )
    }
}

@Composable
fun SimpleScreenTopBarContentWrapper(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            // 上: 操作要素だけにステータスバー安全域を適用する
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .wrapContentHeight(Alignment.CenterVertically),
        contentAlignment = contentAlignment,
    ) {
        content()
    }
}
