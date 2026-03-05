package com.sonusid.ollama.ui.screens.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.sonusid.ollama.R
import com.sonusid.ollama.ui.common.BottomFadeOverlay
import com.sonusid.ollama.ui.common.LocalAppSnackbarHostState
import com.sonusid.ollama.ui.common.PROJECT_SNACKBAR_SHORT_MS
import com.sonusid.ollama.ui.common.TopFadeOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NoticeTopFadeThreshold = 0.dp
private val ReadableBodyLineHeightSp = 22.sp

@Composable
private fun rememberReadableBodyTextStyle(): TextStyle {
    return MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Medium,
        lineHeight = ReadableBodyLineHeightSp,
    )
}

@Composable
fun NoticeScreen(navController: NavController) {
    val context = LocalContext.current
    var noticeText by remember { mutableStateOf("") }
    val density = LocalDensity.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = LocalAppSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.about_notice_copy_done)
    val readableBodyTextStyle = rememberReadableBodyTextStyle()
    val scrollState = rememberScrollState()
    val thresholdPx = with(density) { NoticeTopFadeThreshold.roundToPx() }
    val showTopFade by remember {
        derivedStateOf { scrollState.value > thresholdPx }
    }
    val showBottomFade by remember {
        derivedStateOf {
            scrollState.maxValue > thresholdPx &&
                scrollState.value < (scrollState.maxValue - thresholdPx)
        }
    }

    // 上端の安全領域は TopAppBar 側で処理し、Scaffold は左右のみ適用する
    val scaffoldInsets = WindowInsets.systemBars.only(
        WindowInsetsSides.Horizontal,
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
                    // 左右：長文可読性を保つ最小限の余白（先頭余白は詰める）
                    .padding(start = 16.dp, end = 16.dp)
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
                Text(
                    text = noticeText,
                    style = readableBodyTextStyle,
                    modifier = Modifier
                        // 上：NOTICE見出し相当の見え位置を揃えるため最小限の余白
                        .padding(top = 24.dp),
                )
            }
            TopFadeOverlay(
                show = showTopFade,
                bg = scaffoldBg,
                modifier = Modifier.align(Alignment.TopCenter),
                label = "noticeTopFade",
            )
            BottomFadeOverlay(
                show = showBottomFade,
                bg = scaffoldBg,
                modifier = Modifier.align(Alignment.BottomCenter),
                label = "noticeBottomFade",
            )
        }
    }
}
