package com.sonusid.ollama.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.sonusid.ollama.BuildConfig
import com.sonusid.ollama.R
import com.sonusid.ollama.api.RetrofitClient
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.ui.components.LamiHeaderStatus
import com.sonusid.ollama.ui.components.LamiSprite
import com.sonusid.ollama.ui.components.rememberLamiCharacterBackdropColor
import com.sonusid.ollama.viewmodels.LamiUiState
import com.sonusid.ollama.viewmodels.LamiStatus
import com.sonusid.ollama.viewmodels.LamiState
import com.sonusid.ollama.viewmodels.OllamaViewModel

internal fun buildVersionLabel(version: String, sha: String): String {
    val shaShort = sha.trim().takeIf { it.isNotBlank() }?.take(7)
    return if (shaShort != null) "v$version ($shaShort)" else "v$version"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun About(
    navController: NavController,
    viewModel: OllamaViewModel? = null,
) {
    val baseUrl = remember { RetrofitClient.currentBaseUrl().trimEnd('/') }
    val lamiStatus =
        viewModel?.lamiAnimationStatus?.collectAsState(initial = LamiStatus.READY)?.value
            ?: LamiStatus.READY
    val lamiState =
        viewModel?.lamiUiState?.collectAsState(initial = LamiUiState())?.value?.state
            ?: LamiState.Idle
    val animationEpochMs =
        viewModel?.animationEpochMs?.collectAsState(initial = 0L)?.value ?: 0L
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(painterResource(R.drawable.back), "exit")
                    }
                },
                title = {
                    LamiHeaderStatus(
                        baseUrl = baseUrl,
                        selectedModel = null,
                        lastError = null,
                        lamiStatus = lamiStatus,
                        lamiState = lamiState,
                        availableModels = emptyList(),
                        onSelectModel = {},
                        onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                        debugOverlayEnabled = false,
                        syncEpochMs = animationEpochMs,
                    )
                }
            )
        }) { paddingValues ->
        Box(
            modifier = Modifier
                // 上：Scaffold の余白をそのまま適用する
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    // 上：画面中央基準で位置を安定させる
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BoxWithConstraints {
                    // 中央キャラ：現状の上限(100.dp)を基準に約2倍を目標にする
                    val baseSpriteSize = 100.dp
                    val targetSize = baseSpriteSize * 2f
                    val maxSizeByWidth = maxWidth * 0.92f
                    val maxSizeByHeight = maxHeight * 0.45f
                    val finalSize = minOf(targetSize, maxSizeByWidth, maxSizeByHeight)
                    LamiSprite(
                        state = lamiState,
                        lamiStatus = lamiStatus,
                        sizeDp = finalSize,
                        modifier = Modifier,
                        shape = CircleShape,
                        backgroundColor = rememberLamiCharacterBackdropColor(),
                        // 中央キャラ：背景円の余白をなくす
                        contentPadding = 0.dp,
                        animationsEnabled = true,
                        replacementEnabled = true,
                        blinkEffectEnabled = true,
                        contentOffsetYDp = 2.dp,
                        tightContainer = true,
                        maxStatusSpriteSizeDp = finalSize,
                        debugOverlayEnabled = false,
                        syncEpochMs = animationEpochMs,
                    )
                }
                // 下：タイトルとの距離を確保するための Spacer
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.app_name),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                // 下：バージョン表示との距離を確保するための Spacer
                Spacer(Modifier.height(10.dp))
                val versionLabel = buildVersionLabel(BuildConfig.VERSION_NAME, BuildConfig.GIT_SHA)
                Text(
                    versionLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                // 下：セクション終端の余白を確保するための Spacer
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AboutPreview() {
    val dummyNav = rememberNavController()
    MaterialTheme(colorScheme = lightColorScheme()) { About(dummyNav) }
}
