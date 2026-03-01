package com.sonusid.ollama.ui.screens.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sonusid.ollama.R
import com.sonusid.ollama.ui.common.LocalAppSnackbarHostState
import com.sonusid.ollama.ui.common.PROJECT_SNACKBAR_SHORT_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NoticeTopFadeHeight = 64.dp
private val NoticeTopFadeThreshold = 0.dp

@Composable
fun NoticeScreen(navController: NavController) {
    val context = LocalContext.current
    var noticeText by remember { mutableStateOf("") }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemBarInsets = WindowInsets.systemBars
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = LocalAppSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.about_notice_copy_done)
    val scrollState = rememberScrollState()
    val thresholdPx = with(density) { NoticeTopFadeThreshold.roundToPx() }
    val showTopFade by remember {
        derivedStateOf { scrollState.value > thresholdPx }
    }

    // 左右の安全領域は維持し、上は TopAppBar 側で処理する
    val scaffoldInsets = WindowInsets(
        left = systemBarInsets.getLeft(density, layoutDirection),
        top = 0,
        right = systemBarInsets.getRight(density, layoutDirection),
        bottom = 0,
    )

    LaunchedEffect(Unit) {
        noticeText = withContext(Dispatchers.IO) {
            context.resources.openRawResource(R.raw.notice).bufferedReader().use { it.readText() }
        }
    }

    val scaffoldBg = MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = scaffoldBg,
        contentWindowInsets = scaffoldInsets,
        topBar = {
            SettingsTopAppBar(
                titleResId = R.string.notice_title,
                onBack = { navController.popBackStack() },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 上：ScaffoldのinnerPaddingをそのまま適用
                .padding(innerPadding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 四辺：長文可読性を保つ最小限の余白
                    .padding(16.dp)
                    .verticalScroll(scrollState)
                    .pointerInput(noticeText) {
                        detectTapGestures(
                            onLongPress = {
                                clipboardManager.setText(AnnotatedString(noticeText))
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val dismissJob = launch {
                                        delay(PROJECT_SNACKBAR_SHORT_MS)
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                    }
                                    try {
                                        snackbarHostState.showSnackbar(
                                            message = copiedText,
                                            duration = SnackbarDuration.Indefinite,
                                        )
                                    } finally {
                                        dismissJob.cancel()
                                    }
                                }
                            },
                        )
                    },
            ) {
                Text(text = noticeText)
            }
            TopFadeOverlay(
                show = showTopFade,
                bg = scaffoldBg,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun TopFadeOverlay(
    show: Boolean,
    bg: Color,
    modifier: Modifier = Modifier,
    height: Dp = NoticeTopFadeHeight,
) {
    val alpha by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
        label = "noticeTopFade",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { this.alpha = alpha }
            .clipToBounds()
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to bg.copy(alpha = 1.0f),
                            0.5f to bg.copy(alpha = 0.6f),
                            1.0f to bg.copy(alpha = 0.0f),
                        ),
                    ),
                    size = size,
                )
            },
    )
}
