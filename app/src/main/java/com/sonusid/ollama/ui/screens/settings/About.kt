package com.sonusid.ollama.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.sonusid.ollama.R
import com.sonusid.ollama.api.RetrofitClient
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.ui.components.LamiAvatar
import com.sonusid.ollama.ui.components.LamiSprite
import com.sonusid.ollama.viewmodels.LamiStatus
import com.sonusid.ollama.viewmodels.LamiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun About(navController: NavController) {
    val baseUrl = remember { RetrofitClient.currentBaseUrl().trimEnd('/') }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LamiAvatar(
                            baseUrl = baseUrl,
                            selectedModel = null,
                            lastError = null,
                            lamiStatus = LamiStatus.READY,
                            lamiState = LamiState.Idle,
                            modifier = Modifier.offset(x = (-1).dp),
                            onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                            debugOverlayEnabled = false,
                        )
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(painterResource(R.drawable.back), "exit")
                        }
                    }
                },
                title = { Text("About") }
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
                    val targetSize = 352.dp
                    val maxSizeByWidth = maxWidth * 0.92f
                    val maxSizeByHeight = maxHeight * 0.45f
                    val finalSize = minOf(targetSize, maxSizeByWidth, maxSizeByHeight)
                    LamiSprite(
                        state = LamiState.Idle,
                        lamiStatus = LamiStatus.READY,
                        sizeDp = finalSize,
                        modifier = Modifier,
                        shape = CircleShape,
                        backgroundColor = MaterialTheme.colorScheme.surfaceBright,
                        // 中央キャラ：背景円の余白をなくす
                        contentPadding = 0.dp,
                        animationsEnabled = true,
                        replacementEnabled = true,
                        blinkEffectEnabled = true,
                        contentOffsetYDp = 2.dp,
                        tightContainer = true,
                        debugOverlayEnabled = false,
                    )
                }
                // 下：タイトルとの距離を確保するための Spacer
                Spacer(Modifier.height(20.dp))
                Text("Ollama", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                // 下：バージョン表示との距離を確保するための Spacer
                Spacer(Modifier.height(10.dp))
                Text("v1.0.0 (Beta)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
