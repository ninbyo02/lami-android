package io.github.ninbyo02.lami.ui.screens.settings

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun UsbHeadsetSettingsRow(onClick: () -> Unit) {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    fun connectedNames(): List<String> = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
        .map { it.productName.toString().trim().ifBlank { "USBヘッドセット" } }
        .distinct()
    var names by remember(audio) { mutableStateOf(connectedNames()) }
    DisposableEffect(audio) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                names = connectedNames()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                names = connectedNames()
            }
        }
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { audio.unregisterAudioDeviceCallback(callback) }
    }
    SettingsNavRowItem(
        headline = "USBヘッドセット",
        supporting = if (names.isEmpty()) "未接続" else names.joinToString("、") + " · USB接続済み",
        leadingIcon = Icons.Filled.Headset,
        onClick = onClick,
    )
}
