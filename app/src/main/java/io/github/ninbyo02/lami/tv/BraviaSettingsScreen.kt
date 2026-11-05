package io.github.ninbyo02.lami.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BraviaSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val manager = remember { BraviaManager.get(context) }
    val scope = rememberCoroutineScope()
    var address by rememberSaveable { mutableStateOf("") }
    var awaitingPin by rememberSaveable { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf(BraviaConnectionSummary()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var discovered by remember { mutableStateOf<List<BraviaDiscoveredDevice>>(emptyList()) }
    var inspection by remember { mutableStateOf<BraviaInspection?>(null) }

    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { message = error.message ?: "テレビとの接続を確認してください。" }
            finally { busy = false }
        }
    }

    LaunchedEffect(Unit) {
        try { summary = manager.summary(); if (address.isBlank()) address = summary.address }
        catch (error: Exception) { message = error.message.orEmpty() }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("テレビ操作") }, navigationIcon = {
            TextButton(onClick = { navController.popBackStack() }) { Text("戻る") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("SONY BRAVIA", style = MaterialTheme.typography.titleLarge)
            Text("テレビとスマートフォンを同じホームネットワークに接続してください。")
            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("テレビのIPアドレス") },
                placeholder = { Text("192.168.x.x") }, singleLine = true, enabled = !busy && !awaitingPin,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            TextButton(enabled = !busy && !awaitingPin, onClick = { runAction {
                discovered = BraviaDiscovery.find(context)
                message = if (discovered.isEmpty()) "テレビが見つかりません。電源を入れるか、IPアドレスを入力してください。" else "接続するテレビを選んでください。"
                if (discovered.size == 1) address = discovered.single().address
            } }) { Text("テレビを探す") }
            discovered.forEach { tv ->
                TextButton(enabled = !busy && !awaitingPin, onClick = { address = tv.address }) {
                    Text("${tv.model}　${tv.name}（${tv.address}）")
                }
            }
            Button(enabled = !busy && address.isNotBlank(), onClick = { runAction {
                val connected = manager.beginPairing(address)
                awaitingPin = !connected; pin = ""
                if (connected) { summary = manager.summary(); message = "${summary.model}に接続しました。" }
                else message = "テレビ画面に表示された4桁の認証番号を入力してください。"
            } }) { Text(if (awaitingPin) "認証番号を再表示" else "テレビに認証番号を表示") }
            if (awaitingPin) {
                OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                    label = { Text("テレビの認証番号（4桁）") }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !busy && pin.length == 4, onClick = { runAction {
                        val entered = pin; pin = ""
                        summary = manager.completePairing(entered); awaitingPin = false
                        message = "${summary.model}を登録しました。チャットから操作できます。"
                    } }) { Text("登録する") }
                    TextButton(enabled = !busy, onClick = { awaitingPin = false; pin = "" }) { Text("キャンセル") }
                }
            }
            if (busy) CircularProgressIndicator()
            if (message.isNotBlank()) Text(message)
            if (summary.paired) {
                Text("登録済み：${summary.model}（${summary.address}）")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("チャットからテレビを操作")
                    Switch(checked = summary.enabled, enabled = !busy, onCheckedChange = { enabled -> runAction {
                        manager.setEnabled(enabled); summary = manager.summary()
                    } })
                }
                TextButton(enabled = !busy, onClick = { runAction {
                    inspection = manager.inspect(); message = "テレビから操作一覧を取得しました。"
                } }) { Text("接続と対応操作を確認") }
                inspection?.let { info ->
                    Text("リモコン操作：${info.keys.size}種類")
                    if (info.apps.isNotEmpty()) Text("起動できるアプリ\n" + info.apps.joinToString("、").take(2000))
                }
                TextButton(enabled = !busy, onClick = { runAction {
                    manager.forget(); summary = BraviaConnectionSummary(); inspection = null
                    message = "このスマートフォンの接続情報を削除しました。"
                } }) { Text("接続情報を削除") }
            }
            Text("チャットでの依頼例", style = MaterialTheme.typography.titleMedium)
            Text("テレビをつけて\nテレビの音量を10にして\nテレビをミュートして\nテレビをHDMI1に切り替えて\nテレビを地デジの1チャンネルにして\nテレビでYouTubeを開いて\nテレビの状態を教えて")
            Text("電源オンも使う場合は、テレビの［設定］→［ネットワークとインターネット］→［リモートスタート］を有効にしてください。")
        }
    }
}
