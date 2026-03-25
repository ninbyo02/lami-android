package com.sonusid.ollama.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.sonusid.ollama.R

@Composable
fun LocalBaseModelScreen(navController: NavController) {
    Scaffold(
        // Settings 系では Scaffold 自体は Insets を受けず、topBar/content の座標だけを返す
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SettingsTopAppBar(
                titleResId = R.string.local_base_model_title,
                onBack = { navController.popBackStack() },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 上下左右: Scaffold と TopBar が決めた描画領域に本文を揃える
                .padding(innerPadding)
                // Scaffold の Insets はこの階層で消費し、本文側へ二重適用しない
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.local_base_model_placeholder),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
