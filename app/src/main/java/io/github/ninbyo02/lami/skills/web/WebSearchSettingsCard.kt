package io.github.ninbyo02.lami.skills.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ninbyo02.lami.ui.screens.settings.SettingsToggleRowItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun WebSearchSettingsCard() {
    val context = LocalContext.current
    val manager = remember(context.applicationContext) { WebSearchManager.get(context.applicationContext) }
    val enabled by manager.enabled.collectAsState()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            SettingsToggleRowItem(headline = "Web検索（DuckDuckGo）", leadingIcon = null,
                supporting = "明示した検索語だけをDuckDuckGoへ送信します。初期設定はOFFです。",
                checked = enabled, enabled = !saving, onCheckedChange = { value ->
                    saving = true
                    scope.launch {
                        try { manager.setEnabled(value); failed = false
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (_: Exception) { failed = true
                        } finally { saving = false }
                    }
                })
            Text("Web検索の結果・検索後の回答は端末内で扱い、サーバー履歴には含めません。\n使い方: /web 調べたい言葉",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (failed) Text("設定を保存できませんでした。もう一度変更してください。", color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}
