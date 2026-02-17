package com.sonusid.ollama.ui.screens.home

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sonusid.ollama.ui.text.Segment
import com.sonusid.ollama.ui.text.parseFencedCodeSegments
import dev.jeziellago.compose.markdowntext.MarkdownText


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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is Segment.Text -> {
                    if (segment.text.isNotEmpty()) {
                        MarkdownText(
                            segment.text,
                            syntaxHighlightColor = MaterialTheme.colorScheme.primaryContainer
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

@Composable
private fun CodeBlockCard(
    lang: String?,
    code: String,
) {
    val clipboardManager = LocalClipboardManager.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .padding(end = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = lang?.takeIf { it.isNotBlank() } ?: "Code",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectionContainer {
                    Text(
                        text = code,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(code)) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "コードをコピー",
                    modifier = Modifier.size(12.dp)
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
