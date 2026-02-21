package com.sonusid.ollama.ui.screens.home

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ReplacementSpan
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonusid.ollama.ui.common.buildHighlightedCodeAnnotatedString
import com.sonusid.ollama.ui.text.Segment
import com.sonusid.ollama.ui.text.parseFencedCodeSegments
import dev.jeziellago.compose.markdowntext.MarkdownText
import io.noties.markwon.core.spans.CodeSpan


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: String,
    isSentByMe: Boolean,
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
                MessageSegments(segments = segments)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlainAssistantMessage(
    message: String,
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val segments = remember(message) { parseFencedCodeSegments(message) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
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
        lineHeight = (bodyMedium.lineHeight.value * 0.885f + 4f).sp,
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
    val codeSpans = text.getSpans(0, text.length, CodeSpan::class.java)
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
                    horizontalPaddingPx = density * 4f,
                    verticalInsetPx = density * 1.5f,
                    cornerRadiusPx = density * 6f
                ),
                start,
                end,
                flags
            )
        }
    }
}

private class InlineCodeChipSpan(
    private val textColor: Int,
    private val backgroundColor: Int,
    private val horizontalPaddingPx: Float,
    private val verticalInsetPx: Float,
    private val cornerRadiusPx: Float,
) : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
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
        val rectTop = top + verticalInsetPx
        val rectBottom = bottom - verticalInsetPx
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
        val baselineShift = (paint as? TextPaint)?.baselineShift ?: 0
        canvas.drawText(text, start, end, x + horizontalPaddingPx, y.toFloat() + baselineShift, paint)
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
