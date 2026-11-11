package io.github.ninbyo02.lami.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.ninbyo02.lami.tts.LamiTtsPreviewController
import io.github.ninbyo02.lami.tts.TtsEngineChoice
import io.github.ninbyo02.lami.tts.TtsPreviewState
import kotlinx.coroutines.launch

internal const val DEFAULT_TTS_PREVIEW_TEXT = "こんにちは。ラミィがお手伝いします。"

@Composable
internal fun rememberTtsPreviewController(): LamiTtsPreviewController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context, scope) { LamiTtsPreviewController(context.applicationContext, scope) }
    DisposableEffect(controller, lifecycleOwner) {
        controller.refreshAvailability()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) controller.refreshAvailability()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.shutdown() }
    }
    return controller
}

@Composable
internal fun TtsEngineSettingsSection(
    settingsPreferences: SettingsPreferences,
    controller: LamiTtsPreviewController,
) {
    val scope = rememberCoroutineScope()
    val selectedEngine by settingsPreferences.ttsEngineFlow.collectAsState(initial = TtsEngineChoice.ANDROID)
    val state by controller.state.collectAsState()
    var previewText by rememberSaveable { mutableStateOf(DEFAULT_TTS_PREVIEW_TEXT) }

    TtsEngineSettingsContent(
        selectedEngine = selectedEngine,
        onEngineSelected = { engine -> scope.launch { settingsPreferences.setTtsEngine(engine) } },
        previewText = previewText,
        onPreviewTextChanged = { previewText = it },
        state = state,
        onPlay = { controller.play(selectedEngine, previewText) },
        onStop = controller::stop,
    )
}

@Composable
internal fun TtsEngineSettingsContent(
    selectedEngine: TtsEngineChoice,
    onEngineSelected: (TtsEngineChoice) -> Unit,
    previewText: String,
    onPreviewTextChanged: (String) -> Unit,
    state: TtsPreviewState,
    onPlay: () -> Unit,
    onStop: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("tts-engine-settings")) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("読み上げ方式", style = MaterialTheme.typography.titleMedium)
            Text(
                "初期設定はAndroidの音声読み上げです。選んだ方式をチャットで使います。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.selectableGroup()) {
                for (engine in TtsEngineChoice.entries) {
                    val availability = state.availability[engine]
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .testTag("tts-engine-${engine.storedValue}")
                            .selectable(
                                selected = engine == selectedEngine,
                                role = Role.RadioButton,
                                onClick = { onEngineSelected(engine) },
                            ).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = engine == selectedEngine, onClick = null)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(engine.label, style = MaterialTheme.typography.bodyLarge)
                            Text(engine.description, style = MaterialTheme.typography.bodySmall)
                            Text(
                                availability?.message ?: "利用できるか確認中…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("同じ文章で聴き比べ", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = previewText,
                onValueChange = onPreviewTextChanged,
                label = { Text("試聴する文章") },
                modifier = Modifier.fillMaxWidth().testTag("tts-preview-text"),
                minLines = 2,
                maxLines = 5,
                enabled = !state.busy && !state.stopping,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPlay,
                    enabled = !state.busy && !state.stopping && previewText.isNotBlank() &&
                        state.availability[selectedEngine]?.available == true,
                    modifier = Modifier.testTag("tts-preview-play"),
                ) { Text("選んだ方式で試聴") }
                OutlinedButton(
                    onClick = onStop,
                    enabled = state.busy && !state.stopping,
                    modifier = Modifier.testTag("tts-preview-stop"),
                ) { Text(if (state.stopping) "停止処理中…" else "停止") }
            }
            Text(
                when {
                    state.stopping -> "停止処理が終わるまでお待ちください。"
                    state.busy -> "${state.activeEngine?.label ?: "選んだ方式"}で試聴中…"
                    state.availability[selectedEngine]?.available == false ->
                        state.availability.getValue(selectedEngine).message
                    else -> "文章を入力し、方式を切り替えて同じ文章を試聴できます。"
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("tts-preview-status"),
            )
            state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (state.results.isNotEmpty()) {
                HorizontalDivider()
                Text("比較結果", style = MaterialTheme.typography.titleMedium)
                Text(
                    "再生開始は端末からの通知を基準に計測しています。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (result in state.results.asReversed()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(result.engine.label, style = MaterialTheme.typography.titleSmall)
                        Text(result.text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (result.error != null) {
                            Text(result.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(
                                "再生開始通知まで：${result.playbackStartMs?.let { "$it ms" } ?: "計測なし"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "再生終了まで（生成・再生を含む）：${result.totalMs?.let { "$it ms" } ?: "計測なし"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (result.audioChunks > 0) {
                                Text("音声チャンク：${result.audioChunks}個", style = MaterialTheme.typography.bodySmall)
                                result.firstPcmMs?.let { first ->
                                    Text("最初の音声チャンクまで：$first ms", style = MaterialTheme.typography.bodySmall)
                                }
                                if (result.streamedBeforeGenerationCompleted) {
                                    Text("生成完了前の再生開始を確認", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
