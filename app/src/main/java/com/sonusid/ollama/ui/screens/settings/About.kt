package com.sonusid.ollama.ui.screens.settings

import android.content.res.Configuration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.sonusid.ollama.BuildConfig
import com.sonusid.ollama.R
import com.sonusid.ollama.navigation.Routes
import com.sonusid.ollama.ui.common.LocalAppSnackbarHostState
import com.sonusid.ollama.ui.common.PROJECT_SNACKBAR_SHORT_MS
import com.sonusid.ollama.ui.components.LamiSprite
import com.sonusid.ollama.ui.components.rememberLamiCharacterBackdropColor
import com.sonusid.ollama.viewmodels.LamiState
import com.sonusid.ollama.viewmodels.LamiStatus
import com.sonusid.ollama.viewmodels.LamiUiState
import com.sonusid.ollama.viewmodels.OllamaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun buildVersionLabel(version: String, sha: String): String {
    val shaShort = sha.trim().takeIf { it.isNotBlank() }?.take(7)
    return if (shaShort != null) "v$version ($shaShort)" else "v$version"
}

internal fun buildPrLabel(buildPrNumber: String): String {
    val normalized = buildPrNumber.trim().takeIf { it.matches(Regex("^\\d+$")) }
    val buildNumber = normalized ?: BuildConfig.VERSION_CODE.toString()
    return "Build: $buildNumber"
}

@Composable
fun About(
    navController: NavController,
    viewModel: OllamaViewModel? = null,
) {
    val lamiStatus =
        viewModel?.lamiAnimationStatus?.collectAsState(initial = LamiStatus.READY)?.value
            ?: LamiStatus.READY
    val lamiState =
        viewModel?.lamiUiState?.collectAsState(initial = LamiUiState())?.value?.state
            ?: LamiState.Idle
    val animationEpochMs =
        viewModel?.animationEpochMs?.collectAsState(initial = 0L)?.value ?: 0L
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemBarInsets = WindowInsets.systemBars
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = LocalAppSnackbarHostState.current
    val scope = rememberCoroutineScope()

    // 左右の安全領域は維持し、上は TopAppBar 側で処理する
    val scaffoldInsets = WindowInsets(
        left = systemBarInsets.getLeft(density, layoutDirection),
        top = 0,
        right = systemBarInsets.getRight(density, layoutDirection),
        bottom = 0,
    )

    val licenseLine1 = stringResource(R.string.about_license_line1)
    val licenseLine2 = stringResource(R.string.about_license_line2)
    val licenseLine3 = stringResource(R.string.about_license_line3)
    val noticeText = stringResource(R.string.notice)
    val copiedText = stringResource(R.string.about_notice_copy_done)
    val fullLicenseText = listOf(licenseLine1, licenseLine2, licenseLine3).joinToString("\n")

    val noticeAnnotatedText = buildAnnotatedString {
        val noticeStart = licenseLine3.indexOf(noticeText)
        append(licenseLine3)
        if (noticeStart >= 0) {
            val noticeEnd = noticeStart + noticeText.length
            addStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                ),
                start = noticeStart,
                end = noticeEnd,
            )
            addLink(
                LinkAnnotation.Clickable(
                    tag = "notice",
                    linkInteractionListener = {
                        navController.navigate(Routes.NOTICE)
                    },
                ),
                start = noticeStart,
                end = noticeEnd,
            )
        }
    }

    Scaffold(
        // 左右の安全領域は維持し、上は TopAppBar 側で処理する
        contentWindowInsets = scaffoldInsets,
        topBar = {
            SettingsTopAppBar(
                titleResId = R.string.about,
                onBack = { navController.popBackStack() },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                // 上：Scaffold の余白をそのまま適用する
                .padding(paddingValues)
                .fillMaxSize(),
        ) {
            if (isLandscape) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(0.4f)
                            .fillMaxHeight()
                            .widthIn(max = 560.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.app_name),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            BuildConfig.APP_SUBTITLE,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        val versionLabel = buildVersionLabel(BuildConfig.VERSION_NAME, BuildConfig.GIT_SHA)
                        Text(
                            versionLabel,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            buildPrLabel(BuildConfig.BUILD_PR_NUMBER),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(24.dp))
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(fullLicenseText) {
                                    detectTapGestures(
                                        onLongPress = {
                                            clipboardManager.setText(AnnotatedString(fullLicenseText))
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
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.about_license_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = licenseLine1,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = licenseLine2,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = noticeAnnotatedText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        BoxWithConstraints {
                            val baseSpriteSize = 100.dp
                            val targetSize = baseSpriteSize * 2f
                            val maxSizeByHeight = maxHeight * 0.85f
                            val maxSizeByWidth = maxWidth * 0.55f
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
                    }
                }
            } else {
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
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        BuildConfig.APP_SUBTITLE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 下：バージョン表示との距離を確保するための Spacer
                    Spacer(Modifier.height(10.dp))
                    val versionLabel = buildVersionLabel(BuildConfig.VERSION_NAME, BuildConfig.GIT_SHA)
                    Text(
                        versionLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        buildPrLabel(BuildConfig.BUILD_PR_NUMBER),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(24.dp))
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .pointerInput(fullLicenseText) {
                                detectTapGestures(
                                    onLongPress = {
                                        clipboardManager.setText(AnnotatedString(fullLicenseText))
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
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.about_license_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = licenseLine1,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = licenseLine2,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = noticeAnnotatedText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
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
