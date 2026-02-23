package com.sonusid.ollama.ui.screens.home

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ReplacementSpan
import android.net.Uri
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonusid.ollama.ui.common.buildHighlightedCodeAnnotatedString
import com.sonusid.ollama.ui.text.Segment
import com.sonusid.ollama.ui.text.parseFencedCodeSegments
import dev.jeziellago.compose.markdowntext.MarkdownText


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: String,
    isSentByMe: Boolean,
    attachmentUriString: String? = null,
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val segments = remember(message) { parseFencedCodeSegments(message) }
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
                if (!attachmentUriString.isNullOrBlank()) {
                    val attachmentUri = remember(attachmentUriString) { Uri.parse(attachmentUriString) }
                    AndroidView(
                        factory = { context ->
                            ImageView(context).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                            }
                        },
                        update = { imageView ->
                            imageView.setImageURI(attachmentUri)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    if (message.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                if (message.isNotBlank()) {
                    MessageSegments(segments = segments)
                }
            }
        }
    }
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
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp)
        )
    ) {
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lang?.takeIf { it.isNotBlank() } ?: "Code",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(code)) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 18.dp, y = (-18).dp)
                    .minimumInteractiveComponentSize()
                    .padding(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "コードをコピー",
                    modifier = Modifier.size(18.dp)
                )
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
