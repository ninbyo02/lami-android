package io.github.ninbyo02.lami.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import io.github.ninbyo02.lami.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopAppBar(
    titleResId: Int,
    onBack: () -> Unit,
) {
    SettingsTopAppBar(
        title = stringResource(titleResId),
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopAppBar(
    title: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1f)
    ) {
        TopAppBar(
            // 上: Settings 系では status bar 吸収を行わず、Scaffold の innerPadding に責務を統一する
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
                        .fillMaxHeight()
                        .wrapContentHeight(Alignment.CenterVertically)
                ) {
                    Text(title)
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
            .wrapContentHeight(Alignment.CenterVertically),
        contentAlignment = contentAlignment,
    ) {
        content()
    }
}
