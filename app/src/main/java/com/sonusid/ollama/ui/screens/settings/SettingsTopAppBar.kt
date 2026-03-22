package com.sonusid.ollama.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import com.sonusid.ollama.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopAppBar(
    titleResId: Int,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            // [dp] 縦: Settings 画面と同じ AppBar 高さに揃える
            .height(48.dp)
            .fillMaxWidth()
            .zIndex(1f)
    ) {
        TopAppBar(
            // Settings 画面と同様に TopAppBar 側の Insets は 0 に統一する
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
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
                    Text(stringResource(titleResId))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

