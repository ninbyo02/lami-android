package io.github.ninbyo02.lami.ui.screens.settings

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.R
import io.github.ninbyo02.lami.navigation.Routes
import io.github.ninbyo02.lami.ui.common.LocalAppSnackbarHostState
import io.github.ninbyo02.lami.ui.common.PROJECT_SNACKBAR_SHORT_MS
import io.github.ninbyo02.lami.ui.theme.LamiTypographyTokens
import io.github.ninbyo02.lami.ui.components.LamiSprite
import io.github.ninbyo02.lami.ui.components.rememberLamiCharacterBackdropColor
import io.github.ninbyo02.lami.viewmodels.LamiState
import io.github.ninbyo02.lami.viewmodels.LamiStatus
import io.github.ninbyo02.lami.viewmodels.LamiUiState
import io.github.ninbyo02.lami.viewmodels.OllamaViewModel
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlinx.coroutines.launch

private const val DeveloperAccessTapTarget = 7


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
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = LocalAppSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val settingsPreferences = remember(context) { SettingsPreferences(context) }
    var developerAccessTapCount by remember { mutableStateOf(0) }

    val licenseLine1 = stringResource(R.string.about_license_line1)
    val licenseLine2 = stringResource(R.string.about_license_line2)
    val licenseLine3 = stringResource(R.string.about_license_line3)
    val noticeText = stringResource(R.string.notice)
    val copiedText = stringResource(R.string.about_notice_copy_done)
    val fullLicenseText = listOf(licenseLine1, licenseLine2, licenseLine3).joinToString("\n")
    val readableBodyTextStyle = LamiTypographyTokens.bodyReadable()
    fun handleDeveloperAccessTap() {
        if (!BuildConfig.DEBUG) return
        val nextCount = developerAccessTapCount + 1
        developerAccessTapCount = nextCount
        if (nextCount >= DeveloperAccessTapTarget) {
            developerAccessTapCount = 0
            scope.launch {
                settingsPreferences.saveDeveloperAccessEnabled(true)
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = "Developer access enabled",
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }
    val developerAccessTapModifier =
        if (BuildConfig.DEBUG) {
            Modifier.clickable { handleDeveloperAccessTap() }
        } else {
            Modifier
        }

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
        // Settings 系では Scaffold 自体は Insets を受けず、topBar/content の座標だけを返す
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SettingsTopAppBar(
                titleResId = R.string.about,
                onBack = { navController.popBackStack() },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 上下左右: Scaffold と TopBar が決めた描画領域に本文を揃える
                .padding(paddingValues)
                // Scaffold の Insets はこの階層で消費し、内部スクロールへ重ねない
                .consumeWindowInsets(paddingValues),
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
                            .fillMaxWidth(0.6f)
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
                            style = readableBodyTextStyle,
                        )
                        Spacer(Modifier.height(10.dp))
                        val versionLabel = buildVersionLabel(BuildConfig.VERSION_NAME, BuildConfig.GIT_SHA)
                        Text(
                            versionLabel,
                            style = LamiTypographyTokens.aboutVersion(),
                            modifier = developerAccessTapModifier,
                        )
                        Text(
                            buildPrLabel(BuildConfig.BUILD_PR_NUMBER),
                            style = LamiTypographyTokens.aboutBuild(),
                            modifier = developerAccessTapModifier,
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
                                    style = readableBodyTextStyle,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = licenseLine2,
                                    style = readableBodyTextStyle,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = noticeAnnotatedText,
                                    style = readableBodyTextStyle,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxWidth(0.4f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        BoxWithConstraints {
                            val baseSpriteSize = 100.dp
                            val targetSize = baseSpriteSize * 2f
                            val maxSizeByHeight = maxHeight * 0.85f
                            val maxSizeByWidth = maxWidth * 0.90f
                            val finalSize = minOf(targetSize, maxSizeByWidth, maxSizeByHeight)
                            val upwardOffset = -(minOf(maxHeight * 0.03f, 32.dp))

                            LamiSprite(
                                state = lamiState,
                                lamiStatus = lamiStatus,
                                sizeDp = finalSize,
                                modifier = Modifier.offset(y = upwardOffset),
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
                        style = readableBodyTextStyle,
                    )
                    // 下：バージョン表示との距離を確保するための Spacer
                    Spacer(Modifier.height(10.dp))
                    val versionLabel = buildVersionLabel(BuildConfig.VERSION_NAME, BuildConfig.GIT_SHA)
                    Text(
                        versionLabel,
                        style = LamiTypographyTokens.aboutVersion(),
                        modifier = developerAccessTapModifier,
                    )
                    Text(
                        buildPrLabel(BuildConfig.BUILD_PR_NUMBER),
                        style = LamiTypographyTokens.aboutBuild(),
                        modifier = developerAccessTapModifier,
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
                                style = readableBodyTextStyle,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = licenseLine2,
                                style = readableBodyTextStyle,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = noticeAnnotatedText,
                                style = readableBodyTextStyle,
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
