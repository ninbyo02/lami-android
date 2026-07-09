package io.github.ninbyo02.lami.ui.screens.home

import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ReplacementSpan
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.ninbyo02.lami.ui.model.InferenceStats
import io.github.ninbyo02.lami.ui.text.PythonCodeSyntaxInspector
import io.github.ninbyo02.lami.ui.text.PythonCodeWarning
import io.github.ninbyo02.lami.ui.text.PythonCodeWarningType
import io.github.ninbyo02.lami.ui.util.buildInferenceSummary
import io.github.ninbyo02.lami.ui.text.Segment
import io.github.ninbyo02.lami.ui.text.parseFencedCodeSegments
import dev.jeziellago.compose.markdowntext.MarkdownText
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.hypot

private const val ZOOM_EPS = 1.01f
private const val ASSISTANT_TEXT_SELECTION_MAX_CHARS = 3000

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: String,
    isSentByMe: Boolean,
    attachmentUriString: String? = null,
    attachmentUriStringsJson: String? = null,
    createdAtEpochMs: Long = 0L,
) {
    val resolvedAttachmentUriStrings = remember(attachmentUriString, attachmentUriStringsJson) {
        decodeAttachmentUriStrings(attachmentUriStringsJson).ifEmpty { listOfNotNull(attachmentUriString) }
    }
    ChatBubble(
        message = message,
        isSentByMe = isSentByMe,
        attachmentUriStrings = resolvedAttachmentUriStrings,
        createdAtEpochMs = createdAtEpochMs,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: String,
    isSentByMe: Boolean,
    attachmentUriStrings: List<String>,
    createdAtEpochMs: Long = 0L,
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val segments = remember(message) { parseFencedCodeSegments(message) }
    val attachmentUris = remember(attachmentUriStrings) { attachmentUriStrings.map(Uri::parse) }
    var selectedAttachmentIndex by remember { mutableStateOf<Int?>(null) }
    val timestampText = remember(createdAtEpochMs) { formatMessageTimestamp(createdAtEpochMs) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 上側余白は ChatScreen 側で管理するため、吹き出し側の top padding は持たせない
            .padding(start = 10.dp, top = 0.dp, end = 10.dp, bottom = 10.dp),
        horizontalArrangement = if (isSentByMe) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                // 左右それぞれ +4dp 拡張（合計 +8dp）
                .widthIn(max = 288.dp)
                .testTag("userChatBubble")
                .background(
                    color = if (isSentByMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.combinedClickable(
                    enabled = true,
                    onClick = {},
                    onLongClick = { clipboardManager.setText(AnnotatedString(message)) })
            ) {
                AttachmentGallery(
                    attachmentUris = attachmentUris,
                    onAttachmentClick = { index -> selectedAttachmentIndex = index },
                )

                selectedAttachmentIndex?.let { initialIndex ->
                    AttachmentFullscreenViewer(
                        attachmentUris = attachmentUris,
                        initialIndex = initialIndex,
                        onDismiss = { selectedAttachmentIndex = null },
                    )
                }

                if (message.isNotBlank()) {
                    MessageSegments(segments = segments)
                }
                if (timestampText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = timestampText,
                        modifier = Modifier.align(Alignment.End),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

private fun formatMessageTimestamp(epochMs: Long): String? {
    if (epochMs <= 0L) return null
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
}

@Composable
private fun AttachmentGallery(
    attachmentUris: List<Uri>,
    onAttachmentClick: (Int) -> Unit,
) {
    if (attachmentUris.isEmpty()) {
        return
    }

    if (attachmentUris.size == 1) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    adjustViewBounds = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { imageView ->
                imageView.setImageURI(attachmentUris.first())
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onAttachmentClick(0) },
        )
    } else {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(attachmentUris) { index, attachmentUri ->
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            adjustViewBounds = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    update = { imageView ->
                        imageView.setImageURI(attachmentUri)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onAttachmentClick(index) },
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AttachmentFullscreenViewer(
    attachmentUris: List<Uri>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (attachmentUris.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, attachmentUris.lastIndex),
        pageCount = { attachmentUris.size },
    )
    val coroutineScope = rememberCoroutineScope()
    val pageMoveMutex = remember { Mutex() }
    var isZoomed by remember { mutableStateOf(false) }
    val repeatInitialDelayMs = 250L
    val repeatIntervalMs = 20L
    val overlayButtonSize = 36.dp
    val overlayButtonAlpha = 0.55f
    val overlayButtonModifier = Modifier
        .size(overlayButtonSize)
        .background(Color.Black.copy(alpha = overlayButtonAlpha), CircleShape)

    suspend fun movePageBy(delta: Int): Boolean {
        return pageMoveMutex.withLock {
            val basePage = pagerState.currentPage
            val targetPage = (basePage + delta).coerceIn(0, attachmentUris.lastIndex)
            if (targetPage == basePage) return@withLock false
            try {
                pagerState.animateScrollToPage(targetPage)
            } finally {
                withContext(NonCancellable) {
                    pagerState.scrollToPage(targetPage)
                }
            }
            true
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isZoomed,
            ) { page ->
                ZoomableAttachmentPage(
                    attachmentUri = attachmentUris[page],
                    resetToken = pagerState.currentPage,
                    onZoomChanged = { zoomed ->
                        if (page == pagerState.currentPage) {
                            isZoomed = zoomed
                        }
                    },
                )
            }

            if (pagerState.currentPage > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .then(overlayButtonModifier)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    if (!movePageBy(-1)) return@detectTapGestures
                                    var keepRepeating = true
                                    val repeatJob = coroutineScope.launch {
                                        delay(repeatInitialDelayMs)
                                        while (keepRepeating && isActive) {
                                            if (!movePageBy(-1)) break
                                            if (repeatIntervalMs > 0) {
                                                delay(repeatIntervalMs)
                                            }
                                        }
                                    }
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        keepRepeating = false
                                        repeatJob.join()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "前の画像",
                        tint = Color.White,
                    )
                }
            }

            if (pagerState.currentPage < attachmentUris.lastIndex) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .then(overlayButtonModifier)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    if (!movePageBy(1)) return@detectTapGestures
                                    var keepRepeating = true
                                    val repeatJob = coroutineScope.launch {
                                        delay(repeatInitialDelayMs)
                                        while (keepRepeating && isActive) {
                                            if (!movePageBy(1)) break
                                            if (repeatIntervalMs > 0) {
                                                delay(repeatIntervalMs)
                                            }
                                        }
                                    }
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        keepRepeating = false
                                        repeatJob.join()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "次の画像",
                        tint = Color.White,
                    )
                }
            }

            Text(
                text = "${pagerState.currentPage + 1} / ${attachmentUris.size}",
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 14.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .then(overlayButtonModifier)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onDismiss()
                                tryAwaitRelease()
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "閉じる",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ZoomableAttachmentPage(
    attachmentUri: Uri,
    resetToken: Int,
    onZoomChanged: (Boolean) -> Unit,
) {
    var scale by remember(attachmentUri, resetToken) { mutableFloatStateOf(1f) }
    var offset by remember(attachmentUri, resetToken) { mutableStateOf(Offset.Zero) }

    fun resetZoomIfNeeded() {
        if (scale > ZOOM_EPS) {
            scale = 1f
            offset = Offset.Zero
            onZoomChanged(false)
        }
    }

    LaunchedEffect(attachmentUri, resetToken) {
        scale = 1f
        offset = Offset.Zero
        onZoomChanged(false)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(resetToken) {
                detectTapGestures(
                    onDoubleTap = { resetZoomIfNeeded() }
                )
            }
    ) {
        val density = LocalDensity.current
        val containerW = with(density) { maxWidth.toPx() }
        val containerH = with(density) { maxHeight.toPx() }
        // centerScreen は表示領域（BoxWithConstraints）の中心座標。
        // PointerInput の position と同一のローカル座標系（画面内）で扱う。
        val centerScreen = Offset(containerW / 2f, containerH / 2f)

        fun clampOffset(raw: Offset, currentScale: Float): Offset {
            if (currentScale <= ZOOM_EPS) return Offset.Zero
            val maxX = ((containerW * currentScale) - containerW) / 2f
            val maxY = ((containerH * currentScale) - containerH) / 2f
            return Offset(
                x = raw.x.coerceIn(-maxX, maxX),
                y = raw.y.coerceIn(-maxY, maxY),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { imageView -> imageView.setImageURI(attachmentUri) },
                modifier = Modifier
                    .pointerInput(attachmentUri, resetToken) {
                        awaitEachGesture {
                            val down = awaitFirstDown(pass = PointerEventPass.Main)
                            if (scale <= ZOOM_EPS) return@awaitEachGesture

                            var pointerId = down.id
                            var lastPosition = down.position

                            val drag = awaitTouchSlopOrCancellation(pointerId) { change, over ->
                                if (over != Offset.Zero) {
                                    offset = clampOffset(offset + over, scale)
                                }
                                lastPosition = change.position
                                change.consume()
                            }

                            if (drag == null) return@awaitEachGesture

                            pointerId = drag.id
                            lastPosition = drag.position

                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount >= 2) break

                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) break

                                val delta = change.position - lastPosition
                                if (delta != Offset.Zero) {
                                    offset = clampOffset(offset + delta, scale)
                                }
                                lastPosition = change.position
                                change.consume()
                            }
                        }
                    }
                    .pointerInput(attachmentUri, resetToken) {
                        awaitEachGesture {
                            var prevPos1: Offset
                            var prevPos2: Offset
                            var pointerId1: PointerId
                            var pointerId2: PointerId
                            var anchorStart = Offset.Zero
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                val pressedChanges = event.changes.filter { it.pressed }
                                if (pressedChanges.size < 2) {
                                    if (pressedChanges.isEmpty()) return@awaitEachGesture
                                    continue
                                }

                                val firstChange = pressedChanges[0]
                                val secondChange = pressedChanges[1]
                                pointerId1 = firstChange.id
                                pointerId2 = secondChange.id
                                prevPos1 = firstChange.position
                                prevPos2 = secondChange.position
                                // anchorStart はピンチ開始時の2本指の中心。
                                // screen座標系（centerScreenと同じ座標系）で保持し、
                                // ズーム中はこの位置を拡大中心として固定する。
                                anchorStart = (prevPos1 + prevPos2) / 2f
                                event.changes.forEach { it.consume() }
                                break
                            }

                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                val firstChange = event.changes.firstOrNull { it.id == pointerId1 }
                                if (firstChange == null) {
                                    break
                                }
                                val secondChange = event.changes.firstOrNull { it.id == pointerId2 }
                                if (secondChange == null) {
                                    break
                                }

                                if (!firstChange.pressed || !secondChange.pressed) {
                                    event.changes.forEach { it.consume() }
                                    break
                                }

                                val currPos1 = firstChange.position
                                val currPos2 = secondChange.position
                                val prevDist = hypot(
                                    (prevPos1.x - prevPos2.x).toDouble(),
                                    (prevPos1.y - prevPos2.y).toDouble(),
                                ).toFloat()
                                val currDist = hypot(
                                    (currPos1.x - currPos2.x).toDouble(),
                                    (currPos1.y - currPos2.y).toDouble(),
                                ).toFloat()
                                val zoomFactor = if (prevDist > 0f) currDist / prevDist else 1f

                                val oldScale = scale
                                val newScale = (oldScale * zoomFactor).coerceIn(1f, 5f)
                                if (newScale <= ZOOM_EPS) {
                                    scale = 1f
                                    offset = Offset.Zero
                                    onZoomChanged(false)
                                } else {
                                    val zoom = newScale / oldScale
                                    // anchorRel は表示中心(centerScreen)基準の相対位置。
                                    // offset をこの相対位置に追従させることで、
                                    // ピンチ開始地点を中心にズームする。
                                    val anchorRel = anchorStart - centerScreen
                                    val nextOffset = offset + (anchorRel - offset) * (1f - zoom)
                                    offset = clampOffset(nextOffset, newScale)
                                    scale = newScale
                                    onZoomChanged(true)
                                }

                                prevPos1 = currPos1
                                prevPos2 = currPos2
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }
    }
}

private fun decodeAttachmentUriStrings(attachmentUriStringsJson: String?): List<String> {
    if (attachmentUriStringsJson.isNullOrBlank()) return emptyList()
    return runCatching {
        val jsonArray = JSONArray(attachmentUriStringsJson)
        List(jsonArray.length()) { index -> jsonArray.optString(index) }
            .filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
}

@Composable
fun PlainAssistantMessage(
    message: String,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    isStreaming: Boolean = false,
    createdAtEpochMs: Long = 0L,
    showMessageActions: Boolean = false,
    isReplaying: Boolean = false,
    onReplayClick: (() -> Unit)? = null,
    onStopReplayClick: (() -> Unit)? = null,
    onCopyAllClick: (() -> Unit)? = null,
    inferenceStats: InferenceStats? = null,
    onInferenceStatsClick: (() -> Unit)? = null,
) {
    // 長文回答ではスクロール優先のため選択を無効化する。
    val shouldEnableAssistantTextSelection = remember(message, isStreaming) {
        shouldEnableAssistantTextSelection(
            message = message,
            isStreaming = isStreaming,
        )
    }
    val streamingSplit = remember(message, isStreaming) {
        if (isStreaming) {
            splitStreamingText(message)
        } else {
            StreamingSplit(stable = message, unstable = "")
        }
    }
    val segments = remember(streamingSplit.stable) {
        parseFencedCodeSegments(streamingSplit.stable)
    }
    val shouldRenderUnstableAsCode = remember(streamingSplit.unstable, isStreaming) {
        isStreaming && shouldTreatAsProvisionalCode(streamingSplit.unstable)
    }
    val inferenceSummary = remember(inferenceStats) { inferenceStats?.let(::buildInferenceSummary) }
    val timestampText = remember(createdAtEpochMs) { formatMessageTimestamp(createdAtEpochMs) }
    val pythonSyntaxWarnings = remember(message, isStreaming) {
        if (isStreaming) {
            emptyList()
        } else {
            PythonCodeSyntaxInspector.inspectMarkdown(message).warnings.take(3)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .testTag("assistantPlainMessage")
    ) {
        MessageSegments(
            segments = segments,
            enableTextSelection = shouldEnableAssistantTextSelection,
            isStreaming = isStreaming,
        )
        val unstableTail = streamingSplit.unstable
        if (unstableTail.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            if (shouldRenderUnstableAsCode) {
                CodeBlockCard(
                    lang = detectProvisionalLanguage(unstableTail),
                    code = unstableTail,
                    isClosed = false,
                    isStreamingCodeBlock = isStreaming,
                    showLineNumbers = shouldShowCodeLineNumbers(isStreaming),
                )
            } else {
                Text(
                    text = unstableTail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (pythonSyntaxWarnings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            PythonSyntaxWarningSummary(warnings = pythonSyntaxWarnings)
        }
        if (timestampText != null && !isStreaming) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timestampText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                fontSize = 11.sp,
            )
        }
        if (showMessageActions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isReplaying && onStopReplayClick != null) {
                    IconButton(
                        onClick = { onStopReplayClick() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "再生を停止",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else if (onReplayClick != null) {
                    IconButton(
                        onClick = { onReplayClick() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "回答を再生",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { onCopyAllClick?.invoke() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "全文をコピー",
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (inferenceSummary != null) {
                    Text(
                        text = inferenceSummary,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clickable(enabled = onInferenceStatsClick != null) {
                                onInferenceStatsClick?.invoke()
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PythonSyntaxWarningSummary(warnings: List<PythonCodeWarning>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Pythonコードに構文崩れの可能性があります",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        warnings.forEach { warning ->
            Text(
                text = "L${warning.lineNumber} ${pythonWarningTypeJa(warning.type)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 2.dp)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = warning.lineText.take(140),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = warning.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


private fun pythonWarningTypeJa(type: PythonCodeWarningType): String = when (type) {
    PythonCodeWarningType.POSSIBLE_EMPTY_BLOCK -> "空ブロックの可能性"
    PythonCodeWarningType.POSSIBLE_INDENT_JUMP -> "インデント崩れの可能性"
    PythonCodeWarningType.POSSIBLE_TOP_LEVEL_DEDENT_AFTER_BLOCK -> "ブロック直後の字下げ崩れの可能性"
    PythonCodeWarningType.POSSIBLE_FUSED_CODE -> "コード連結の可能性"
}

data class StreamingSplit(
    val stable: String,
    val unstable: String,
)

data class AssistantDisplayText(
    val text: String,
    val isTrimmedForRender: Boolean,
)

fun buildAssistantDisplayText(
    originalMessage: String,
    @Suppress("UNUSED_PARAMETER")
    tailLimitChars: Int,
): AssistantDisplayText {
    val sanitized = sanitizeAssistantMessageForDisplay(originalMessage)
    // tail trim/前半省略は使わず、常に整形済み全文を表示する。
    return AssistantDisplayText(
        text = sanitized,
        isTrimmedForRender = false,
    )
}

fun sanitizeAssistantMessageForDisplay(message: String): String {
    if (message.isBlank()) return message
    val wsTraceKeywords = setOf(
        "=== WS TRACE ===",
        "=== RUNNER WS TRACE ===",
        "=== DEV Stream ===",
        "RAW:",
        "NORMALIZED:",
        "LEN:",
        "SPACES:",
        "NL:",
        "----",
    )
    val visibleLines = mutableListOf<String>()
    for (line in message.lineSequence()) {
        val trimmed = line.trim()
        if (wsTraceKeywords.any { keyword -> trimmed.startsWith(keyword) }) {
            break
        }
        visibleLines += line
    }
    return visibleLines
        .joinToString("\n")
        .replace('␠', ' ')
        .trim()
}

fun splitStreamingText(text: String): StreamingSplit {
    if (text.isEmpty()) return StreamingSplit(stable = "", unstable = "")
    val lines = text.lines()
    val hasTrailingNewLine = text.endsWith("\n")
    val unclosedFence = text.split("```").size % 2 == 0
    val unstableStartLine = findProvisionalUnstableStartLine(
        lines = lines,
        hasTrailingNewLine = hasTrailingNewLine,
        unclosedFence = unclosedFence,
    ) ?: return StreamingSplit(stable = text, unstable = "")

    val unstablePart = lines.drop(unstableStartLine).joinToString("\n")
    val unstableFirstLine = unstablePart.lineSequence().firstOrNull().orEmpty().trim()
    val shouldSplitTail =
        shouldTreatAsProvisionalCode(unstablePart) ||
            unstablePart.lineSequence().any { line -> isPythonFusionStart(line.trim()) } ||
            unstableFirstLine in PROVISIONAL_LANGUAGE_TAGS ||
            unstableFirstLine.startsWith("```")

    if (!shouldSplitTail) {
        return StreamingSplit(stable = text, unstable = "")
    }

    val stablePart = lines.take(unstableStartLine).joinToString("\n")
    return StreamingSplit(stable = stablePart, unstable = unstablePart)
}


fun isPythonFusionStart(text: String): Boolean {
    val normalized = text.trimStart()
    if (!normalized.startsWith("python")) return false
    val tail = normalized.removePrefix("python").trimStart()
    if (tail.isBlank()) return false
    val strongFusionPrefixes = listOf(
        "import",
        "def",
        "class",
        "for",
        "while",
        "print(",
        "return",
        "from",
        "self.",
        "GRID_",
    )
    if (strongFusionPrefixes.any { prefix -> tail.startsWith(prefix) }) {
        return true
    }
    if (
        Regex("^[A-Za-z_][A-Za-z0-9_]{2,}\\s*[(:=\\[]").containsMatchIn(tail) ||
            Regex("^np\\.").containsMatchIn(tail)
    ) {
        return true
    }
    return false
}

fun shouldTreatAsProvisionalCode(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return false
    if (trimmed in setOf("python", "kotlin", "bash", "json")) return true
    val hasPythonFusion = isPythonFusionStart(trimmed)
    if (hasPythonFusion) return true
    val hasPythonLanguageTag = trimmed.equals("python", ignoreCase = true)
    val pythonContext = hasPythonLanguageTag || hasPythonFusion

    val assignmentLike = Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\s*=\\s*[^=]")
    val signals = listOf(
        Regex("\\bimport\\b"),
        Regex("\\bfrom\\s+"),
        Regex("\\bdef\\b"),
        Regex("\\bclass\\b"),
        Regex("\\bfor\\b"),
        Regex("\\bwhile\\b"),
        Regex("\\belif\\b"),
        Regex("\\bexcept\\b"),
        Regex("\\breturn\\b"),
        Regex("lambda"),
        Regex("self\\."),
        Regex("np\\."),
        Regex("GRID_[A-Za-z0-9_]*"),
        Regex("print\\("),
        assignmentLike,
        Regex("->"),
        Regex("[{}]"),
        Regex("[\\[\\]]"),
        Regex(";"),
    )
    var score = signals.count { signal -> signal.containsMatchIn(trimmed) }
    if (hasStrongCodeSignal(trimmed)) {
        score += 2
    }
    if (pythonContext && (trimmed.startsWith("from ") || trimmed.startsWith("self.") || trimmed.startsWith("print("))) {
        score += 1
    }
    if (text.startsWith("    ") || text.startsWith("\t")) score += 1
    return score >= 3
}

private fun findProvisionalUnstableStartLine(
    lines: List<String>,
    hasTrailingNewLine: Boolean,
    unclosedFence: Boolean,
): Int? {
    if (hasTrailingNewLine || lines.isEmpty()) return null
    val blockEnd = lines.indexOfLast { it.isNotBlank() }
    if (blockEnd < 0) return null
    val blockStart =
        lines.subList(0, blockEnd + 1).indexOfLast { it.isBlank() }.let { blankIndex ->
            if (blankIndex >= 0) blankIndex + 1 else 0
        }

    if (unclosedFence) {
        val fenceStart = (0..blockEnd).lastOrNull { index -> lines[index].trimStart().startsWith("```") }
        if (fenceStart != null) return fenceStart
    }

    for (index in blockStart..blockEnd) {
        val line = lines[index].trim()
        if (line in PROVISIONAL_LANGUAGE_TAGS && index < blockEnd) {
            val activeBlock = lines.drop(index).joinToString("\n")
            if (shouldTreatAsProvisionalCode(activeBlock) || hasStrongCodeSignal(lines[index + 1])) {
                return index
            }
        }
        if (isPythonFusionStart(line)) return index
    }

    val candidateIndices = (blockStart..blockEnd).filter { index -> lines[index].isNotBlank() }
    if (candidateIndices.isEmpty()) return null
    var streakStart: Int? = null
    var previousIndex: Int? = null
    for (index in candidateIndices) {
        val codeLike = shouldTreatAsProvisionalCode(lines[index]) || hasStrongCodeSignal(lines[index])
        if (codeLike && previousIndex != null && index == previousIndex + 1 && streakStart != null) {
            return streakStart
        }
        if (codeLike) {
            streakStart = index
        } else {
            streakStart = null
        }
        previousIndex = index
    }
    return null
}

private val PROVISIONAL_LANGUAGE_TAGS = setOf("python", "kotlin", "bash", "json")

private fun hasStrongCodeSignal(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isBlank()) return false
    return Regex("^\\s*(import\\s+|from\\s+|def\\s+|class\\s+|for\\s+|while\\s+|return\\b)").containsMatchIn(trimmed) ||
        Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\s*=\\s*[^=]").containsMatchIn(trimmed) ||
        trimmed.startsWith("print(") ||
        trimmed.startsWith("```")
}

private fun detectProvisionalLanguage(text: String): String? {
    val trimmed = text.trimStart()
    return when {
        trimmed.startsWith("python") -> "python"
        trimmed.startsWith("fun ") || trimmed.startsWith("val ") || trimmed.startsWith("class ") -> "kotlin"
        trimmed.startsWith("{") || trimmed.startsWith("[") -> "json"
        trimmed.startsWith("$") || trimmed.startsWith("#!/bin/bash") -> "bash"
        else -> null
    }
}

@Composable
private fun MessageSegments(
    segments: List<Segment>,
    enableTextSelection: Boolean = false,
    isStreaming: Boolean = false,
) {
    val bodyMedium = MaterialTheme.typography.bodyMedium
    val markdownTextStyle = bodyMedium.copy(
        lineHeight = (bodyMedium.lineHeight.value * 0.895f + 4f).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    val inlineCodeBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is Segment.Text -> {
                    if (segment.text.isNotEmpty()) {
                        val textComposable: @Composable () -> Unit = {
                            MarkdownText(
                                segment.text,
                                style = markdownTextStyle,
                                isTextSelectable = enableTextSelection,
                                syntaxHighlightColor = inlineCodeBg,
                                beforeSetMarkdown = { textView, spanned ->
                                    if (spanned is Spannable) {
                                        replaceInlineCodeSpans(
                                            textView = textView,
                                            text = spanned,
                                            backgroundColor = inlineCodeBg.toArgb()
                                        )
                                    }
                                }
                            )
                        }
                        textComposable()
                    }
                }

                is Segment.Code -> {
                    val isStreamingCodeBlock = isStreaming && !segment.isClosed
                    val shouldShowCodeLineNumbers = shouldShowCodeLineNumbers(isStreaming)
                    CodeBlockCard(
                        lang = segment.lang,
                        code = segment.code,
                        isClosed = !shouldShowCodeGeneratingState(
                            isStreaming = isStreaming,
                            isSegmentClosed = segment.isClosed,
                        ),
                        isStreamingCodeBlock = isStreamingCodeBlock,
                        showLineNumbers = shouldShowCodeLineNumbers,
                    )
                }
            }
        }
    }
}

internal fun shouldShowCodeGeneratingState(
    isStreaming: Boolean,
    isSegmentClosed: Boolean,
): Boolean = isStreaming && !isSegmentClosed

internal fun shouldUsePlainTextForStreamingCodeFence(
    message: String,
    isStreaming: Boolean,
): Boolean = isStreaming && message.contains("```")

internal fun shouldEnableAssistantTextSelection(
    message: String,
    isStreaming: Boolean,
): Boolean = !isStreaming &&
    message.length <= ASSISTANT_TEXT_SELECTION_MAX_CHARS &&
    !message.contains("```")

internal fun shouldShowCodeLineNumbers(
    isStreaming: Boolean,
): Boolean = !isStreaming

internal fun shouldDisableCodeBlockBodyInteractions(
    code: String,
    isStreamingCodeBlock: Boolean,
): Boolean = isStreamingCodeBlock || code.length > ASSISTANT_TEXT_SELECTION_MAX_CHARS

internal fun calculateCodeLineNumberDigits(lineCount: Int): Int =
    maxOf(2, lineCount.coerceAtLeast(1).toString().length)

internal fun buildCodeLinesForDisplay(code: String): List<String> =
    code.lines().dropTrailingFenceArtifacts()

internal fun List<String>.dropTrailingFenceArtifacts(): List<String> =
    dropLastWhile { it.isEmpty() }

private fun replaceInlineCodeSpans(
    textView: TextView,
    text: Spannable,
    backgroundColor: Int,
) {
    val density = textView.resources.displayMetrics.density
    val spanned: Spanned = text as? Spanned ?: return
    val codeSpans: Array<CodeSpan> = spanned.getSpans(0, text.length, CodeSpan::class.java)
    codeSpans.forEach { codeSpan ->
        val start = text.getSpanStart(codeSpan)
        val end = text.getSpanEnd(codeSpan)
        val flags = text.getSpanFlags(codeSpan)
        if (start in 0 until end) {
            text.removeSpan(codeSpan)
            text.setSpan(
                InlineCodeChipSpan(
                    textColor = textView.currentTextColor,
                    backgroundColor = backgroundColor,
                    density = density,
                    horizontalPaddingPx = density * 4f,
                    verticalInsetPx = density * 1.5f,
                    padYPx = density * 2f,
                    cornerRadiusPx = density * 6f
                ),
                start,
                end,
                flags
            )
        }
    }
}

private class CodeSpan : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int = paint.measureText(text, start, end).toInt()

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        canvas.drawText(text, start, end, x, y.toFloat(), paint)
    }
}

private class InlineCodeChipSpan(
    private val textColor: Int,
    private val backgroundColor: Int,
    private val density: Float,
    private val horizontalPaddingPx: Float,
    private val verticalInsetPx: Float,
    private val padYPx: Float,
    private val cornerRadiusPx: Float,
) : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        fm?.let {
            it.ascent = (it.ascent - padYPx).toInt()
            it.top = (it.top - padYPx).toInt()
            it.descent = (it.descent + padYPx).toInt()
            it.bottom = (it.bottom + padYPx).toInt()
        }
        return (paint.measureText(text, start, end) + horizontalPaddingPx * 2f).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val baselineShift = (paint as? TextPaint)?.baselineShift ?: 0
        val baselineY = y.toFloat() + baselineShift
        val fm = paint.fontMetrics
        val rectTop = baselineY + fm.ascent - verticalInsetPx - padYPx
        val rectBottom = baselineY + fm.descent + verticalInsetPx + padYPx
        val textWidth = paint.measureText(text, start, end)
        val rectRight = x + textWidth + horizontalPaddingPx * 2f
        val previousColor = paint.color

        paint.color = backgroundColor
        canvas.drawRoundRect(
            x,
            rectTop,
            rectRight,
            rectBottom,
            cornerRadiusPx,
            cornerRadiusPx,
            paint
        )

        paint.color = textColor
        canvas.drawText(text, start, end, x + horizontalPaddingPx, baselineY, paint)
        paint.color = previousColor
    }
}

@Composable
private fun CodeBlockCard(
    lang: String?,
    code: String,
    isClosed: Boolean = true,
    isStreamingCodeBlock: Boolean = false,
    showLineNumbers: Boolean = true,
) {
    val clipboardManager = LocalClipboardManager.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(12.dp)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // ヘッダーを角丸の縁から少し離して表示する
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = lang?.takeIf { it.isNotBlank() } ?: "Code",
                                style = MaterialTheme.typography.labelMedium,
                                color = lerp(
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                    MaterialTheme.colorScheme.primary,
                                    0.18f,
                                )
                            )
                            if (!isClosed) {
                                Text(
                                    text = "生成中…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clickable { clipboardManager.setText(AnnotatedString(code)) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "コードをコピー",
                                modifier = Modifier.size(18.dp),
                                tint = lerp(
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                    MaterialTheme.colorScheme.primary,
                                    0.16f,
                                )
                            )
                        }
                    }
                    if (!showLineNumbers) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = code,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                softWrap = false,
                            )
                        }
                    } else {
                        val codeLines = remember(code) { buildCodeLinesForDisplay(code) }
                        val lineNumberDigits = remember(codeLines) {
                            calculateCodeLineNumberDigits(codeLines.size)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            codeLines.forEachIndexed { index, line ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        text = (index + 1).toString().padStart(lineNumberDigits, ' '),
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.End,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                    Text(
                                        text = line,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                ChatBubble("Heyy", isSentByMe = true)
                PlainAssistantMessage("**Heyy**")
            }
        }
    }
}
