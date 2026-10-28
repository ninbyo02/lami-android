package io.github.ninbyo02.lami.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ninbyo02.lami.BuildConfig
import io.github.ninbyo02.lami.MainActivity
import io.github.ninbyo02.lami.asr.foundation.vad.AsrListeningSettings
import io.github.ninbyo02.lami.asr.foundation.wake.WakeWordMatcher
import io.github.ninbyo02.lami.assistant.EXTRA_ASSISTANT_START_LISTENING
import io.github.ninbyo02.lami.assistant.EXTRA_ASSISTANT_AUTO_SUBMIT
import io.github.ninbyo02.lami.assistant.EXTRA_ASSISTANT_WAKE_WORD
import kotlinx.coroutines.launch

@Composable
internal fun AsrListeningSettingsSection() {
    val context = LocalContext.current
    val preferences = remember(context.applicationContext) { SettingsPreferences(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val savedSettings by preferences.asrListeningSettingsFlow.collectAsState(initial = null)
    val savedPhrase by preferences.wakeWordPhraseFlow.collectAsState(initial = null)
    val savedAutoSend by preferences.voiceInputAutoSendFlow.collectAsState(initial = null)
    val savedSilenceSeconds by preferences.voiceInputSilenceSecondsFlow.collectAsState(initial = null)
    var autoSend by remember { mutableStateOf(false) }
    var silenceSeconds by remember { mutableStateOf(2) }
    var stable by remember { mutableStateOf(false) }
    var threshold by remember { mutableStateOf("650") }
    var silence by remember { mutableStateOf("800") }
    var noSpeech by remember { mutableStateOf("10000") }
    var phrase by remember { mutableStateOf("ラミ") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var savingAutoSend by remember { mutableStateOf(false) }
    LaunchedEffect(savedAutoSend, savedSilenceSeconds) {
        savedSilenceSeconds?.let { silenceSeconds = it }
    }
    LaunchedEffect(savedSettings, savedPhrase) {
        savedSettings?.let {
            stable = it.stableDetection
            threshold = it.startThresholdRms.toString()
            silence = it.endSilenceMillis.toString()
            noSpeech = it.noSpeechTimeoutMillis.toString()
        }
        savedPhrase?.let { phrase = it }
    }

    fun save(startWaiting: Boolean) {
        val settings = runCatching {
            AsrListeningSettings(stable, threshold.toInt(), silence.toInt(), noSpeech.toInt())
        }.getOrNull()
        if (settings == null) {
            error = "RMSは100～4000、終了無音は420～800 ms、発話待ちは3000～10000 ms。時間は20 ms刻みです。"
            return
        }
        if (runCatching { WakeWordMatcher(phrase) }.isFailure) {
            error = "呼びかけは2～24文字の文字・数字で指定してください。"
            return
        }
        val selectedPhrase = phrase.trim()
        saving = true
        error = null
        scope.launch {
            try {
                preferences.setAsrListeningSettings(settings)
                preferences.setVoiceInputSubmissionSettings(savedAutoSend == true, silenceSeconds)
                preferences.setWakeWordPhrase(selectedPhrase)
                if (startWaiting) {
                    context.startActivity(Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(EXTRA_ASSISTANT_START_LISTENING, true)
                        putExtra(EXTRA_ASSISTANT_AUTO_SUBMIT, true)
                        putExtra(EXTRA_ASSISTANT_WAKE_WORD, selectedPhrase)
                    })
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "音声入力設定を保存できませんでした。"
            } finally {
                saving = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("音声入力", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("発話終了後に自動送信", modifier = Modifier.weight(1f))
            Switch(checked = if (savingAutoSend) autoSend else savedAutoSend == true, onCheckedChange = { selected ->
                autoSend = selected
                savingAutoSend = true
                error = null
                scope.writePreferenceDurably(
                    write = { preferences.setVoiceInputAutoSend(selected) },
                    onFailure = {
                        autoSend = savedAutoSend ?: false
                        error = "自動送信設定を保存できませんでした。"
                    },
                    onComplete = { savingAutoSend = false },
                )
            }, enabled = !saving && !savingAutoSend && savedAutoSend != null)
        }
        Text("発話終了の無音時間", style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (1..5).forEach { seconds ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RadioButton(selected = silenceSeconds == seconds, onClick = { silenceSeconds = seconds }, enabled = !saving)
                    Text("${seconds}秒", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("VAD安定判定（試験）", modifier = Modifier.weight(1f))
            Switch(checked = stable, onCheckedChange = { stable = it }, enabled = !saving)
        }
        NumericListeningField("発話開始しきい値 (RMS)", threshold, !saving) { threshold = it }
        NumericListeningField("最初の発話を待つ時間 (ms)", noSpeech, !saving) { noSpeech = it }
        OutlinedTextField(
            value = phrase,
            onValueChange = { if (it.length <= 64) phrase = it },
            label = { Text("呼びかけ語") },
            singleLine = true,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = { save(false) }, enabled = !saving && !savingAutoSend && savedSettings != null && savedPhrase != null && savedAutoSend != null && savedSilenceSeconds != null) { Text("保存") }
        if (BuildConfig.DEBUG) {
            Button(onClick = { save(true) }, enabled = !saving && !savingAutoSend && savedSettings != null && savedPhrase != null && savedAutoSend != null && savedSilenceSeconds != null) {
                Text("呼びかけ待機を開始（60秒・試験）")
            }
        }
    }
}

@Composable
private fun NumericListeningField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}
