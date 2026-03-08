package com.sonusid.ollama.ui.screens.home

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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sonusid.ollama.ui.common.buildHighlightedCodeAnnotatedString
import com.sonusid.ollama.ui.text.Segment
import com.sonusid.ollama.ui.text.parseFencedCodeSegments
import dev.jeziellago.compose.markdowntext.MarkdownText
import org.json.JSONArray
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.hypot

private const val ZOOM_EPS = 1.01f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: String,
    isSentByMe: Boolean,
    attachmentUriString: String? = null,
    attachmentUriStringsJson: String? = null,
) {
    val resolvedAttachmentUriStrings = remember(attachmentUriString, attachmentUriStringsJson) {
        decodeAttachmentUriStrings(attachmentUriStringsJson).ifEmpty { listOfNotNull(attachmentUriString) }
    }
    ChatBubble(
        message = message,
        isSentByMe = isSentByMe,
        attachmentUriStrings = resolvedAttachmentUriStrings,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: String,
    isSentByMe: Boolean,
    attachmentUriStrings: List<String>,
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val segments = remember(message) { parseFencedCodeSegments(message) }
    val attachmentUris = remember(attachmentUriStrings) { attachmentUriStrings.map(Uri::parse) }
    var selectedAttachmentIndex by remember { mutableStateOf<Int?>(null) }
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
            }
        }
    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlainAssistantMessage(
    message: String,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val segments = remember(message) { parseFencedCodeSegments(message) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .testTag("assistantPlainMessage")
            .combinedClickable(
                enabled = true,
                onClick = {},
                onLongClick = { clipboardManager.setText(AnnotatedString(message)) }
            )
    ) {
        MessageSegments(segments = segments)
    }
}

@Composable
private fun MessageSegments(segments: List<Segment>) {
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
                        MarkdownText(
                            segment.text,
                            style = markdownTextStyle,
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
                }

                is Segment.Code -> {
                    CodeBlockCard(lang = segment.lang, code = segment.code)
                }
            }
        }
    }
}

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
) {
    val clipboardManager = LocalClipboardManager.current
    val colorScheme = MaterialTheme.colorScheme
    val bodyMedium = MaterialTheme.typography.bodyMedium
    val codeTextStyle = bodyMedium.copy(
        lineHeight = bodyMedium.lineHeight * 0.94f,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    val highlightedCode = remember(code, lang, colorScheme) {
        buildHighlightedCodeAnnotatedString(
            code = code,
            language = lang,
            colors = colorScheme,
        )
    }
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
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.42f))
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
                        Text(
                            text = lang?.takeIf { it.isNotBlank() } ?: "Code",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
                        )
                        Box(
                            modifier = Modifier
                                .clickable { clipboardManager.setText(AnnotatedString(code)) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "コードをコピー",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.94f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    SelectionContainer {
                        Text(
                            text = highlightedCode,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace,
                            style = codeTextStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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
