package io.github.ninbyo02.lami.ui.screens.settings

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Uses the system picker; reading this row never changes the assistant or starts listening. */
@Composable
internal fun AssistantSettingsRow() {
    val context = LocalContext.current
    val settings = remember(context) { AssistantSettings(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var selected by remember(settings) { mutableStateOf(settings.isSelected()) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(lifecycleOwner, settings) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) selected = settings.isSelected()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SettingsNavRowItem(
        headline = "アシスタントに設定",
        supporting = error ?: if (selected) "設定済み：LAMI · タップして変更" else "未設定 · LAMIを既定のアシスタントにします",
        leadingIcon = Icons.Filled.Assistant,
        testTag = "assistant-settings",
        onClick = {
            error = null
            selected = settings.isSelected()
            if (!settings.openSystemSettings()) {
                error = "設定画面を開けません。Androidの設定から既定のアシスタントを選択してください。"
            }
        },
    )
}

private class AssistantSettings(private val context: Context) {
    private val roles = runCatching { context.getSystemService(RoleManager::class.java) }.getOrNull()

    fun isSelected(): Boolean {
        if (runCatching { roles?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true }.getOrDefault(false)) return true
        return runCatching {
            Settings.Secure.getString(context.contentResolver, "assistant")
                ?.let(ComponentName::unflattenFromString)?.packageName == context.packageName
        }.getOrDefault(false)
    }

    fun openSystemSettings(): Boolean {
        for (action in listOf(Settings.ACTION_VOICE_INPUT_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) {
            try {
                context.startActivity(Intent(action))
                return true
            } catch (_: ActivityNotFoundException) {
                // Some device vendors expose only the general default-app picker.
            } catch (_: SecurityException) {
                // The next public settings action may still be available.
            }
        }
        return false
    }
}
