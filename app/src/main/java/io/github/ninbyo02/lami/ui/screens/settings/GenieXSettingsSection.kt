package io.github.ninbyo02.lami.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

internal enum class GenieXComputeChoice(val storedValue: String, val label: String) {
    OFF("off", "オフ"),
    CPU("cpu", "CPU"),
    GPU("gpu", "GPU"),
    NPU("npu", "NPU"),
    HYBRID("hybrid", "Hybrid"),
    ;

    companion object {
        fun fromStoredValue(value: String?): GenieXComputeChoice =
            entries.firstOrNull { it.storedValue == value } ?: OFF
    }
}

private const val GENIEX_COMPANION_PACKAGE = "io.github.ninbyo02.lamigeniexprobe"
private val modelImporter = ComponentName(GENIEX_COMPANION_PACKAGE, "$GENIEX_COMPANION_PACKAGE.ProbeActivity")

private enum class CompanionAvailability(val description: String) {
    INSTALLED("コンパニオンを起動してGGUFモデルの取り込み・確認ができます。"),
    MISSING("LAMI GenieXコンパニオンをインストールしてください。"),
    SIGNATURE_MISMATCH("このLAMIと同じ署名のGenieXコンパニオンをインストールしてください。"),
}

private fun companionAvailability(context: Context): CompanionAvailability {
    val packageManager = context.packageManager
    try {
        packageManager.getActivityInfo(modelImporter, PackageManager.ComponentInfoFlags.of(0))
    } catch (_: PackageManager.NameNotFoundException) {
        return CompanionAvailability.MISSING
    }
    return if (packageManager.checkSignatures(context.packageName, GENIEX_COMPANION_PACKAGE) ==
        PackageManager.SIGNATURE_MATCH
    ) {
        CompanionAvailability.INSTALLED
    } else {
        CompanionAvailability.SIGNATURE_MISMATCH
    }
}

@Composable
internal fun GenieXSettingsSection(settingsPreferences: SettingsPreferences) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val selected by settingsPreferences.genieXCompute.collectAsState(initial = GenieXComputeChoice.OFF.storedValue)
    var availability by remember(context) { mutableStateOf(companionAvailability(context)) }
    var launchFailed by remember(context) { mutableStateOf(false) }
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) availability = companionAvailability(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(modifier = Modifier.fillMaxWidth().testTag("geniex-settings")) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("GenieX ローカル推論", style = MaterialTheme.typography.titleMedium)
            Text(
                "ローカルのテキスト会話にLFM3-1.7Bを使います。先にコンパニオンでモデルを取り込んでください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.selectableGroup()) {
                for (choice in GenieXComputeChoice.entries) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .testTag("geniex-compute-${choice.storedValue}")
                            .selectable(
                                selected = selected == choice.storedValue,
                                role = Role.RadioButton,
                                onClick = { scope.launch { settingsPreferences.saveGenieXCompute(choice.storedValue) } },
                            )
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = selected == choice.storedValue, onClick = null)
                        Text(choice.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(
                if (launchFailed) "モデル取り込み画面を開けませんでした。" else availability.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                enabled = availability == CompanionAvailability.INSTALLED,
                onClick = {
                    launchFailed = try {
                        context.startActivity(Intent().setComponent(modelImporter))
                        false
                    } catch (_: ActivityNotFoundException) {
                        true
                    } catch (_: SecurityException) {
                        true
                    }
                },
                modifier = Modifier.testTag("geniex-model-import"),
            ) {
                Text("GenieXモデルの取り込み")
            }
        }
    }
}
