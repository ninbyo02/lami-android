package com.sonusid.ollama.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

val SimpleScreenTopBarVisualNudgeDp = 0.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopAppBar(
    titleResId: Int,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            // DEBUG top-gap probe start
            .topGapProbeOverlay(TopGapProbeTopBarColor)
            .topGapProbeBounds("SettingsTopAppBar.container(statusBarsPadding)")
            // DEBUG top-gap probe end
            // 上: 単純画面の TopBar は親 Box 側でだけステータスバーを回避する
            .statusBarsPadding()
            .fillMaxWidth()
            .zIndex(1f)
    ) {
        TopGapProbeLog("SettingsTopAppBar.windowInsets=WindowInsets(0,0,0,0)")
        TopAppBar(
            // 上端余白の重複を防ぐため、TopAppBar 側の Insets は明示的に 0 にする
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .fillMaxHeight()
                        .wrapContentHeight(Alignment.CenterVertically),
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
                Box(
                    modifier = Modifier
                        // DEBUG top-gap probe start
                        .topGapProbeOverlay(TopGapProbeActualContentColor)
                        .topGapProbeBounds("SettingsTopAppBar.titleParent")
                        // DEBUG top-gap probe end
                        .fillMaxHeight()
                        .wrapContentHeight(Alignment.CenterVertically)
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
            // DEBUG top-gap probe start
            .topGapProbeOverlay(TopGapProbeActualContentColor)
            .topGapProbeBounds("SimpleScreenTopBarContentWrapper.windowInsetsPadding")
            // DEBUG top-gap probe end
            .fillMaxHeight()
            // 上: Sprite Editor 側の安全域適用は現状維持しつつ、共通 TopBar の復旧を優先する
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .wrapContentHeight(Alignment.CenterVertically),
        contentAlignment = contentAlignment,
    ) {
        content()
    }
}
