package com.sonusid.ollama.ui.screens.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sonusid.ollama.R
import com.sonusid.ollama.ui.common.LocalAppSnackbarHostState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    Scaffold(
        contentWindowInsets = scaffoldInsets,
        topBar = {
            SettingsTopAppBar(
                titleResId = R.string.notice,
                onBack = { navController.popBackStack() },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 上：ScaffoldのinnerPaddingをそのまま適用
                .padding(innerPadding)
                // 四辺：長文可読性を保つ最小限の余白
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .pointerInput(noticeText) {
                    detectTapGestures(
                        onLongPress = {
                            clipboardManager.setText(AnnotatedString(noticeText))
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = copiedText,
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        },
                    )
                },
        ) {
            Text(text = noticeText)
        }
    }
}
