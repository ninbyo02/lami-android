package io.github.ninbyo02.lami.tools.bravia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BraviaTvSettingsCard() {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) { BraviaTvRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var configuration by remember { mutableStateOf(repository.configuration()) }
    var address by remember { mutableStateOf(configuration.address) }
    var pin by remember { mutableStateOf("") }
    var awaitingPin by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var discovered by remember { mutableStateOf<List<DiscoveredBravia>>(emptyList()) }

    fun runTask(task: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                message = task()
            } catch (error: CancellationException) {
                throw error
            } catch (error: BraviaException) {
                message = error.message ?: "テレビと接続できませんでした。"
            } catch (_: Exception) {
                message = "テレビと接続できませんでした。同じ家庭内ネットワークに接続しているか確認してください。"
            } finally {
                configuration = repository.configuration()
                busy = false
            }
        }
    }

    Text("テレビ操作", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SONY BRAVIA", style = MaterialTheme.typography.titleSmall)
            Text("同じ家庭内ネットワークのテレビを登録すると、チャットから操作できます。",
                style = MaterialTheme.typography.bodySmall)
            if (configuration.paired) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(configuration.model.ifBlank { "登録済みテレビ" }, Modifier.weight(1f))
                    Switch(configuration.enabled, onCheckedChange = {
                        repository.setEnabled(it)
                        configuration = repository.configuration()
                    }, enabled = !busy)
                }
                Text("チャットからの操作：" + if (configuration.enabled) "有効" else "無効",
                    style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("テレビのIPアドレス") },
                placeholder = { Text("192.168.1.20") },
                singleLine = true,
                enabled = !busy && !awaitingPin,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy && !awaitingPin,
                    onClick = { runTask {
                        discovered = withContext(Dispatchers.IO) { BraviaDiscovery.discover() }
                        if (discovered.isEmpty()) "テレビが見つかりませんでした。電源や接続先を確認するか、IPアドレスを入力してください。"
                        else "登録するテレビを選んでください。"
                    } },
                ) { Text("テレビを探す") }
                Button(
                    enabled = !busy && !awaitingPin && address.isNotBlank(),
                    onClick = { runTask {
                        awaitingPin = repository.beginPairing(address)
                        pin = ""
                        if (awaitingPin) "テレビに表示された4桁の番号を入力してください。"
                        else "テレビを登録しました。"
                    } },
                ) { Text(if (busy) "接続中…" else "テレビを登録") }
            }
            discovered.forEach { tv ->
                TextButton(enabled = !busy && !awaitingPin, onClick = {
                    address = tv.address
                    discovered = emptyList()
                    message = tv.model + "を選択しました。「テレビを登録」を押してください。"
                }) { Text(tv.model + "（" + tv.address + "）") }
            }
            if (awaitingPin) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                    label = { Text("テレビに表示された番号") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(enabled = !busy && Regex("[0-9]{4}").matches(pin), onClick = {
                    val enteredPin = pin
                    pin = ""
                    runTask {
                        configuration = repository.completePairing(address, enteredPin)
                        awaitingPin = false
                        "テレビを登録しました。"
                    }
                }) { Text("番号で接続") }
            }
            if (configuration.paired || awaitingPin) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (configuration.paired) {
                        TextButton(enabled = !busy, onClick = { runTask { repository.status() } }) { Text("接続確認") }
                    }
                    TextButton(enabled = !busy, onClick = {
                        repository.forget()
                        configuration = repository.configuration()
                        awaitingPin = false
                        pin = ""
                        message = "ラミィに保存したテレビの登録情報を削除しました。"
                    }) { Text("登録を解除") }
                }
            }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
            Text("依頼例：「テレビをつけて」「テレビの音量を10にして」「テレビをHDMI 1にして」",
                style = MaterialTheme.typography.bodySmall)
            Text("電源オンも使う場合は、テレビの「リモートスタート」を有効にしてください。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
